/*
 * Copyright 2018 Google Inc.
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

package com.google.cloud.tools.jib.registry.credentials;

import com.google.cloud.tools.jib.http.Authorization;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Stores retrieved registry credentials.
 *
 * <p>The credentials are referred to by the registry they are used for.
 */
public class RegistryCredentials {

  private static final RegistryCredentials EMPTY = new RegistryCredentials();

  /** Instantiates with no credentials. */
  public static RegistryCredentials none() {
    return EMPTY;
  }

  /** Instantiates with credentials for a single registry. */
  public static RegistryCredentials of(
      String registry, String credentialSource, Authorization authorization) {
    return new RegistryCredentials().store(registry, credentialSource, authorization);
  }

  /**
   * Retrieves credentials for {@code registries} using the credential helpers referred to by {@code
   * credentialHelperSuffixes}.
   *
   * <p>This obtains the registry credentials, not the <a
   * href="https://docs.docker.com/registry/spec/auth/token/">Docker authentication token</a>.
   */
  public static RegistryCredentials from(
      List<String> credentialHelperSuffixes, List<String> registries)
      throws IOException, NonexistentDockerCredentialHelperException {
    RegistryCredentials registryCredentials = new RegistryCredentials();

    // TODO: These can be done in parallel.
    for (String registry : registries) {
      for (String credentialHelperSuffix : credentialHelperSuffixes) {
        // Attempts to retrieve authorization for the registry using
        // docker-credential-[credentialSource].
        try {
          registryCredentials.store(
              registry,
              "docker-credential-" + credentialHelperSuffix,
              new DockerCredentialHelper(registry, credentialHelperSuffix).retrieve());

        } catch (NonexistentServerUrlDockerCredentialHelperException ex) {
          // No authorization is found, so continues on to the next credential helper.
        }
      }
    }
    return registryCredentials;
  }

  /**
   * Instantiates from a credential source and a map of registry credentials.
   *
   * @param credentialSource the source of the credentials, useful for informing users where the
   *     credentials came from
   * @param registryCredentialMap a map from registries to their respective credentials
   */
  public static RegistryCredentials from(
      String credentialSource, Map<String, Authorization> registryCredentialMap) {
    RegistryCredentials registryCredentials = new RegistryCredentials();
    for (Map.Entry<String, Authorization> registryCredential : registryCredentialMap.entrySet()) {
      registryCredentials.store(
          registryCredential.getKey(), credentialSource, registryCredential.getValue());
    }
    return registryCredentials;
  }

  /** Pair of (source of credentials, {@link Authorization}). */
  private static class AuthorizationSourcePair {

    /**
     * A string representation of where the credentials were retrieved from. This is useful for
     * letting the user know which credentials were used.
     */
    private final String credentialSource;

    private final Authorization authorization;

    private AuthorizationSourcePair(String credentialSource, Authorization authorization) {
      this.credentialSource = credentialSource;
      this.authorization = authorization;
    }
  }

  /** Maps from registry to the credentials for that registry. */
  private final Map<String, AuthorizationSourcePair> credentials = new HashMap<>();

  /** Instantiate using {@link #from}. Immutable after instantiation. */
  private RegistryCredentials() {};

  /** @return {@code true} if there are credentials for {@code registry}; {@code false} otherwise */
  public boolean has(String registry) {
    return credentials.containsKey(registry);
  }

  /**
   * @return the {@code Authorization} retrieved for the {@code registry}, or {@code null} if none
   *     exists
   */
  @Nullable
  public Authorization getAuthorization(String registry) {
    if (credentials.get(registry) == null) {
      return null;
    }
    return credentials.get(registry).authorization;
  }

  /**
   * @return the name of the credential helper used to retrieve authorization for the {@code
   *     registry}, or {@code null} if none exists
   */
  @Nullable
  public String getCredentialSource(String registry) {
    if (credentials.get(registry) == null) {
      return null;
    }
    return credentials.get(registry).credentialSource;
  }

  /** Only to be called in static initializers. */
  private RegistryCredentials store(
      String registry, String credentialSource, Authorization authorization) {
    credentials.put(registry, new AuthorizationSourcePair(credentialSource, authorization));
    return this;
  }
}
