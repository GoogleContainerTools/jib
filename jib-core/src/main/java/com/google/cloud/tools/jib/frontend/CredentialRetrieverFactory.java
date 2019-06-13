/*
 * Copyright 2018 Google LLC.
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

import com.google.cloud.tools.jib.api.Credential;
import com.google.cloud.tools.jib.api.CredentialRetriever;
import com.google.cloud.tools.jib.api.ImageReference;
import com.google.cloud.tools.jib.api.LogEvent;
import com.google.cloud.tools.jib.registry.credentials.CredentialHelperNotFoundException;
import com.google.cloud.tools.jib.registry.credentials.CredentialHelperUnhandledServerUrlException;
import com.google.cloud.tools.jib.registry.credentials.CredentialRetrievalException;
import com.google.cloud.tools.jib.registry.credentials.DockerConfigCredentialRetriever;
import com.google.cloud.tools.jib.registry.credentials.DockerCredentialHelper;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/** Static factories for various {@link CredentialRetriever}s. */
public class CredentialRetrieverFactory {

  /** Used for passing in mock {@link DockerCredentialHelper}s for testing. */
  @VisibleForTesting
  @FunctionalInterface
  interface DockerCredentialHelperFactory {
    DockerCredentialHelper create(String registry, Path credentialHelper);
  }

  /**
   * Defines common credential helpers to use as defaults. Maps from registry suffix to credential
   * helper suffix.
   */
  private static final ImmutableMap<String, String> COMMON_CREDENTIAL_HELPERS =
      ImmutableMap.of("gcr.io", "gcr", "amazonaws.com", "ecr-login");

  /**
   * Creates a new {@link CredentialRetrieverFactory} for an image.
   *
   * @param imageReference the image the credential are for
   * @param logger a consumer for handling log events
   * @return a new {@link CredentialRetrieverFactory}
   */
  public static CredentialRetrieverFactory forImage(
      ImageReference imageReference, Consumer<LogEvent> logger) {
    return new CredentialRetrieverFactory(imageReference, logger, DockerCredentialHelper::new);
  }

  /**
   * Creates a new {@link CredentialRetrieverFactory} for an image.
   *
   * @param imageReference the image the credential are for
   * @return a new {@link CredentialRetrieverFactory}
   */
  public static CredentialRetrieverFactory forImage(ImageReference imageReference) {
    return new CredentialRetrieverFactory(
        imageReference, logEvent -> {}, DockerCredentialHelper::new);
  }

  private final ImageReference imageReference;
  private final Consumer<LogEvent> logger;
  private final DockerCredentialHelperFactory dockerCredentialHelperFactory;

  @VisibleForTesting
  CredentialRetrieverFactory(
      ImageReference imageReference,
      Consumer<LogEvent> logger,
      DockerCredentialHelperFactory dockerCredentialHelperFactory) {
    this.imageReference = imageReference;
    this.logger = logger;
    this.dockerCredentialHelperFactory = dockerCredentialHelperFactory;
  }

  /**
   * Creates a new {@link CredentialRetriever} that returns a known {@link Credential}.
   *
   * @param credential the known credential
   * @param credentialSource the source of the credentials (for logging)
   * @return a new {@link CredentialRetriever}
   */
  public CredentialRetriever known(Credential credential, String credentialSource) {
    return () -> {
      logGotCredentialsFrom(credentialSource);
      return Optional.of(credential);
    };
  }

  /**
   * Creates a new {@link CredentialRetriever} for retrieving credentials via a Docker credential
   * helper, such as {@code docker-credential-gcr}.
   *
   * @param credentialHelper the credential helper executable
   * @return a new {@link CredentialRetriever}
   */
  public CredentialRetriever dockerCredentialHelper(String credentialHelper) {
    return dockerCredentialHelper(Paths.get(credentialHelper));
  }

  /**
   * Creates a new {@link CredentialRetriever} for retrieving credentials via a Docker credential
   * helper, such as {@code docker-credential-gcr}.
   *
   * @param credentialHelper the credential helper executable
   * @return a new {@link CredentialRetriever}
   * @see <a
   *     href="https://github.com/docker/docker-credential-helpers#development">https://github.com/docker/docker-credential-helpers#development</a>
   */
  public CredentialRetriever dockerCredentialHelper(Path credentialHelper) {
    return () -> {
      logger.accept(LogEvent.info("Checking credentials from " + credentialHelper));

      try {
        return Optional.of(retrieveFromDockerCredentialHelper(credentialHelper));

      } catch (CredentialHelperUnhandledServerUrlException ex) {
        logger.accept(
            LogEvent.info(
                "No credentials for " + imageReference.getRegistry() + " in " + credentialHelper));
        return Optional.empty();

      } catch (IOException ex) {
        throw new CredentialRetrievalException(ex);
      }
    };
  }

