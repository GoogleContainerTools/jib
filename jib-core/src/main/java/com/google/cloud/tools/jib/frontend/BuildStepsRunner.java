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
import com.google.cloud.tools.jib.builder.BuildDockerSteps;
import com.google.cloud.tools.jib.builder.BuildImageSteps;
import com.google.cloud.tools.jib.builder.BuildLogger;
import com.google.cloud.tools.jib.builder.BuildSteps;
import com.google.cloud.tools.jib.builder.SourceFilesConfiguration;
import com.google.cloud.tools.jib.cache.CacheDirectoryNotOwnedException;
import com.google.cloud.tools.jib.cache.CacheMetadataCorruptedException;
import com.google.cloud.tools.jib.cache.Caches;
import com.google.cloud.tools.jib.cache.Caches.Initializer;
import com.google.cloud.tools.jib.registry.RegistryAuthenticationFailedException;
import com.google.cloud.tools.jib.registry.RegistryUnauthorizedException;
import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import org.apache.http.conn.HttpHostConnectException;

/** Runs a {@link BuildSteps} and builds helpful error messages. */
public class BuildStepsRunner {

  /**
   * Creates a runner to build an image. Creates a directory for the cache, if needed.
   *
   * @param useOnlyProjectCache if {@code true}, sets the base layers cache directory to be the same
   *     as the application layers cache directory
   * @throws CacheDirectoryCreationException if the {@code cacheDirectory} could not be created
   */
  public static BuildStepsRunner forBuildImage(
      BuildConfiguration buildConfiguration,
      SourceFilesConfiguration sourceFilesConfiguration,
      Path cacheDirectory,
      boolean useOnlyProjectCache)
      throws CacheDirectoryCreationException {
    Initializer cacheInitializer = getCacheInitializer(cacheDirectory, useOnlyProjectCache);
    return new BuildStepsRunner(
        new BuildImageSteps(buildConfiguration, sourceFilesConfiguration, cacheInitializer));
  }

  /**
   * Creates a runner to build to the Docker daemon. Creates a directory for the cache, if needed.
   *
   * @param useOnlyProjectCache if {@code true}, sets the base layers cache directory to be the same
   *     as the application layers cache directory
   * @throws CacheDirectoryCreationException if the {@code cacheDirectory} could not be created
   */
  public static BuildStepsRunner forBuildToDockerDaemon(
      BuildConfiguration buildConfiguration,
      SourceFilesConfiguration sourceFilesConfiguration,
      Path cacheDirectory,
      boolean useOnlyProjectCache)
      throws CacheDirectoryCreationException {
    Initializer cacheInitializer = getCacheInitializer(cacheDirectory, useOnlyProjectCache);
    return new BuildStepsRunner(
        new BuildDockerSteps(buildConfiguration, sourceFilesConfiguration, cacheInitializer));
  }

  /** @return true if Docker is installed on the user's system. */
  public static boolean isDockerInstalled() {
    try {
      new ProcessBuilder("docker").start();
      return true;
    } catch (IOException ex) {
      return false;
    }
  }

  private static Initializer getCacheInitializer(Path cacheDirectory, boolean useOnlyProjectCache)
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
    return cachesInitializer;
  }

  private static void handleRegistryUnauthorizedException(
      RegistryUnauthorizedException registryUnauthorizedException,
      BuildConfiguration buildConfiguration,
      HelpfulSuggestions helpfulSuggestions)
      throws BuildStepsExecutionException {
    if (registryUnauthorizedException.getHttpResponseException().getStatusCode()
        == HttpStatusCodes.STATUS_CODE_FORBIDDEN) {
      // No permissions for registry/repository.
      throw new BuildStepsExecutionException(
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
        throw new BuildStepsExecutionException(
            helpfulSuggestions.forNoCredentialHelpersDefinedForBaseImage(
                registryUnauthorizedException.getRegistry()),
            registryUnauthorizedException);
      }
      if (isRegistryForTarget && !areTargetImageCredentialsConfigured) {
        throw new BuildStepsExecutionException(
            helpfulSuggestions.forNoCredentialHelpersDefinedForTargetImage(
                registryUnauthorizedException.getRegistry()),
            registryUnauthorizedException);
      }

      // Credential helper probably was not configured correctly or did not have the necessary
      // credentials.
      throw new BuildStepsExecutionException(
          helpfulSuggestions.forCredentialsNotCorrect(registryUnauthorizedException.getRegistry()),
          registryUnauthorizedException);
    }
  }

  private final BuildSteps buildSteps;

  @VisibleForTesting
  BuildStepsRunner(BuildSteps buildSteps) {
    this.buildSteps = buildSteps;
  }

  /**
   * Runs the {@link BuildImageSteps}.
   *
   * @param helpfulSuggestions suggestions to use in help messages for exceptions
   */
  public void build(HelpfulSuggestions helpfulSuggestions) throws BuildStepsExecutionException {
    try {
      // TODO: This logging should be injected via another logging class.
      BuildLogger buildLogger = buildSteps.getBuildConfiguration().getBuildLogger();

      buildLogger.lifecycle("");
      buildLogger.lifecycle(buildSteps.getStartupMessage());

      // Logs the different source files used.
      buildLogger.info("Containerizing application with the following files:");

      buildLogger.info("\tClasses:");
      buildSteps
          .getSourceFilesConfiguration()
          .getClassesFiles()
          .forEach(classesFile -> buildLogger.info("\t\t" + classesFile));

      buildLogger.info("\tResources:");
      buildSteps
          .getSourceFilesConfiguration()
          .getResourcesFiles()
          .forEach(resourceFile -> buildLogger.info("\t\t" + resourceFile));

      buildLogger.info("\tDependencies:");
      buildSteps
          .getSourceFilesConfiguration()
          .getDependenciesFiles()
          .forEach(dependencyFile -> buildLogger.info("\t\t" + dependencyFile));

      buildSteps.run();

      buildLogger.lifecycle("");
      buildLogger.lifecycle(buildSteps.getSuccessMessage());

    } catch (CacheMetadataCorruptedException cacheMetadataCorruptedException) {
      // TODO: Have this be different for Maven and Gradle.
      throw new BuildStepsExecutionException(
          helpfulSuggestions.forCacheMetadataCorrupted(), cacheMetadataCorruptedException);

    } catch (ExecutionException executionException) {
      BuildConfiguration buildConfiguration = buildSteps.getBuildConfiguration();

      if (executionException.getCause() instanceof HttpHostConnectException) {
        // Failed to connect to registry.
        throw new BuildStepsExecutionException(
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
        throw new BuildStepsExecutionException(
            helpfulSuggestions.forUnknownHost(), executionException.getCause());

      } else {
        throw new BuildStepsExecutionException(
            helpfulSuggestions.none(), executionException.getCause());
      }

    } catch (InterruptedException | IOException ex) {
      // TODO: Add more suggestions for various build failures.
      throw new BuildStepsExecutionException(helpfulSuggestions.none(), ex);

    } catch (CacheDirectoryNotOwnedException ex) {
      throw new BuildStepsExecutionException(
          helpfulSuggestions.forCacheDirectoryNotOwned(ex.getCacheDirectory()), ex);
    }
  }
}
