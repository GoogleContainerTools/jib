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
import java.util.Optional;
import java.util.function.Function;
import org.apache.maven.settings.Server;

/**
 * Retrieves credentials for servers defined in <a
 * href="https://maven.apache.org/settings.html">Maven settings</a>.
 */
class MavenSettingsServerCredentials implements Function<String, Optional<AuthProperty>> {

  static final String CREDENTIAL_SOURCE = "Maven settings";

  private final DecryptedMavenSettings settings;

  /**
   * Create new instance.
   *
   * @param settings decrypted Maven settings
   */
  MavenSettingsServerCredentials(DecryptedMavenSettings settings) {
    this.settings = settings;
  }

  /**
   * Retrieves credentials for {@code registry} from Maven settings.
   *
   * @param registry the registry
   * @return the auth info for the registry, or {@link Optional#empty} if none could be retrieved
   */
  @Override
  public Optional<AuthProperty> apply(String registry) {
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
    for (Server server : settings.getServers()) {
      if (registry.equals(server.getId())) {
        return Optional.of(server);
      }
    }
    return Optional.empty();
  }
}