  /**
   * Creates a new {@link CredentialRetriever} that tries common Docker credential helpers to
   * retrieve credentials based on the registry of the image, such as {@code docker-credential-gcr}
   * for images with the registry as {@code gcr.io}.
   *
   * @return a new {@link CredentialRetriever}
   */
  public CredentialRetriever inferCredentialHelper() {
    List<String> inferredCredentialHelperSuffixes = new ArrayList<>();
    for (String registrySuffix : COMMON_CREDENTIAL_HELPERS.keySet()) {
      if (!imageReference.getRegistry().endsWith(registrySuffix)) {
        continue;
      }
      String inferredCredentialHelperSuffix = COMMON_CREDENTIAL_HELPERS.get(registrySuffix);
      if (inferredCredentialHelperSuffix == null) {
        throw new IllegalStateException("No COMMON_CREDENTIAL_HELPERS should be null");
      }
      inferredCredentialHelperSuffixes.add(inferredCredentialHelperSuffix);
    }

    return () -> {
      for (String inferredCredentialHelperSuffix : inferredCredentialHelperSuffixes) {
        try {
          return Optional.of(
              retrieveFromDockerCredentialHelper(
                  Paths.get(
                      DockerCredentialHelper.CREDENTIAL_HELPER_PREFIX
                          + inferredCredentialHelperSuffix)));

        } catch (CredentialHelperNotFoundException
            | CredentialHelperUnhandledServerUrlException ex) {
          if (ex.getMessage() != null) {
            // Warns the user that the specified (or inferred) credential helper cannot be used.
            logger.accept(LogEvent.info(ex.getMessage()));
            if (ex.getCause() != null && ex.getCause().getMessage() != null) {
              logger.accept(LogEvent.info("  Caused by: " + ex.getCause().getMessage()));
            }
          }

        } catch (IOException ex) {
          throw new CredentialRetrievalException(ex);
        }
      }
      return Optional.empty();
    };
  }

  /**
   * Creates a new {@link CredentialRetriever} that tries to retrieve credentials from Docker config
   * (located at {@code $USER_HOME/.docker/config.json}).
   *
   * @return a new {@link CredentialRetriever}
   * @see DockerConfigCredentialRetriever
   */
  public CredentialRetriever dockerConfig() {
    return dockerConfig(new DockerConfigCredentialRetriever(imageReference.getRegistry()));
  }

  /**
   * Creates a new {@link CredentialRetriever} that tries to retrieve credentials from a custom path
   * to a Docker config.
   *
   * @param dockerConfigFile the path to the Docker config file
   * @return a new {@link CredentialRetriever}
   * @see DockerConfigCredentialRetriever
   */
  public CredentialRetriever dockerConfig(Path dockerConfigFile) {
    return dockerConfig(
        new DockerConfigCredentialRetriever(imageReference.getRegistry(), dockerConfigFile));
  }

  @VisibleForTesting
  CredentialRetriever dockerConfig(
      DockerConfigCredentialRetriever dockerConfigCredentialRetriever) {
    return () -> {
      try {
        Optional<Credential> dockerConfigCredentials =
            dockerConfigCredentialRetriever.retrieve(logger);
        if (dockerConfigCredentials.isPresent()) {
          logger.accept(
              LogEvent.info(
                  "Using credentials from Docker config for " + imageReference.getRegistry()));
          return dockerConfigCredentials;
        }

      } catch (IOException ex) {
        logger.accept(LogEvent.info("Unable to parse Docker config"));
      }
      return Optional.empty();
    };
  }

  private Credential retrieveFromDockerCredentialHelper(Path credentialHelper)
      throws CredentialHelperUnhandledServerUrlException, CredentialHelperNotFoundException,
          IOException {
    Credential credentials =
        dockerCredentialHelperFactory
            .create(imageReference.getRegistry(), credentialHelper)
            .retrieve();
    logGotCredentialsFrom(credentialHelper.getFileName().toString());
    return credentials;
  }

  private void logGotCredentialsFrom(String credentialSource) {
    logger.accept(
        LogEvent.info("Using " + credentialSource + " for " + imageReference.getRegistry()));
  }
}
