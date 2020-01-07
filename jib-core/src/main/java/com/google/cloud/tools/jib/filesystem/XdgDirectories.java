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
   * Returns {@code $XDG_CACHE_HOME}, if available, or resolves the OS-specific user cache home
   * based.
   *
   * <p>For Linux, this is {@code $HOME/.cache/}.
   *
   * <p>For Windows, this is {@code %LOCALAPPDATA%}.
   *
   * <p>For macOS, this is {@code $HOME/Library/Application Support/}.
   */
  @VisibleForTesting
  static Path getCacheHome(Properties properties, Map<String, String> environment) {
    // Use environment variable $XDG_CACHE_HOME if set and not empty.
    String xdgCacheHome = environment.get("XDG_CACHE_HOME");
    if (xdgCacheHome != null && !xdgCacheHome.trim().isEmpty()) {
      return Paths.get(xdgCacheHome);
    }

    String userHome = properties.getProperty("user.home");
    Path xdgPath = Paths.get(userHome, ".cache");

    String rawOsName = properties.getProperty("os.name");
    String osName = rawOsName.toLowerCase(Locale.ENGLISH);

    if (osName.contains("linux")) {
      return xdgPath;

    } else if (osName.contains("windows")) {
      // Use %LOCALAPPDATA% for Windows.
      String localAppDataEnv = environment.get("LOCALAPPDATA");
      if (localAppDataEnv == null || localAppDataEnv.trim().isEmpty()) {
        LOGGER.warning("LOCALAPPDATA environment is invalid or missing");
        return xdgPath;
      }
      Path localAppData = Paths.get(localAppDataEnv);
      if (!Files.exists(localAppData)) {
        LOGGER.warning(localAppData + " does not exist");
        return xdgPath;
      }
      return localAppData;

    } else if (osName.contains("mac") || osName.contains("darwin")) {
      // Use '~/Library/Application Support/' for macOS.
      Path applicationSupport = Paths.get(userHome, "Library", "Application Support");
      if (!Files.exists(applicationSupport)) {
        LOGGER.warning(applicationSupport + " does not exist");
        return xdgPath;
      }
      return applicationSupport;
    }

    throw new IllegalStateException("Unknown OS: " + rawOsName);
  }

  /**
   * Returns config directory based on {@code $XDG_CONFIG_HOME}, if available, or resolves the
   * OS-specific user config directory.
   *
   * <p>For Linux, this is {@code $HOME/.config/google-cloud-tools-java/jib/}.
   *
   * <p>For Windows, this is {@code %LOCALAPPDATA%/Google/Jib/}.
   *
   * <p>For macOS, this is {@code $HOME/Library/Preferences/Google/Jib/}.
   */
  @VisibleForTesting
  static Path getConfigHome(Properties properties, Map<String, String> environment) {
    Path windowsSubDirectory = JIB_SUBDIRECTORY_OTHER.resolve("Config");
    String rawOsName = properties.getProperty("os.name");
    String osName = rawOsName.toLowerCase(Locale.ENGLISH);

    // Use environment variable $XDG_CONFIG_HOME if set and not empty.
    String xdgConfigHome = environment.get("XDG_CONFIG_HOME");
    if (xdgConfigHome != null && !xdgConfigHome.trim().isEmpty()) {
      if (osName.contains("linux")) {
        return Paths.get(xdgConfigHome).resolve(JIB_SUBDIRECTORY_LINUX);
      } else if (osName.contains("windows")) {
        return Paths.get(xdgConfigHome).resolve(windowsSubDirectory);
      }
      return Paths.get(xdgConfigHome).resolve(JIB_SUBDIRECTORY_OTHER);
    }

    String userHome = properties.getProperty("user.home");
    Path xdgPath = Paths.get(userHome, ".config");

    if (osName.contains("linux")) {
      return xdgPath.resolve(JIB_SUBDIRECTORY_LINUX);

    } else if (osName.contains("windows")) {
      // Use %LOCALAPPDATA% for Windows.
      String localAppDataEnv = environment.get("LOCALAPPDATA");
      if (localAppDataEnv == null || localAppDataEnv.trim().isEmpty()) {
        LOGGER.warning("LOCALAPPDATA environment is invalid or missing");
        return xdgPath.resolve(windowsSubDirectory);
      }
      Path localAppData = Paths.get(localAppDataEnv);
      if (!Files.exists(localAppData)) {
        LOGGER.warning(localAppData + " does not exist");
        return xdgPath.resolve(windowsSubDirectory);
      }
      return localAppData.resolve(windowsSubDirectory);

    } else if (osName.contains("mac") || osName.contains("darwin")) {
      // Use '~/Library/Preferences/' for macOS.
      Path applicationSupport = Paths.get(userHome, "Library", "Preferences");
      if (!Files.exists(applicationSupport)) {
        LOGGER.warning(applicationSupport + " does not exist");
        return xdgPath.resolve(JIB_SUBDIRECTORY_OTHER);
      }
      return applicationSupport.resolve(JIB_SUBDIRECTORY_OTHER);
    }

    throw new IllegalStateException("Unknown OS: " + rawOsName);
  }

  private XdgDirectories() {}
}
