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

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
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
import java.util.*;
import java.util.function.Consumer;

/** Static factories for various {@link CredentialRetriever}s. */
public class CredentialRetrieverFactory {

  /** Used for passing in mock {@link DockerCredentialHelper}s for testing. */
  @VisibleForTesting
  @FunctionalInterface
  interface DockerCredentialHelperFactory {
    DockerCredentialHelper create(String registry, Path credentialHelper, Map<String, String> environment);
  }

  /** Used for passing in mock {@link GoogleCredentials} for testing. */
  @VisibleForTesting
  @FunctionalInterface
  interface GoogleCredentialsProvider {
    GoogleCredentials get() throws IOException;
  }

  // com.google.api.services.storage.StorageScopes.DEVSTORAGE_READ_WRITE
  // OAuth2 credentials require at least the GCS write scope for GCR push. We need to manually set
  // this scope for "OAuth2 credentials" instantiated from a service account, which are not scoped
  // (i.e., createScopedRequired() returns true). Note that for a service account, the IAM roles of
  // the service account determine the IAM permissions.
  private static final String OAUTH_SCOPE_STORAGE_READ_WRITE =
      "https://www.googleapis.com/auth/devstorage.read_write";

  /** Mapping between well-known credential helpers and registries (suffixes). */
  private static final ImmutableMap<String, String> WELL_KNOWN_CREDENTIAL_HELPERS =
      ImmutableMap.of(
          "gcr.io", "docker-credential-gcr", "amazonaws.com", "docker-credential-ecr-login");

  /**
   * Creates a new {@link CredentialRetrieverFactory} for an image.
   *
   * @param imageReference the image the credential are for
   * @param logger a consumer for handling log events
   * @return a new {@link CredentialRetrieverFactory}
   */
  public static CredentialRetrieverFactory forImage(
      ImageReference imageReference, Consumer<LogEvent> logger) {
    return new CredentialRetrieverFactory(
        imageReference,
        logger,
        DockerCredentialHelper::new,
        GoogleCredentials::getApplicationDefault,
        new HashMap<>());
  }

  public static CredentialRetrieverFactory forImage(
          ImageReference imageReference, Consumer<LogEvent> logger, Map<String, String> environment) {
    return new CredentialRetrieverFactory(
            imageReference,
            logger,
            DockerCredentialHelper::new,
            GoogleCredentials::getApplicationDefault, environment);
  }

  private final ImageReference imageReference;
  private final Consumer<LogEvent> logger;
  private final DockerCredentialHelperFactory dockerCredentialHelperFactory;
  private final GoogleCredentialsProvider googleCredentialsProvider;
  private final Map<String, String> environment;

