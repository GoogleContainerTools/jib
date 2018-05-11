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
import com.google.cloud.tools.jib.builder.SourceFilesConfiguration;
import com.google.cloud.tools.jib.cache.CacheDirectoryNotOwnedException;
import com.google.cloud.tools.jib.cache.CacheMetadataCorruptedException;
import com.google.cloud.tools.jib.cache.Caches;
import com.google.cloud.tools.jib.registry.RegistryAuthenticationFailedException;
import com.google.cloud.tools.jib.registry.RegistryUnauthorizedException;
import java.io.IOException;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;
import org.apache.http.conn.HttpHostConnectException;

/**
 * Runs {@link BuildDockerSteps} and builds helpful error messages.
 *
 * <p>TODO: Consolidate with {@link BuildImageStepsRunner}.
 */
public class BuildDockerStepsRunner {

  /**
   * Sets up a new {@link BuildDockerStepsRunner}. Creates the directory for the cache, if needed.
   *
   * @param useOnlyProjectCache if {@code true}, sets the base layers cache directory to be the same
   *     as the application layers cache directory
   * @throws CacheDirectoryCreationException if the {@code cacheDirectory} could not be created
   */
  public static BuildDockerStepsRunner newRunner(
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

    return new BuildDockerStepsRunner(
        buildConfiguration, sourceFilesConfiguration, cachesInitializer);
  }

  private final Supplier<BuildDockerSteps> buildDockerStepsSupplier;

  private BuildDockerStepsRunner(
      BuildConfiguration buildConfiguration,
      SourceFilesConfiguration sourceFilesConfiguration,
      Caches.Initializer cachesInitializer) {
    buildDockerStepsSupplier =
        () -> new BuildDockerSteps(buildConfiguration, sourceFilesConfiguration, cachesInitializer);
  }

  /**
   * Runs the {@link BuildDockerSteps}.
   *
   * @param helpfulSuggestions suggestions to use in help messages for exceptions
   */
  public void buildDocker(HelpfulSuggestions helpfulSuggestions)
      throws BuildImageStepsExecutionException {
    BuildDockerSteps buildDockerSteps = buildDockerStepsSupplier.get();

    try {
      buildDockerSteps.run();

    } catch (CacheMetadataCorruptedException cacheMetadataCorruptedException) {
      // TODO: Have this be different for Maven and Gradle.
      throw new BuildImageStepsExecutionException(
          helpfulSuggestions.forCacheMetadataCorrupted(), cacheMetadataCorruptedException);

    } catch (ExecutionException executionException) {
      BuildConfiguration buildConfiguration = buildDockerSteps.getBuildConfiguration();

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
      boolean areBaseImageCredentialsConfigured =
          buildConfiguration.getBaseImageCredentialHelperName() != null
              || buildConfiguration.getKnownBaseRegistryCredentials() != null;
      if (isRegistryForBase && !areBaseImageCredentialsConfigured) {
        throw new BuildImageStepsExecutionException(
            helpfulSuggestions.forNoCredentialHelpersDefinedForBaseImage(
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
