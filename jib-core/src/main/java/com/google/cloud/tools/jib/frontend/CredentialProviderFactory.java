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

import com.google.cloud.tools.jib.JibLogger;
import com.google.cloud.tools.jib.configuration.credentials.CredentialProvider;
import com.google.cloud.tools.jib.configuration.credentials.Credentials;
import com.google.cloud.tools.jib.image.ImageReference;
import com.google.cloud.tools.jib.registry.credentials.DockerCredentialHelperFactory;
import com.google.cloud.tools.jib.registry.credentials.NonexistentServerUrlDockerCredentialHelperException;
import com.google.common.annotations.VisibleForTesting;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Static factories for various {@link CredentialProvider}s. */
public class CredentialProviderFactory {

  /**
   * Creates a new {@link CredentialProviderFactory} for an image.
   *
   * @param imageReference the image the credential are for
   * @param logger a logger for logging
   * @return a new {@link CredentialProviderFactory}
   */
  public static CredentialProviderFactory forImage(
      ImageReference imageReference, JibLogger logger) {
    return new CredentialProviderFactory(imageReference, logger);
  }

  private final ImageReference imageReference;
  private final JibLogger logger;

  private CredentialProviderFactory(ImageReference imageReference, JibLogger logger) {
    this.imageReference = imageReference;
    this.logger = logger;
  }

  /**
   * Creates a new {@link CredentialProvider} for retrieving credentials via a Docker credential
   * helper, such as {@code docker-credential-gcr}.
   *
   * @param credentialHelperSuffix the credential helper executable suffix, following {@code
   *     docker-credential-} (ie. {@code gcr} for {@code docker-credential-gcr})
   * @return a new {@link CredentialProvider}
   */
  public CredentialProvider dockerCredentialHelper(String credentialHelperSuffix) {
    return dockerCredentialHelper(
        Paths.get(DockerCredentialHelperFactory.CREDENTIAL_HELPER_PREFIX + credentialHelperSuffix),
        new DockerCredentialHelperFactory());
  }

  /**
   * Creates a new {@link CredentialProvider} for retrieving credentials via a Docker credential
   * helper, such as {@code docker-credential-gcr}.
   *
   * @param credentialHelper the credential helper executable
   * @return a new {@link CredentialProvider}
   * @see <a
   *     href="https://github.com/docker/docker-credential-helpers#development">https://github.com/docker/docker-credential-helpers#development</a>
   */
  public CredentialProvider dockerCredentialHelper(Path credentialHelper) {
    return dockerCredentialHelper(credentialHelper, new DockerCredentialHelperFactory());
  }

  @VisibleForTesting
  CredentialProvider dockerCredentialHelper(
      Path credentialHelper, DockerCredentialHelperFactory dockerCredentialHelperFactory) {
    String registry = imageReference.getRegistry();

    return () -> {
      logger.info("Checking credentials from " + credentialHelper);

      try {
        Credentials credentials =
            dockerCredentialHelperFactory
                .newDockerCredentialHelper(registry, credentialHelper)
                .retrieve();
        logGotCredentialsFrom(credentialHelper.getFileName().toString());
        return credentials;

      } catch (NonexistentServerUrlDockerCredentialHelperException ex) {
        logger.info("No credentials for " + registry + " in " + credentialHelper);
        return null;
      }
    };
  }

  private void logGotCredentialsFrom(String credentialSource) {
    logger.info("Using " + credentialSource + " for " + imageReference.getRegistry());
  }
}
