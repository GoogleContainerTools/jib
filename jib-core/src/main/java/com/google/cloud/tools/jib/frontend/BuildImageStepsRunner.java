/*
 * Copyright 2018 Google LLC. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.cloud.tools.jib.frontend;

import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.HttpStatusCodes;
import com.google.cloud.tools.jib.builder.BuildConfiguration;
import com.google.cloud.tools.jib.builder.BuildImageSteps;
import com.google.cloud.tools.jib.builder.BuildLogger;
import com.google.cloud.tools.jib.builder.SourceFilesConfiguration;
import com.google.cloud.tools.jib.cache.CacheDirectoryNotOwnedException;
import com.google.cloud.tools.jib.cache.CacheMetadataCorruptedException;
import com.google.cloud.tools.jib.cache.Caches;
import com.google.cloud.tools.jib.image.ImageReference;
import com.google.cloud.tools.jib.registry.RegistryAuthenticationFailedException;
import com.google.cloud.tools.jib.registry.RegistryUnauthorizedException;
import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;
import org.apache.http.conn.HttpHostConnectException;

/** Runs {@link BuildImageSteps} and builds helpful error messages. */
public class BuildImageStepsRunner {
  /**
   * Sets up a new {@link BuildImageStepsRunner}. Creates the directory for the cache, if needed.
   *
   * @param useOnlyProjectCache if {@code true}, sets the base layers cache directory to be the same
   *     as the application layers cache directory
   * @throws CacheDirectoryCreationException if the {@code cacheDirectory} could not be created
   */
  public static BuildImageStepsRunner newRunner(
      BuildConfiguration buildConfiguration,
      SourceFilesConfiguration sourceFilesConfiguration,
      Path cacheDirectory,
      boolean useOnlyProjectCache)
      throws CacheDirectoryCreationException {
    if (!Files.exists(cacheDirectory)) {
      try {
        Files.createDirectory(cacheDirectory);

      } catch (IOException ex) {
        throw new CacheDirectoryCreationException(cacheDirectory, ex);
      }
    }
    Caches.Initializer cachesInitializer = Caches.newInitializer(cacheDirectory);
    if (useOnlyProjectCache) {
      cachesInitializer.setBaseCacheDirectory(cacheDirectory);
    }

    return new BuildImageStepsRunner(
        buildConfiguration, sourceFilesConfiguration, cachesInitializer);
  }

  private final Supplier<BuildImageSteps> buildImageStepsSupplier;

  @VisibleForTesting
  BuildImageStepsRunner(Supplier<BuildImageSteps> buildImageStepsSupplier) {
    this.buildImageStepsSupplier = buildImageStepsSupplier;
  }

  private BuildImageStepsRunner(
      BuildConfiguration buildConfiguration,
      SourceFilesConfiguration sourceFilesConfiguration,
      Caches.Initializer cachesInitializer) {
    buildImageStepsSupplier =
        () -> new BuildImageSteps(buildConfiguration, sourceFilesConfiguration, cachesInitializer);
  }

