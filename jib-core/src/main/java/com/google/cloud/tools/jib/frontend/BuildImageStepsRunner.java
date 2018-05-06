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
import com.google.cloud.tools.jib.builder.SourceFilesConfiguration;
import com.google.cloud.tools.jib.cache.CacheDirectoryNotOwnedException;
import com.google.cloud.tools.jib.cache.CacheMetadataCorruptedException;
import com.google.cloud.tools.jib.cache.Caches;
import com.google.cloud.tools.jib.registry.RegistryAuthenticationFailedException;
import com.google.cloud.tools.jib.registry.RegistryUnauthorizedException;
import com.google.common.annotations.VisibleForTesting;
import org.apache.http.conn.HttpHostConnectException;

import java.io.IOException;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

/** Runs {@link BuildImageSteps} and builds helpful error messages. */
public class BuildImageStepsRunner {

  private static final HelpfulMessageBuilder helpfulMessageBuilder =
      new HelpfulMessageBuilder("Build image failed");

  /**
   * Sets up a new {@link BuildImageStepsRunner}. Creates the directory for the cache, if needed.
   *
   * @param useOnlyProjectCache if {@code true}, sets the base layers cache directory to be the same as the application layers cache directory
   * @throws CacheDirectoryCreationException if the {@code cacheDirectory} could not be created
   */
  public static BuildImageStepsRunner newRunner(BuildConfiguration buildConfiguration, SourceFilesConfiguration sourceFilesConfiguration, Path cacheDirectory, boolean useOnlyProjectCache) throws CacheDirectoryCreationException {
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

    return new BuildImageStepsRunner(buildConfiguration, sourceFilesConfiguration, cachesInitializer);
  }

  private final Supplier<BuildImageSteps> buildImageStepsSupplier;

  @VisibleForTesting
  BuildImageStepsRunner(Supplier<BuildImageSteps> buildImageStepsSupplier) {
    this.buildImageStepsSupplier = buildImageStepsSupplier;
  }

  private BuildImageStepsRunner(BuildConfiguration buildConfiguration, SourceFilesConfiguration sourceFilesConfiguration, Caches.Initializer cachesInitializer) {
    buildImageStepsSupplier = () -> new BuildImageSteps(buildConfiguration, sourceFilesConfiguration, cachesInitializer);
  }

  public void buildImage() throws BuildImageStepsExecutionException {
    BuildImageSteps buildImageSteps = buildImageStepsSupplier.get();

    try {
      buildImageSteps.run();

    } catch (CacheMetadataCorruptedException cacheMetadataCorruptedException) {
      // TODO: Have this be different for Maven and Gradle.
      throw new BuildImageStepsExecutionException(
          helpfulMessageBuilder.withSuggestion("run 'mvn clean' to clear the cache"),
          cacheMetadataCorruptedException);

    } catch (ExecutionException executionException) {
      BuildConfiguration buildConfiguration = buildImageSteps.getBuildConfiguration();

      if (executionException.getCause() instanceof HttpHostConnectException) {
        // Failed to connect to registry.
        throw new BuildImageStepsExecutionException(
            helpfulMessageBuilder.withSuggestion(
                "make sure your Internet is up and that the registry you are pushing to exists"),
            executionException.getCause());

      } else if (executionException.getCause() instanceof RegistryUnauthorizedException) {
        handleRegistryUnauthorizedException(
            (RegistryUnauthorizedException) executionException.getCause(), buildConfiguration);

      } else if (executionException.getCause() instanceof RegistryAuthenticationFailedException
          && executionException.getCause().getCause() instanceof HttpResponseException) {
        handleRegistryUnauthorizedException(
            new RegistryUnauthorizedException(
                buildConfiguration.getTargetRegistry(),
                buildConfiguration.getTargetRepository(),
                (HttpResponseException) executionException.getCause().getCause()),
            buildConfiguration);

      } else if (executionException.getCause() instanceof UnknownHostException) {
        throw new BuildImageStepsExecutionException(
            helpfulMessageBuilder.withSuggestion(
                "make sure that the registry you configured exists/is spelled properly"),
            executionException.getCause());

      } else {
        throw new BuildImageStepsExecutionException(
            helpfulMessageBuilder.withNoHelp(), executionException.getCause());
      }

    } catch (InterruptedException | IOException ex) {
      // TODO: Add more suggestions for various build failures.
      throw new BuildImageStepsExecutionException(helpfulMessageBuilder.withNoHelp(), ex);

    } catch (CacheDirectoryNotOwnedException ex) {
      throw new BuildImageStepsExecutionException(
          helpfulMessageBuilder.withSuggestion(
              "check that '"
                  + ex.getCacheDirectory()
                  + "' is not used by another application or set the `useOnlyProjectCache` "
                  + "configuration"),
          ex);
    }
  }

  private void handleRegistryUnauthorizedException(
      RegistryUnauthorizedException registryUnauthorizedException,
      BuildConfiguration buildConfiguration)
      throws BuildImageStepsExecutionException {
    if (registryUnauthorizedException.getHttpResponseException().getStatusCode()
        == HttpStatusCodes.STATUS_CODE_FORBIDDEN) {
      // No permissions for registry/repository.
      throw new BuildImageStepsExecutionException(
          helpfulMessageBuilder.withSuggestion(
              "make sure you have permissions for "
                  + registryUnauthorizedException.getImageReference()),
          registryUnauthorizedException);

    } else if ((buildConfiguration.getCredentialHelperNames() == null
        || buildConfiguration.getCredentialHelperNames().isEmpty())
        && (buildConfiguration.getKnownRegistryCredentials() == null
        || !buildConfiguration
        .getKnownRegistryCredentials()
        .has(registryUnauthorizedException.getRegistry()))) {
      // No credential helpers defined.
      throw new BuildImageStepsExecutionException(
          // TODO: Have this be different for Maven and Gradle.
          helpfulMessageBuilder.withSuggestion(
              "set a credential helper name with the configuration 'credHelpers' or "
                  + "set credentials for '"
                  + registryUnauthorizedException.getRegistry()
                  + "' in your Maven settings"),
          registryUnauthorizedException);

    } else {
      // Credential helper probably was not configured correctly or did not have the necessary
      // credentials.
      throw new BuildImageStepsExecutionException(
          helpfulMessageBuilder.withSuggestion(
              "make sure your credentials for '"
                  + registryUnauthorizedException.getRegistry()
                  + "' are set up correctly"),
          registryUnauthorizedException);
    }
  }
}
