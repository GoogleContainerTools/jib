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

import com.google.cloud.tools.jib.configuration.credentials.Credential;
import com.google.cloud.tools.jib.configuration.credentials.CredentialRetriever;
import com.google.cloud.tools.jib.frontend.CredentialRetrieverFactory;
import com.google.cloud.tools.jib.registry.credentials.DockerCredentialHelper;
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
  @Nullable private String credentialHelperSuffix;

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
   * Sets the suffix for a known credential helper.
   *
   * @param credentialHelperSuffix the known credential helper suffix (following {@code
   *     docker-credential-})
   * @return this
   */
  public DefaultCredentialRetrievers setCredentialHelperSuffix(
      @Nullable String credentialHelperSuffix) {
    this.credentialHelperSuffix = credentialHelperSuffix;
    return this;
  }

  /**
   * Makes a list of {@link CredentialRetriever}s.
   *
   * @return the list of {@link CredentialRetriever}s
   */
  public List<CredentialRetriever> asList() {
    List<CredentialRetriever> credentialRetrievers = new ArrayList<>();
    if (knownCredentialRetriever != null) {
      credentialRetrievers.add(knownCredentialRetriever);
    }
    if (credentialHelperSuffix != null) {
      credentialRetrievers.add(
          credentialRetrieverFactory.dockerCredentialHelper(
              DockerCredentialHelper.CREDENTIAL_HELPER_PREFIX + credentialHelperSuffix));
    }
    if (inferredCredentialRetriever != null) {
      credentialRetrievers.add(inferredCredentialRetriever);
    }
    credentialRetrievers.add(credentialRetrieverFactory.inferCredentialHelper());
    credentialRetrievers.add(credentialRetrieverFactory.dockerConfig());
    return credentialRetrievers;
  }
}
