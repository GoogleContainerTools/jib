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

package com.google.cloud.tools.jib.maven;

import com.google.cloud.tools.jib.http.Authorizations;
import com.google.cloud.tools.jib.registry.credentials.RegistryCredentials;
import com.google.common.annotations.VisibleForTesting;
import javax.annotation.Nullable;
import org.apache.maven.plugin.MojoExecutionException;
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
class MavenSettingsServerCredentials {

  @VisibleForTesting static final String CREDENTIAL_SOURCE = "Maven settings";

  private final Settings settings;
  private final SettingsDecrypter settingsDecrypter;

  MavenSettingsServerCredentials(Settings settings, @Nullable SettingsDecrypter settingsDecrypter) {
    this.settings = settings;
    this.settingsDecrypter = settingsDecrypter;
  }

  /**
   * Attempts to retrieve credentials for {@code registry} from Maven settings.
   *
   * @param registry the registry
   * @return the credentials for the registry
   * @throws MojoExecutionException if the credentials could not be retrieved
   */
  @Nullable
  RegistryCredentials retrieve(@Nullable String registry) throws MojoExecutionException {
    if (registry == null) {
      return null;
    }

    Server registryServer = settings.getServer(registry);
    if (registryServer == null) {
      return null;
    }

    if (settingsDecrypter != null) {
      SettingsDecryptionRequest request = new DefaultSettingsDecryptionRequest(registryServer);
      SettingsDecryptionResult result = settingsDecrypter.decrypt(request);
      // un-encrypted passwords are passed through, so a problem indicates a real issue
      for (SettingsProblem problem : result.getProblems()) {
        if (problem.getSeverity() == SettingsProblem.Severity.ERROR
            || problem.getSeverity() == SettingsProblem.Severity.FATAL) {
          throw new MojoExecutionException(
              "Unable to decrypt password for " + registry + ": " + problem);
        }
      }
      if (result.getServer() != null) {
        registryServer = result.getServer();
      }
    }

    return new RegistryCredentials(
        CREDENTIAL_SOURCE,
        Authorizations.withBasicCredentials(
            registryServer.getUsername(), registryServer.getPassword()));
  }
}
