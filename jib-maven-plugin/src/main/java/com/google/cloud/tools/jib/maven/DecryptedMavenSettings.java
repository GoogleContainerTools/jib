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

import java.util.List;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.settings.Proxy;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.building.SettingsProblem;
import org.apache.maven.settings.crypto.DefaultSettingsDecryptionRequest;
import org.apache.maven.settings.crypto.SettingsDecrypter;
import org.apache.maven.settings.crypto.SettingsDecryptionRequest;
import org.apache.maven.settings.crypto.SettingsDecryptionResult;

/** Provides decrypted Maven settings information. */
class DecryptedMavenSettings {

  static DecryptedMavenSettings from(Settings settings, SettingsDecrypter decryptor)
      throws MojoExecutionException {
    SettingsDecryptionRequest request = new DefaultSettingsDecryptionRequest(settings);
    SettingsDecryptionResult result = decryptor.decrypt(request);
    // Un-encrypted passwords are passed through, so a problem indicates a real issue.
    // If there are any ERROR or FATAL problems reported, then decryption failed.
    for (SettingsProblem problem : result.getProblems()) {
      if (problem.getSeverity() == SettingsProblem.Severity.ERROR
          || problem.getSeverity() == SettingsProblem.Severity.FATAL) {
        throw new MojoExecutionException("Unable to decrypt settings.xml: " + problem);
      }
    }
    return new DecryptedMavenSettings(result, settings);
  }

  private final SettingsDecryptionResult result;
  private final Settings settings;

  private DecryptedMavenSettings(SettingsDecryptionResult result, Settings settings) {
    this.result = result;
    this.settings = settings;
  }

  List<Server> getServers() {
    // SettingsDecrypter and SettingsDecryptionResult do not document the meanings of the return
    // results. SettingsDecryptionResult#getServers() does note that the list of decrypted servers
    // can be empty. If the decrypted result returns an empty list, we fall back to the original
    // settings.
    return result.getServers().isEmpty() ? settings.getServers() : result.getServers();
  }

  List<Proxy> getProxies() {
    return result.getProxies().isEmpty() ? settings.getProxies() : result.getProxies();
  }
}