  /**
   * Runs the {@link BuildImageSteps}.
   *
   * @param helpfulSuggestions suggestions to use in help messages for exceptions
   */
  public void buildImage(HelpfulSuggestions helpfulSuggestions)
      throws BuildImageStepsExecutionException {
    BuildImageSteps buildImageSteps = buildImageStepsSupplier.get();

    try {
      // TODO: This logging should be injected via another logging class.
      BuildLogger buildLogger = buildImageSteps.getBuildConfiguration().getBuildLogger();
      ImageReference targetImageReference =
          buildImageSteps.getBuildConfiguration().getTargetImageReference();

      buildLogger.lifecycle("");
      buildLogger.lifecycle("Containerizing application to " + targetImageReference + "...");

      // Logs the different source files used.
      buildLogger.info("Containerizing application with the following files:");

      buildLogger.info("\tClasses:");
      buildImageSteps
          .getSourceFilesConfiguration()
          .getClassesFiles()
          .forEach(classesFile -> buildLogger.info("\t\t" + classesFile));

      buildLogger.info("\tResources:");
      buildImageSteps
          .getSourceFilesConfiguration()
          .getResourcesFiles()
          .forEach(resourceFile -> buildLogger.info("\t\t" + resourceFile));

      buildLogger.info("\tDependencies:");
      buildImageSteps
          .getSourceFilesConfiguration()
          .getDependenciesFiles()
          .forEach(dependencyFile -> buildLogger.info("\t\t" + dependencyFile));

      buildImageSteps.run();

      buildLogger.lifecycle("");
      // targetImageReference in cyan.
      buildLogger.lifecycle(
          "Built and pushed image as \u001B[36m" + targetImageReference + "\u001B[0m");

    } catch (CacheMetadataCorruptedException cacheMetadataCorruptedException) {
      // TODO: Have this be different for Maven and Gradle.
      throw new BuildImageStepsExecutionException(
          helpfulSuggestions.forCacheMetadataCorrupted(), cacheMetadataCorruptedException);

    } catch (ExecutionException executionException) {
      BuildConfiguration buildConfiguration = buildImageSteps.getBuildConfiguration();

      if (executionException.getCause() instanceof HttpHostConnectException) {
        // Failed to connect to registry.
        throw new BuildImageStepsExecutionException(
            helpfulSuggestions.forHttpHostConnect(), executionException.getCause());

      } else if (executionException.getCause() instanceof RegistryUnauthorizedException) {
        handleRegistryUnauthorizedException(
            (RegistryUnauthorizedException) executionException.getCause(),
            buildConfiguration,
            helpfulSuggestions);

      } else if (executionException.getCause() instanceof RegistryAuthenticationFailedException
          && executionException.getCause().getCause() instanceof HttpResponseException) {
        handleRegistryUnauthorizedException(
            new RegistryUnauthorizedException(
                buildConfiguration.getTargetImageRegistry(),
                buildConfiguration.getTargetImageRepository(),
                (HttpResponseException) executionException.getCause().getCause()),
            buildConfiguration,
            helpfulSuggestions);

      } else if (executionException.getCause() instanceof UnknownHostException) {
        throw new BuildImageStepsExecutionException(
            helpfulSuggestions.forUnknownHost(), executionException.getCause());

      } else {
        throw new BuildImageStepsExecutionException(
            helpfulSuggestions.none(), executionException.getCause());
      }

    } catch (InterruptedException | IOException ex) {
      // TODO: Add more suggestions for various build failures.
      throw new BuildImageStepsExecutionException(helpfulSuggestions.none(), ex);

    } catch (CacheDirectoryNotOwnedException ex) {
      throw new BuildImageStepsExecutionException(
          helpfulSuggestions.forCacheDirectoryNotOwned(ex.getCacheDirectory()), ex);
    }
  }

  private void handleRegistryUnauthorizedException(
      RegistryUnauthorizedException registryUnauthorizedException,
      BuildConfiguration buildConfiguration,
      HelpfulSuggestions helpfulSuggestions)
      throws BuildImageStepsExecutionException {
    if (registryUnauthorizedException.getHttpResponseException().getStatusCode()
        == HttpStatusCodes.STATUS_CODE_FORBIDDEN) {
      // No permissions for registry/repository.
      throw new BuildImageStepsExecutionException(
          helpfulSuggestions.forHttpStatusCodeForbidden(
              registryUnauthorizedException.getImageReference()),
          registryUnauthorizedException);

    } else {
      boolean isRegistryForBase =
          registryUnauthorizedException
              .getRegistry()
              .equals(buildConfiguration.getBaseImageRegistry());
      boolean isRegistryForTarget =
          registryUnauthorizedException
              .getRegistry()
              .equals(buildConfiguration.getTargetImageRegistry());
      boolean areBaseImageCredentialsConfigured =
          buildConfiguration.getBaseImageCredentialHelperName() != null
              || buildConfiguration.getKnownBaseRegistryCredentials() != null;
      boolean areTargetImageCredentialsConfigured =
          buildConfiguration.getTargetImageCredentialHelperName() != null
              || buildConfiguration.getKnownTargetRegistryCredentials() != null;

      if (isRegistryForBase && !areBaseImageCredentialsConfigured) {
        throw new BuildImageStepsExecutionException(
            helpfulSuggestions.forNoCredentialHelpersDefinedForBaseImage(
                registryUnauthorizedException.getRegistry()),
            registryUnauthorizedException);
      }
      if (isRegistryForTarget && !areTargetImageCredentialsConfigured) {
        throw new BuildImageStepsExecutionException(
            helpfulSuggestions.forNoCredentialHelpersDefinedForTargetImage(
                registryUnauthorizedException.getRegistry()),
            registryUnauthorizedException);
      }

      // Credential helper probably was not configured correctly or did not have the necessary
      // credentials.
      throw new BuildImageStepsExecutionException(
          helpfulSuggestions.forCredentialsNotCorrect(registryUnauthorizedException.getRegistry()),
          registryUnauthorizedException);
    }
  }
}
