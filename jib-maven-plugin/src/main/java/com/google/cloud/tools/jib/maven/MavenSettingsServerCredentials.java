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

import com.google.cloud.tools.jib.configuration.credentials.Credential;
import com.google.common.annotations.VisibleForTesting;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

  static final String CREDENTIAL_SOURCE = "Maven settings";

  // pattern cribbed directly from
  // https://github.com/sonatype/plexus-cipher/blob/master/src/main/java/org/sonatype/plexus/components/cipher/DefaultPlexusCipher.java
  private static final Pattern ENCRYPTED_STRING_PATTERN =
      Pattern.compile(".*?[^\\\\]?\\{(.*?[^\\\\])\\}.*");

  /**
   * Return true if the given string appears to have been encrypted with the <a
   * href="https://maven.apache.org/guides/mini/guide-encryption.html#How_to_encrypt_server_passwords">Maven
   * password encryption</a>. Such passwords appear between unescaped braces.
   */
  @VisibleForTesting
  static boolean isEncrypted(String password) {
    Matcher matcher = ENCRYPTED_STRING_PATTERN.matcher(password);
    return matcher.matches() || matcher.find();
  }

  private final Settings settings;
  @Nullable private final SettingsDecrypter settingsDecrypter;
  private final MavenJibLogger mavenJibLogger;

  /**
   * Create new instance.
   *
   * @param settings the Maven settings object
   * @param settingsDecrypter the Maven decrypter component
   * @param mavenJibLogger the Maven build log
   */
  MavenSettingsServerCredentials(
      Settings settings,
      @Nullable SettingsDecrypter settingsDecrypter,
      MavenJibLogger mavenJibLogger) {
    this.settings = settings;
    this.settingsDecrypter = settingsDecrypter;
    this.mavenJibLogger = mavenJibLogger;
  }

  /**
   * Attempts to retrieve credentials for {@code registry} from Maven settings.
   *
   * @param registry the registry
   * @return the credentials for the registry, or {@link Optional#empty} if none could be retrieved
   * @throws MojoExecutionException if the credentials could not be retrieved
   */
  Optional<Credential> retrieve(@Nullable String registry) throws MojoExecutionException {
    if (registry == null) {
      return Optional.empty();
    }

    Server registryServer = settings.getServer(registry);
    if (registryServer == null) {
      return Optional.empty();
    }

    if (settingsDecrypter != null) {
      // SettingsDecrypter and SettingsDecryptionResult do not document the meanings of the return
      // results. SettingsDecryptionResult#getServers() does note that the list of decrypted servers
      // can be empty.  We handle the results as follows:
      //    - if there are any ERROR or FATAL problems reported, then decryption failed
      //    - if no decrypted servers returned then treat as if no decryption was required
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
    } else if (isEncrypted(registryServer.getPassword())) {
      mavenJibLogger.warn(
          "Server password for registry "
              + registry
              + " appears to be encrypted, but there is no decrypter available");
    }

    return Optional.of(
        Credential.basic(registryServer.getUsername(), registryServer.getPassword()));
  }
}
