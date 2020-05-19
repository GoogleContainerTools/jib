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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.cloud.tools.jib.api.LogEvent;
import com.google.cloud.tools.jib.filesystem.XdgDirectories;
import com.google.cloud.tools.jib.http.FailoverHttpClient;
import com.google.cloud.tools.jib.http.Request;
import com.google.cloud.tools.jib.http.Response;
import com.google.cloud.tools.jib.json.JsonTemplate;
import com.google.cloud.tools.jib.json.JsonTemplateMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/** Checks if Jib is up-to-date. */
public class UpdateChecker {

  private static final String CONFIG_FILENAME = "config.json";
  private static final String LAST_UPDATE_CHECK_FILENAME = "lastUpdateCheck";

  /** JSON template for the configuration file used to enable/disable update checks. */
  @VisibleForTesting
  static class ConfigJsonTemplate implements JsonTemplate {
    private boolean disableUpdateCheck;

    @VisibleForTesting
    void setDisableUpdateCheck(boolean disableUpdateCheck) {
      this.disableUpdateCheck = disableUpdateCheck;
    }
  }

  /** JSON template for content downloaded during version check. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  private static class VersionJsonTemplate implements JsonTemplate {
    private String latest = "";
  }

  /**
   * Begins checking for an update in a separate thread.
   *
   * @param executorService the {@link ExecutorService}
   * @param log {@link Consumer} used to log messages
   * @param versionUrl the location to check for the latest version
   * @param toolName the tool name
   * @param toolVersion the tool version
   * @return a new {@link UpdateChecker}
   */
  public static Future<Optional<String>> checkForUpdate(
      ExecutorService executorService,
      Consumer<LogEvent> log,
      String versionUrl,
      String toolName,
      String toolVersion) {
    return executorService.submit(
        () -> performUpdateCheck(log, toolVersion, versionUrl, getConfigDir(), toolName));
  }

  @VisibleForTesting
  static Optional<String> performUpdateCheck(
      Consumer<LogEvent> log,
      String currentVersion,
      String versionUrl,
      Path configDir,
      String toolName) {
    // Abort if offline or update checks are disabled
    if (Boolean.getBoolean(PropertyNames.DISABLE_UPDATE_CHECKS)) {
      return Optional.empty();
    }

    Path configFile = configDir.resolve(CONFIG_FILENAME);
    Path lastUpdateCheck = configDir.resolve(LAST_UPDATE_CHECK_FILENAME);

    try {
      // Check global config
      if (Files.exists(configFile) && Files.size(configFile) > 0) {
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
                  "Failed to read global Jib config; you may need to fix or delete "
                      + configFile
                      + ": "
                      + ex.getMessage()));
          return Optional.empty();
        }
      } else {
        // Generate config file if it doesn't exist
        ConfigJsonTemplate config = new ConfigJsonTemplate();
        Files.createDirectories(configDir);
        Path tempConfigFile = configDir.resolve(CONFIG_FILENAME + ".tmp");
        try (OutputStream outputStream = Files.newOutputStream(tempConfigFile)) {
          JsonTemplateMapper.writeTo(config, outputStream);
          tryAtomicMove(tempConfigFile, configFile);
        } catch (IOException ex) {
          // If attempt to generate new config file failed, delete so we can try again next time
          log.accept(LogEvent.debug("Failed to generate global Jib config; " + ex.getMessage()));
          try {
            Files.deleteIfExists(tempConfigFile);
          } catch (IOException cleanupEx) {
            log.accept(
                LogEvent.debug(
                    "Failed to cleanup "
                        + tempConfigFile.toString()
                        + " -- "
                        + cleanupEx.getMessage()));
          }
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
      FailoverHttpClient httpClient = new FailoverHttpClient(true, false, ignored -> {});
      try {
        Response response =
            httpClient.get(
                new URL(versionUrl),
                Request.builder()
                    .setHttpTimeout(3000)
                    .setUserAgent("jib " + currentVersion + " " + toolName)
                    .build());
        VersionJsonTemplate version =
            JsonTemplateMapper.readJson(response.getBody(), VersionJsonTemplate.class);
        Path lastUpdateCheckTemp = configDir.resolve(LAST_UPDATE_CHECK_FILENAME + ".tmp");
        Files.write(lastUpdateCheckTemp, Instant.now().toString().getBytes(StandardCharsets.UTF_8));
        tryAtomicMove(lastUpdateCheckTemp, lastUpdateCheck);
        if (currentVersion.equals(version.latest)) {
          return Optional.empty();
        }
        return Optional.of(
            "A new version of Jib ("
                + version.latest
                + ") is available (currently using "
                + currentVersion
                + "). Update your build configuration to use the latest features and fixes!");

      } finally {
        httpClient.shutDown();
      }

    } catch (IOException ex) {
      log.accept(LogEvent.debug("Update check failed; " + ex.getMessage()));
    }

    return Optional.empty();
  }

  /**
   * Returns a message indicating Jib should be upgraded if the check succeeded and the current
   * version is outdated, or returns {@code Optional.empty()} if the check was interrupted or did
   * not determine that a later version was available.
   *
   * @param updateMessageFuture the {@link Future} returned by {@link UpdateChecker#checkForUpdate}
   * @return the {@link Optional} message to upgrade Jib if a later version was found, else {@code
   *     Optional.empty()}.
   */
  public static Optional<String> finishUpdateCheck(Future<Optional<String>> updateMessageFuture) {
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

  /**
   * Attempts an atomic move first, and falls back to non-atomic if the file system does not support
   * atomic moves.
   *
   * @param source the source path
   * @param destination the destination path
   * @throws IOException if the move fails
   */
  private static void tryAtomicMove(Path source, Path destination) throws IOException {
    try {
      Files.move(
          source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (AtomicMoveNotSupportedException ignored) {
      Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private UpdateChecker() {}
}
