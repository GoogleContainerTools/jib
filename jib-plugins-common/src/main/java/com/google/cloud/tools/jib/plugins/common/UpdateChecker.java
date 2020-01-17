/*
 * Copyright 2020 Google LLC.
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

package com.google.cloud.tools.jib.plugins.common;

import com.google.cloud.tools.jib.ProjectInfo;
import com.google.cloud.tools.jib.api.LogEvent;
import com.google.cloud.tools.jib.filesystem.XdgDirectories;
import com.google.cloud.tools.jib.json.JsonTemplate;
import com.google.cloud.tools.jib.json.JsonTemplateMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.base.Verify;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/** Checks if Jib is up-to-date. */
public class UpdateChecker {

  /** JSON template for the configuration file used to enable/disable update checks. */
  @VisibleForTesting
  static class ConfigJsonTemplate implements JsonTemplate {
    private boolean disableUpdateCheck;

    @VisibleForTesting
    void setDisableUpdateCheck(boolean disableUpdateCheck) {
      this.disableUpdateCheck = disableUpdateCheck;
    }
  }

  /**
   * Begins checking for an update in a separate thread.
   *
   * @param log {@link Consumer} used to log messages
   * @param versionUrl the location to check for the latest version
   * @return a new {@link UpdateChecker}
   */
  public static UpdateChecker checkForUpdate(Consumer<LogEvent> log, String versionUrl) {
    ExecutorService executorService = Executors.newSingleThreadExecutor();
    Future<Optional<String>> messageFuture =
        executorService.submit(
            () ->
                performUpdateCheck(
                    log, Verify.verifyNotNull(ProjectInfo.VERSION), versionUrl, getConfigDir()));
    executorService.shutdown();
    return new UpdateChecker(messageFuture);
  }

  @VisibleForTesting
  static Optional<String> performUpdateCheck(
      Consumer<LogEvent> log, String currentVersion, String versionUrl, Path configDir) {
    // Abort if offline or update checks are disabled
    if (Boolean.getBoolean(PropertyNames.DISABLE_UPDATE_CHECKS)) {
      return Optional.empty();
    }

    Path configFile = configDir.resolve("config.json");
    Path lastUpdateCheck = configDir.resolve("lastUpdateCheck");

    try {
      // Check global config
      if (Files.exists(configFile)) {
        // Abort if update checks are disabled
        try {
          ConfigJsonTemplate config =
              JsonTemplateMapper.readJsonFromFile(configFile, ConfigJsonTemplate.class);
          if (config.disableUpdateCheck) {
            return Optional.empty();
          }
        } catch (IOException ex) {
          log.accept(
              LogEvent.warn(
                  "Failed to read global Jib config: "
                      + ex.getMessage()
                      + "; you may need to fix or delete "
                      + configFile
                      + "; "));
          return Optional.empty();
        }
      } else {
        // Generate config file if it doesn't exist
        ConfigJsonTemplate config = new ConfigJsonTemplate();
        Files.createDirectories(configDir);
        try (OutputStream outputStream = Files.newOutputStream(configFile)) {
          JsonTemplateMapper.writeTo(config, outputStream);
        } catch (IOException ex) {
          // If attempt to generate new config file failed, delete so we can try again next time
          log.accept(LogEvent.debug("Failed to generate global Jib config; " + ex.getMessage()));
          Files.deleteIfExists(configFile);
        }
      }

      // Check time of last update check
      if (Files.exists(lastUpdateCheck)) {
        try {
          String fileContents =
              new String(Files.readAllBytes(lastUpdateCheck), StandardCharsets.UTF_8);
          Instant modifiedTime = Instant.parse(fileContents);
          if (modifiedTime.plus(Duration.ofDays(1)).isAfter(Instant.now())) {
            return Optional.empty();
          }
        } catch (DateTimeParseException | IOException ex) {
          // If reading update time failed, file might be corrupt, so delete it
          log.accept(LogEvent.debug("Failed to read lastUpdateCheck; " + ex.getMessage()));
          Files.delete(lastUpdateCheck);
        }
      }

      // Check for update
      HttpURLConnection connection = (HttpURLConnection) new URL(versionUrl).openConnection();
      try {
        connection.setConnectTimeout(3000);
        BufferedReader bufferedReader =
            new BufferedReader(
                new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
        String latestVersion = bufferedReader.readLine().trim();
        Files.write(lastUpdateCheck, Instant.now().toString().getBytes(StandardCharsets.UTF_8));
        if (currentVersion.equals(latestVersion)) {
          return Optional.empty();
        }
        return Optional.of(
            "A new version of Jib ("
                + latestVersion
                + ") is available (currently using "
                + currentVersion
                + "). Update your build configuration to use the latest features and fixes!");

      } finally {
        connection.disconnect();
      }

    } catch (IOException ex) {
      log.accept(LogEvent.debug("Update check failed; " + ex.getMessage()));
    }

    return Optional.empty();
  }

  /**
   * Returns the config directory set by {@link PropertyNames#CONFIG_DIRECTORY} if not null,
   * otherwise returns the default config directory.
   *
   * @return the config directory set by {@link PropertyNames#CONFIG_DIRECTORY} if not null,
   *     otherwise returns the default config directory.
   */
  private static Path getConfigDir() {
    String configDirProperty = System.getProperty(PropertyNames.CONFIG_DIRECTORY);
    if (!Strings.isNullOrEmpty(configDirProperty)) {
      return Paths.get(configDirProperty);
    }
    return XdgDirectories.getConfigHome();
  }

  private final Future<Optional<String>> updateMessageFuture;

  @VisibleForTesting
  UpdateChecker(Future<Optional<String>> updateMessageFuture) {
    this.updateMessageFuture = updateMessageFuture;
  }

  /**
   * Returns a message indicating Jib should be upgraded if the check succeeded and the current
   * version is outdated, or returns {@code Optional.empty()} if the check was interrupted or did
   * not determine that a later version was available.
   *
   * @return the {@link Optional} message to upgrade Jib if a later version was found, else {@code
   *     Optional.empty()}.
   */
  public Optional<String> finishUpdateCheck() {
    if (updateMessageFuture.isDone()) {
      try {
        return updateMessageFuture.get();
      } catch (InterruptedException | ExecutionException ignored) {
        // Fail silently;
      }
    }
    updateMessageFuture.cancel(true);
    return Optional.empty();
  }
}
