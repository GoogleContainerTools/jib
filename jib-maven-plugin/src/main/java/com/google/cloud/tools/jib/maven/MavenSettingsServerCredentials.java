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
import com.google.cloud.tools.jib.plugins.common.InferredAuthException;
import com.google.cloud.tools.jib.plugins.common.InferredAuthProvider;
import com.google.common.annotations.VisibleForTesting;
import java.util.Optional;
import javax.annotation.Nullable;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.building.SettingsProblem;
import org.apache.maven.settings.crypto.DefaultSettingsDecryptionRequest;
import org.apache.maven.settings.crypto.SettingsDecrypter;
import org.apache.maven.settings.crypto.SettingsDecryptionRequest;
import org.apache.maven.settings.crypto.SettingsDecryptionResult;

/**
 * Retrieves credentials for servers defined in <a
 * href="https://maven.apache.org/settings.html">Maven settings</a>.
 */
class MavenSettingsServerCredentials implements InferredAuthProvider {

  static final String CREDENTIAL_SOURCE = "Maven settings file";

  private final Settings settings;
  private final SettingsDecrypter decrypter;

  /**
   * Create new instance.
   *
   * @param settings decrypted Maven settings
   */
  MavenSettingsServerCredentials(Settings settings, SettingsDecrypter decrypter) {
    this.settings = settings;
    this.decrypter = decrypter;
  }

  /**
   * Retrieves credentials for {@code registry} from Maven settings.
   *
   * @param registry the registry
   * @return the auth info for the registry, or {@link Optional#empty} if none could be retrieved
   */
  @Override
  public Optional<AuthProperty> inferAuth(String registry) throws InferredAuthException {

    Server server = getServerFromMavenSettings(registry);
    if (server == null) {
      return Optional.empty();
    }

    SettingsDecryptionRequest request = new DefaultSettingsDecryptionRequest(server);
    SettingsDecryptionResult result = decrypter.decrypt(request);
    // Un-encrypted passwords are passed through, so a problem indicates a real issue.
    // If there are any ERROR or FATAL problems reported, then decryption failed.
    for (SettingsProblem problem : result.getProblems()) {
      if (problem.getSeverity() == SettingsProblem.Severity.ERROR
          || problem.getSeverity() == SettingsProblem.Severity.FATAL) {
        throw new InferredAuthException(
            "Unable to decrypt server(" + registry + ") info from settings.xml: " + problem);
      }
    }
    Server resultServer = result.getServer();

    String username = resultServer.getUsername();
    String password = resultServer.getPassword();

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

  @Nullable
  @VisibleForTesting
  Server getServerFromMavenSettings(String registry) {
    Server server = settings.getServer(registry);
    if (server != null) {
      return server;
    }

    // try without port
    int index = registry.lastIndexOf(':');
    if (index != -1) {
      return settings.getServer(registry.substring(0, index));
    }
    return null;
  }
}