  @VisibleForTesting
  CredentialRetrieverFactory(
      ImageReference imageReference,
      Consumer<LogEvent> logger,
      DockerCredentialHelperFactory dockerCredentialHelperFactory,
      GoogleCredentialsProvider googleCredentialsProvider,
      Map<String, String> environment) {
    this.imageReference = imageReference;
    this.logger = logger;
    this.dockerCredentialHelperFactory = dockerCredentialHelperFactory;
    this.googleCredentialsProvider = googleCredentialsProvider;
    this.environment = environment;
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
      logGotCredentialsFrom("credentials from " + credentialSource);
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
   * Creates a new {@link CredentialRetriever} that tries well-known Docker credential helpers to
   * retrieve credentials based on the registry of the image, such as {@code docker-credential-gcr}
   * for images with the registry ending with {@code gcr.io}.
   *
   * @return a new {@link CredentialRetriever}
   */
  public CredentialRetriever wellKnownCredentialHelpers() {
    return () -> {
      for (Map.Entry<String, String> entry : WELL_KNOWN_CREDENTIAL_HELPERS.entrySet()) {
        try {
          String registrySuffix = entry.getKey();
          if (imageReference.getRegistry().endsWith(registrySuffix)) {
            String credentialHelper = entry.getValue();
            return Optional.of(retrieveFromDockerCredentialHelper(Paths.get(credentialHelper)));
          }

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
   * (located at {@code System.getProperty("user.home")/.docker/config.json}).
   *
   * @return a new {@link CredentialRetriever}
   * @see DockerConfigCredentialRetriever
   */
  public CredentialRetriever dockerConfig() {
    return dockerConfig(
        DockerConfigCredentialRetriever.create(
            imageReference.getRegistry(),
            Paths.get(System.getProperty("user.home"), ".docker", "config.json")));
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
        DockerConfigCredentialRetriever.create(imageReference.getRegistry(), dockerConfigFile));
  }

  /**
   * Creates a new {@link CredentialRetriever} that tries to retrieve credentials from a legacy
   * Docker config file.
   *
   * @param dockerConfigFile the path to a legacy docker configuration file
   * @return a new {@link CredentialRetriever}
   * @see DockerConfigCredentialRetriever
   */
  public CredentialRetriever legacyDockerConfig(Path dockerConfigFile) {
    return dockerConfig(
        DockerConfigCredentialRetriever.createForLegacyFormat(
            imageReference.getRegistry(), dockerConfigFile));
  }

  /**
   * Creates a new {@link CredentialRetriever} that tries to retrieve credentials from <a
   * href="https://cloud.google.com/docs/authentication/production">Google Application Default
   * Credentials</a>.
   *
   * @return a new {@link CredentialRetriever}
   * @see <a
   *     href="https://cloud.google.com/docs/authentication/production">https://cloud.google.com/docs/authentication/production</a>
   */
  public CredentialRetriever googleApplicationDefaultCredentials() {
    return () -> {
      try {
        if (imageReference.getRegistry().endsWith("gcr.io")) {
          GoogleCredentials googleCredentials = googleCredentialsProvider.get();
          logger.accept(LogEvent.info("Google ADC found"));
          if (googleCredentials.createScopedRequired()) { // not scoped if service account
            // The short-lived OAuth2 access token to be generated from the service account with
            // refreshIfExpired() below will have one-hour expiry (as of Aug 2019). Instead of using
            // an access token, it is technically possible to use the service account private key to
            // auth with GCR, but it does not worth writing complex code to achieve that.
            logger.accept(LogEvent.info("ADC is a service account. Setting GCS read-write scope"));
            List<String> scope = Collections.singletonList(OAUTH_SCOPE_STORAGE_READ_WRITE);
            googleCredentials = googleCredentials.createScoped(scope);
          }
          googleCredentials.refreshIfExpired();

          logGotCredentialsFrom("Google Application Default Credentials");
          AccessToken accessToken = googleCredentials.getAccessToken();
          // https://cloud.google.com/container-registry/docs/advanced-authentication#access_token
          return Optional.of(Credential.from("oauth2accesstoken", accessToken.getTokenValue()));
        }

      } catch (IOException ex) { // Includes the case where ADC is simply not available.
        logger.accept(
            LogEvent.info("ADC not present or error fetching access token: " + ex.getMessage()));
      }
      return Optional.empty();
    };
  }

  @VisibleForTesting
  CredentialRetriever dockerConfig(
      DockerConfigCredentialRetriever dockerConfigCredentialRetriever) {
    return () -> {
      Path configFile = dockerConfigCredentialRetriever.getDockerConfigFile();
      try {
        Optional<Credential> credentials = dockerConfigCredentialRetriever.retrieve(logger);
        if (credentials.isPresent()) {
          logGotCredentialsFrom("credentials from Docker config (" + configFile + ")");
          return credentials;
        }

      } catch (IOException ex) {
        logger.accept(LogEvent.info("Unable to parse Docker config file: " + configFile));
      }
      return Optional.empty();
    };
  }

  private Credential retrieveFromDockerCredentialHelper(Path credentialHelper)
      throws CredentialHelperUnhandledServerUrlException, CredentialHelperNotFoundException,
          IOException {
    Credential credentials =
        dockerCredentialHelperFactory
            .create(imageReference.getRegistry(), credentialHelper, environment)
            .retrieve();
    logGotCredentialsFrom("credential helper " + credentialHelper.getFileName().toString());
    return credentials;
  }

  private void logGotCredentialsFrom(String credentialSource) {
    logger.accept(LogEvent.lifecycle("Using " + credentialSource + " for " + imageReference));
  }
}
