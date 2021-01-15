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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Represents read-only Jib global configuration. */
public class GlobalConfig {

  private static final String CONFIG_FILENAME = "config.json";

  public static GlobalConfig readConfig() throws IOException, InvalidGlobalConfigException {
    return readConfig(getConfigDir());
  }

  @VisibleForTesting
  static GlobalConfig readConfig(Path configDir) throws IOException, InvalidGlobalConfigException {
    Path configFile = configDir.resolve(CONFIG_FILENAME);

    try {
      if (Files.exists(configFile)) {
        GlobalConfigTemplate configJson =
            JsonTemplateMapper.readJsonFromFile(configFile, GlobalConfigTemplate.class);
        return from(configJson);
      }

      // Generate config file if it doesn't exist
      Files.createDirectories(configDir);
      Path tempConfigFile = Files.createTempFile(configDir, CONFIG_FILENAME, null);
      tempConfigFile.toFile().deleteOnExit();

      GlobalConfigTemplate configJson = new GlobalConfigTemplate();
      try (OutputStream outputStream = Files.newOutputStream(tempConfigFile)) {
        JsonTemplateMapper.writeTo(configJson, outputStream);
        Files.move(tempConfigFile, configFile);
        return from(configJson);

      } catch (FileAlreadyExistsException ex) {
        // Perhaps created concurrently. Read again.
        return readConfig(configDir);
      }

    } catch (InvalidGlobalConfigException ex) {
      throw new InvalidGlobalConfigException(
          ex.getMessage()
              + "; see https://github.com/GoogleContainerTools/jib/blob/global-config-doc/docs/faq.md#where-is-the-global-jib-configuration-file-and-how-i-can-configure-it "
              + "to fix or you may need to delete "
              + configFile);

    } catch (IOException ex) {
      throw new IOException(
          "Failed to open or parse global Jib config file; see "
              + "https://github.com/GoogleContainerTools/jib/blob/global-config-doc/docs/faq.md#where-is-the-global-jib-configuration-file-and-how-i-can-configure-it "
              + "to fix or you may need to delete "
              + configFile,
          ex);
    }
  }

  private static GlobalConfig from(GlobalConfigTemplate configJson)
      throws InvalidGlobalConfigException {
    Map<String, List<String>> registryMirrors = new HashMap<>();
    for (RegistryMirrorsTemplate mirrorConfig : configJson.getRegistryMirrors()) {
      // validation
      if (Strings.isNullOrEmpty(mirrorConfig.getRegistry())) {
        throw new InvalidGlobalConfigException("'registryMirrors.registry' property is missing");
      }
      if (mirrorConfig.getMirrors().isEmpty()) {
        throw new InvalidGlobalConfigException("'registryMirrors.mirrors' property is missing");
      }

      registryMirrors.put(mirrorConfig.getRegistry(), new ArrayList<>(mirrorConfig.getMirrors()));
    }

    return new GlobalConfig(configJson.isDisableUpdateCheck(), registryMirrors);
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

  private final boolean disableUpdateCheck;
  private final Map<String, List<String>> registryMirrors;

  private GlobalConfig(boolean disableUpdateCheck, Map<String, List<String>> registryMirrors) {
    this.disableUpdateCheck = disableUpdateCheck;
    this.registryMirrors = registryMirrors;
  }

  /**
   * Returns whether to disable update check.
   *
   * @return whether update check is disabled
   */
  public boolean isDisableUpdateCheck() {
    return Boolean.getBoolean(PropertyNames.DISABLE_UPDATE_CHECKS) || disableUpdateCheck;
  }

  /**
   * Gets the registry mirror configuration.
   *
   * @return registry mirrors
   */
  public Map<String, List<String>> getRegistryMirrors() {
    Map<String, List<String>> copy = new HashMap<>();
    for (Map.Entry<String, List<String>> entry : registryMirrors.entrySet()) {
      copy.put(entry.getKey(), new ArrayList<>(entry.getValue()));
    }
    return copy;
  }
}
