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

package com.google.cloud.tools.jib.plugins.common;

import com.google.cloud.tools.jib.filesystem.XdgDirectories;
import com.google.cloud.tools.jib.json.JsonTemplate;
import com.google.cloud.tools.jib.json.JsonTemplateMapper;
import com.google.common.annotations.VisibleForTesting;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

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
   * @param skip if {@code true}, the update check itself will be skipped
   * @param version the current version of Jib being used
   * @param versionUrl the location to check for the latest version
   * @param executorService the {@link ExecutorService}
   * @return a new {@link UpdateChecker}
   */
  public static UpdateChecker checkForUpdate(
      boolean skip, String version, String versionUrl, ExecutorService executorService) {
    return new UpdateChecker(
        executorService.submit(
            () -> performUpdateCheck(skip, version, versionUrl, XdgDirectories.getConfigHome())));
  }

  @VisibleForTesting
  static Optional<String> performUpdateCheck(
      boolean skip, String currentVersion, String versionUrl, Path configDir) {
    // Abort if offline or update checks are disabled
    if (skip || Boolean.getBoolean(PropertyNames.DISABLE_UPDATE_CHECKS)) {
      return Optional.empty();
    }

    try {
      Path configFile = configDir.resolve("config.json");
      if (Files.exists(configFile)) {
        // Abort of update checks are disabled
        ConfigJsonTemplate config =
            JsonTemplateMapper.readJsonFromFile(configFile, ConfigJsonTemplate.class);
        if (config.disableUpdateCheck) {
          return Optional.empty();
        }
      } else {
        // Generate config file if it doesn't exist
        ConfigJsonTemplate config = new ConfigJsonTemplate();
        Files.createDirectories(configDir);
        JsonTemplateMapper.writeTo(config, Files.newOutputStream(configFile));
      }

      // Check time of last update check
      Path lastUpdateCheck = configDir.resolve("lastUpdateCheck");
      if (Files.exists(lastUpdateCheck)) {
        String fileContents =
            new String(Files.readAllBytes(lastUpdateCheck), StandardCharsets.UTF_8);
        try {
          Instant modifiedTime = Instant.parse(fileContents);
          if (modifiedTime.plus(Duration.ofDays(1)).isAfter(Instant.now())) {
            return Optional.empty();
          }
        } catch (DateTimeParseException ex) {
          // Ignore parse failure; assume update check hasn't been performed
        }
      }

      // Check for update
      URLConnection connection = new URL(versionUrl).openConnection();
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

    } catch (IOException ignored) {
      // Fail silently
    }

    return Optional.empty();
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
