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

package com.google.cloud.tools.jib.maven;

import com.google.cloud.tools.jib.plugins.common.AuthProperty;
import com.google.cloud.tools.jib.plugins.common.InferredAuthProvider;
import com.google.cloud.tools.jib.plugins.common.InferredAuthRetrievalException;
import java.util.Optional;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.crypto.SettingsDecryptionResult;

/**
 * Retrieves credentials for servers defined in <a
 * href="https://maven.apache.org/settings.html">Maven settings</a>.
 */
class MavenSettingsServerCredentials implements InferredAuthProvider {

  static final String CREDENTIAL_SOURCE = "Maven settings";

  private final SettingsDecryptionResult decryptedSettings;
  private final Settings settings;

  /**
   * Create new instance.
   *
   * @param settings the Maven settings object
   * @param eventDispatcher the Jib event dispatcher
   */
  MavenSettingsServerCredentials(SettingsDecryptionResult decryptedSettings, Settings settings) {
    this.decryptedSettings = decryptedSettings;
    this.settings = settings;
  }

  /**
   * Attempts to retrieve credentials for {@code registry} from Maven settings.
   *
   * @param registry the registry
   * @return the auth info for the registry, or {@link Optional#empty} if none could be retrieved
   * @throws InferredAuthRetrievalException if the credentials could not be retrieved
   */
  @Override
  public Optional<AuthProperty> getAuth(String registry) throws InferredAuthRetrievalException {
    Optional<Server> optionalServer = getServer(registry);
    if (!optionalServer.isPresent()) {
      return Optional.empty();
    }

    String username = optionalServer.get().getUsername();
    String password = optionalServer.get().getPassword();

    return Optional.of(
        new AuthProperty() {

          @Override
          public String getUsername() {
            return username;
          }

          @Override
          public String getPassword() {
            return password;
          }

          @Override
          public String getAuthDescriptor() {
            return CREDENTIAL_SOURCE;
          }

          @Override
          public String getUsernameDescriptor() {
            return CREDENTIAL_SOURCE;
          }

          @Override
          public String getPasswordDescriptor() {
            return CREDENTIAL_SOURCE;
          }
        });
  }

  private Optional<Server> getServer(String registry) {
    for (Server server : decryptedSettings.getServers()) {
      if (registry.equals(server.getId())) {
        return Optional.of(server);
      }
    }

    // if no decrypted servers returned then treat as if no decryption was required
    Server server = settings.getServer(registry);
    if (server != null) {
      return Optional.of(server);
    }
    return Optional.empty();
  }
}
