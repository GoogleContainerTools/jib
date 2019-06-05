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

package com.google.cloud.tools.jib.plugins.common;

import com.google.cloud.tools.jib.api.Credential;
import com.google.cloud.tools.jib.api.CredentialRetriever;
import com.google.cloud.tools.jib.frontend.CredentialRetrieverFactory;
import com.google.cloud.tools.jib.registry.credentials.DockerCredentialHelper;
import java.io.FileNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Generates a list of default {@link CredentialRetriever}s.
 *
 * <p>The retrievers are, in order of first-checked to last-checked:
 *
 * <ol>
 *   <li>{@link CredentialRetrieverFactory#dockerCredentialHelper} for a known credential helper, if
 *       set
 *   <li>{@link CredentialRetrieverFactory#known} for known credential, if set
 *   <li>{@link CredentialRetrieverFactory#inferCredentialHelper}
 *   <li>{@link CredentialRetrieverFactory#dockerConfig}
 * </ol>
 */
public class DefaultCredentialRetrievers {

  /**
   * Creates a new {@link DefaultCredentialRetrievers} with a given {@link
   * CredentialRetrieverFactory}.
   *
   * @param credentialRetrieverFactory the {@link CredentialRetrieverFactory} to generate the {@link
   *     CredentialRetriever}s
   * @return a new {@link DefaultCredentialRetrievers}
   */
  public static DefaultCredentialRetrievers init(
      CredentialRetrieverFactory credentialRetrieverFactory) {
    return new DefaultCredentialRetrievers(credentialRetrieverFactory);
  }

  private final CredentialRetrieverFactory credentialRetrieverFactory;

  @Nullable private CredentialRetriever knownCredentialRetriever;
  @Nullable private CredentialRetriever inferredCredentialRetriever;
  @Nullable private String credentialHelper;

  private DefaultCredentialRetrievers(CredentialRetrieverFactory credentialRetrieverFactory) {
    this.credentialRetrieverFactory = credentialRetrieverFactory;
  }

  /**
   * Sets the known {@link Credential} to use in the default credential retrievers.
   *
   * @param knownCredential the known credential
   * @param credentialSource the source of the known credential (for logging)
   * @return this
   */
  public DefaultCredentialRetrievers setKnownCredential(
      Credential knownCredential, String credentialSource) {
    knownCredentialRetriever = credentialRetrieverFactory.known(knownCredential, credentialSource);
    return this;
  }

  /**
   * Sets the inferred {@link Credential} to use in the default credential retrievers.
   *
   * @param inferredCredential the inferred credential
   * @param credentialSource the source of the inferred credential (for logging)
   * @return this
   */
  public DefaultCredentialRetrievers setInferredCredential(
      Credential inferredCredential, String credentialSource) {
    inferredCredentialRetriever =
        credentialRetrieverFactory.known(inferredCredential, credentialSource);
    return this;
  }

  /**
   * Sets the known credential helper. May either be a path to a credential helper executable, or a
   * credential helper suffix (following {@code docker-credential-}).
   *
   * @param credentialHelper the path to a credential helper, or a credential helper suffix
   *     (following {@code docker-credential-}).
   * @return this
   */
  public DefaultCredentialRetrievers setCredentialHelper(@Nullable String credentialHelper) {
    this.credentialHelper = credentialHelper;
    return this;
  }

  /**
   * Makes a list of {@link CredentialRetriever}s.
   *
   * @return the list of {@link CredentialRetriever}s
   * @throws FileNotFoundException if a credential helper path is specified, but the file doesn't
   *     exist
   */
  public List<CredentialRetriever> asList() throws FileNotFoundException {
    List<CredentialRetriever> credentialRetrievers = new ArrayList<>();
    if (knownCredentialRetriever != null) {
      credentialRetrievers.add(knownCredentialRetriever);
    }
    if (credentialHelper != null) {
      // If credential helper contains file separator, treat as path; otherwise treat as suffix
      if (credentialHelper.contains(FileSystems.getDefault().getSeparator())) {
        if (Files.exists(Paths.get(credentialHelper))) {
          credentialRetrievers.add(
              credentialRetrieverFactory.dockerCredentialHelper(credentialHelper));
        } else {
          throw new FileNotFoundException(
              "Specified credential helper was not found: " + credentialHelper);
        }
      } else {
        credentialRetrievers.add(
            credentialRetrieverFactory.dockerCredentialHelper(
                DockerCredentialHelper.CREDENTIAL_HELPER_PREFIX + credentialHelper));
      }
    }
    if (inferredCredentialRetriever != null) {
      credentialRetrievers.add(inferredCredentialRetriever);
    }
    credentialRetrievers.add(credentialRetrieverFactory.dockerConfig());
    credentialRetrievers.add(credentialRetrieverFactory.inferCredentialHelper());
    return credentialRetrievers;
  }
}
