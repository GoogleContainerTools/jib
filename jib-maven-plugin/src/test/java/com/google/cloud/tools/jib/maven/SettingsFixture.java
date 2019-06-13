/*
 * Copyright 2019 Google LLC.
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

import com.google.common.base.Preconditions;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.building.DefaultSettingsBuilderFactory;
import org.apache.maven.settings.building.DefaultSettingsBuildingRequest;
import org.apache.maven.settings.building.SettingsBuilder;
import org.apache.maven.settings.building.SettingsBuildingException;
import org.apache.maven.settings.building.SettingsBuildingRequest;
import org.apache.maven.settings.crypto.DefaultSettingsDecrypter;
import org.apache.maven.settings.crypto.SettingsDecrypter;
import org.sonatype.plexus.components.cipher.DefaultPlexusCipher;
import org.sonatype.plexus.components.sec.dispatcher.DefaultSecDispatcher;

public class SettingsFixture {

  /**
   * Create a new {@link Settings} for testing purposes.
   *
   * @param settingsFile absolute path to settings.xml
   * @return {@link Settings} built from settingsFile
   */
  public static Settings newSettings(Path settingsFile) {
    Preconditions.checkArgument(Files.isRegularFile(settingsFile));
    try {
      SettingsBuilder settingsBuilder = new DefaultSettingsBuilderFactory().newInstance();
      SettingsBuildingRequest settingsRequest = new DefaultSettingsBuildingRequest();
      settingsRequest.setUserSettingsFile(settingsFile.toFile());
      return settingsBuilder.build(settingsRequest).getEffectiveSettings();
    } catch (SettingsBuildingException ex) {
      throw new IllegalStateException("Tests need to be rewritten: " + ex.getMessage(), ex);
    }
  }

  /**
   * Create a new {@link SettingsDecrypter} for testing purposes.
   *
   * @param settingsSecurityFile absolute path to security-settings.xml
   * @return {@link SettingsDecrypter} built from settingsSecurityFile
   */
  public static SettingsDecrypter newSettingsDecrypter(Path settingsSecurityFile) {
    Preconditions.checkArgument(Files.isRegularFile(settingsSecurityFile));
    try {

      DefaultPlexusCipher injectCypher = new DefaultPlexusCipher();

      DefaultSecDispatcher injectedDispatcher = new DefaultSecDispatcher();
      injectedDispatcher.setConfigurationFile(settingsSecurityFile.toAbsolutePath().toString());
      setField(DefaultSecDispatcher.class, injectedDispatcher, "_cipher", injectCypher);

      DefaultSettingsDecrypter settingsDecrypter = new DefaultSettingsDecrypter();
      setField(
          DefaultSettingsDecrypter.class,
          settingsDecrypter,
          "securityDispatcher",
          injectedDispatcher);
      return settingsDecrypter;
    } catch (Exception ex) {
      throw new IllegalStateException("Tests need to be rewritten: " + ex.getMessage(), ex);
    }
  }

  private static <T> void setField(
      Class<T> clazz, T instance, String fieldName, Object injectedField)
      throws NoSuchFieldException, IllegalAccessException {
    Field field = clazz.getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(instance, injectedField);
  }
}
