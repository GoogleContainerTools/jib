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
