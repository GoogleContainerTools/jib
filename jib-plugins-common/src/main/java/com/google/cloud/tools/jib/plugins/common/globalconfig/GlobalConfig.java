/*
 * Copyright 2021 Google LLC.
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

package com.google.cloud.tools.jib.plugins.common.globalconfig;

import com.google.cloud.tools.jib.filesystem.XdgDirectories;
import com.google.cloud.tools.jib.json.JsonTemplateMapper;
import com.google.cloud.tools.jib.plugins.common.PropertyNames;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Represents read-only Jib global configuration. */
public class GlobalConfig {

  private static final String CONFIG_FILENAME = "config.json";

  public static GlobalConfig readConfig() throws IOException {
    return readConfig(getConfigDir());
  }

  @VisibleForTesting
  static GlobalConfig readConfig(Path configDir) throws IOException {
    Path configFile = configDir.resolve(CONFIG_FILENAME);

    try {
      if (Files.exists(configFile)) {
        GlobalConfigTemplate configJson =
            JsonTemplateMapper.readJsonFromFile(configFile, GlobalConfigTemplate.class);
        return new GlobalConfig(configJson);
      }

      // Generate config file if it doesn't exist
      Files.createDirectories(configDir);
      Path tempConfigFile = Files.createTempFile(configDir, CONFIG_FILENAME, null);
      tempConfigFile.toFile().deleteOnExit();

      GlobalConfigTemplate configJson = new GlobalConfigTemplate();
      try (OutputStream outputStream = Files.newOutputStream(tempConfigFile)) {
        JsonTemplateMapper.writeTo(configJson, outputStream);
        Files.move(tempConfigFile, configFile);
        return new GlobalConfig(configJson);

      } catch (FileAlreadyExistsException ex) {
        // Perhaps created concurrently. Read again.
        return readConfig(configDir);
      }

    } catch (IOException ex) {
      throw new IOException(
          "Failed to read global Jib config; you may need to fix or delete " + configFile, ex);
    }
  }

  /**
   * Returns the config directory set by {@link PropertyNames#CONFIG_DIRECTORY} if not null,
   * otherwise returns the default config directory.
   *
   * @return the config directory set by {@link PropertyNames#CONFIG_DIRECTORY} if not null,
   *     otherwise returns the default config directory.
   */
  public static Path getConfigDir() {
    String configDirProperty = System.getProperty(PropertyNames.CONFIG_DIRECTORY);
    if (!Strings.isNullOrEmpty(configDirProperty)) {
      return Paths.get(configDirProperty);
    }
    return XdgDirectories.getConfigHome();
  }

  private final GlobalConfigTemplate jsonConfig;

  private GlobalConfig(GlobalConfigTemplate jsonConfig) {
    this.jsonConfig = jsonConfig;
  }

  public boolean isDisableUpdateCheck() {
    return Boolean.getBoolean(PropertyNames.DISABLE_UPDATE_CHECKS)
        || jsonConfig.isDisableUpdateCheck();
  }
}
