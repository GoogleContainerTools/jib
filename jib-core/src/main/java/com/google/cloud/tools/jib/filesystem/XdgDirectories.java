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

package com.google.cloud.tools.jib.filesystem;

import com.google.common.annotations.VisibleForTesting;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Obtains OS-specific directories based on the XDG Base Directory Specification.
 *
 * <p>Specifically, from the specification:
 *
 * <ul>
 *   <li>These directories are defined by the environment variables {@code $XDG_CACHE_HOME} and
 *       {@code $XDG_CONFIG_HOME}.
 *   <li>If {@code $XDG_CACHE_HOME} / {@code $XDG_CONFIG_HOME} is either not set or empty, a
 *       platform-specific equivalent of {@code $HOME/.cache} / {@code $HOME/.config} should be
 *       used.
 * </ul>
 *
 * @see <a
 *     href="https://specifications.freedesktop.org/basedir-spec/basedir-spec-latest.html">https://specifications.freedesktop.org/basedir-spec/basedir-spec-latest.html</a>
 */
public class XdgDirectories {

  private static final Logger LOGGER = Logger.getLogger(XdgDirectories.class.getName());
  private static final Path JIB_SUBDIRECTORY_LINUX =
      Paths.get("google-cloud-tools-java").resolve("jib");
  private static final Path JIB_SUBDIRECTORY_OTHER = Paths.get("Google").resolve("Jib");

  public static Path getCacheHome() {
    return getCacheHome(System.getProperties(), System.getenv());
  }

  public static Path getConfigHome() {
    return getConfigHome(System.getProperties(), System.getenv());
  }

  /**
   * Returns the default OS-specific cache directory.
   *
   * <p>For Linux, this is {@code $HOME/.cache/google-cloud-tools-java/jib/}.
   *
   * <p>For Windows, this is {@code %LOCALAPPDATA%\Google\Jib\Cache\}.
   *
   * <p>For macOS, this is {@code $HOME/Library/Caches/Google/Jib/}.
   */
  @VisibleForTesting
  static Path getCacheHome(Properties properties, Map<String, String> environment) {
    return getOsSpecificDirectory(
        properties, environment, "XDG_CACHE_HOME", ".cache", "Cache", "Caches");
  }

  /**
   * Returns the default OS-specific config directory.
   *
   * <p>For Linux, this is {@code $HOME/.config/google-cloud-tools-java/jib/}.
   *
   * <p>For Windows, this is {@code %LOCALAPPDATA%\Google\Jib\Config\}.
   *
   * <p>For macOS, this is {@code $HOME/Library/Preferences/Google/Jib/}.
   */
  @VisibleForTesting
  static Path getConfigHome(Properties properties, Map<String, String> environment) {
    return getOsSpecificDirectory(
        properties, environment, "XDG_CONFIG_HOME", ".config", "Config", "Preferences");
  }

  /**
   * Helper method for resolving directories on different operating systems.
   *
   * @param xdgEnvVariable the name of the environment variable used to resolve the XDG base
   *     directory
   * @param linuxFolder ".config" or ".cache"
   * @param windowsFolder "Config" or "Cache"
   * @param macFolder "Preferences" or "Caches"
   * @return the full path constructed from the given parameters
   */
  private static Path getOsSpecificDirectory(
      Properties properties,
      Map<String, String> environment,
      String xdgEnvVariable,
      String linuxFolder,
      String windowsFolder,
      String macFolder) {

    Path windowsSubDirectory = JIB_SUBDIRECTORY_OTHER.resolve(windowsFolder);
    String rawOsName = properties.getProperty("os.name");
    String osName = rawOsName.toLowerCase(Locale.ENGLISH);
    String xdgHome = environment.get(xdgEnvVariable);
    String userHome = properties.getProperty("user.home");
    Path xdgPath = Paths.get(userHome, linuxFolder);

    if (osName.contains("linux")) {
      // Use XDG environment variable if set and not empty.
      if (xdgHome != null && !xdgHome.trim().isEmpty()) {
        return Paths.get(xdgHome).resolve(JIB_SUBDIRECTORY_LINUX);
      }
      return xdgPath.resolve(JIB_SUBDIRECTORY_LINUX);

    } else if (osName.contains("windows")) {
      // Use XDG environment variable if set and not empty.
      if (xdgHome != null && !xdgHome.trim().isEmpty()) {
        return Paths.get(xdgHome).resolve(windowsSubDirectory);
      }

      // Use %LOCALAPPDATA% for Windows.
      String localAppDataEnv = environment.get("LOCALAPPDATA");
      if (localAppDataEnv == null || localAppDataEnv.trim().isEmpty()) {
        LOGGER.warning("LOCALAPPDATA environment is invalid or missing");
        return xdgPath.resolve(windowsSubDirectory);
      }
      Path localAppData = Paths.get(localAppDataEnv);
      if (!Files.exists(localAppData)) {
        LOGGER.log(Level.WARNING, "{} does not exist", localAppData);
        return xdgPath.resolve(windowsSubDirectory);
      }
      return localAppData.resolve(windowsSubDirectory);

    } else if (osName.contains("mac") || osName.contains("darwin")) {
      // Use XDG environment variable if set and not empty.
      if (xdgHome != null && !xdgHome.trim().isEmpty()) {
        return Paths.get(xdgHome).resolve(JIB_SUBDIRECTORY_OTHER);
      }

      // Use '~/Library/...' for macOS.
      Path macDirectory = Paths.get(userHome, "Library", macFolder);
      if (!Files.exists(macDirectory)) {
        LOGGER.log(Level.WARNING, "{} does not exist", macDirectory);
        return xdgPath.resolve(JIB_SUBDIRECTORY_OTHER);
      }
      return macDirectory.resolve(JIB_SUBDIRECTORY_OTHER);
    }

    throw new IllegalStateException("Unknown OS: " + rawOsName);
  }

  private XdgDirectories() {}
}
