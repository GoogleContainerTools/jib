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
import com.google.cloud.tools.jib.http.FailoverHttpClient;
import com.google.cloud.tools.jib.http.Request;
import com.google.cloud.tools.jib.http.Response;
import com.google.cloud.tools.jib.json.JsonTemplate;
import com.google.cloud.tools.jib.json.JsonTemplateMapper;
import com.google.cloud.tools.jib.plugins.common.globalconfig.GlobalConfig;
import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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

  private static final String LAST_UPDATE_CHECK_FILENAME = "lastUpdateCheck";

  /** JSON template for content downloaded during version check. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  private static class VersionJsonTemplate implements JsonTemplate {
    private String latest = "";
  }

  /**
   * Begins checking for an update in a separate thread.
   *
   * @param executorService the {@link ExecutorService}
   * @param versionUrl the location to check for the latest version
   * @param toolName the tool name
   * @param toolVersion the tool version
   * @param log {@link Consumer} used to log messages
   * @return a new {@link UpdateChecker}
   */

  @VisibleForTesting
  static int compareVersions(String v1, String v2) {
    String[] p1 = v1.split("\\.");
    String[] p2 = v2.split("\\.");

    int len = Math.max(p1.length, p2.length);
    for (int i = 0; i < len; i++) {
      int a = i < p1.length ? parsePart(p1[i]) : 0;
      int b = i < p2.length ? parsePart(p2[i]) : 0;
      if (a != b) {
        return Integer.compare(a, b);
      }
    }
    return 0;
  }

  private static int parsePart(String s) {
    try {
      // Extract leading numeric portion (e.g., "1-SNAPSHOT" → "1")
      String digits = s.replaceAll("^(\\d+).*$", "$1");
      return Integer.parseInt(digits);
    } catch (NumberFormatException e) {
      return 0;
    }
  }


  public static Future<Optional<String>> checkForUpdate(
      ExecutorService executorService,
      String versionUrl,
      String toolName,
      String toolVersion,
      Consumer<LogEvent> log) {
    return executorService.submit(
        () ->
            performUpdateCheck(
                GlobalConfig.getConfigDir(), toolVersion, versionUrl, toolName, log));
  }

  @VisibleForTesting
  static Optional<String> performUpdateCheck(
      Path configDir,
      String currentVersion,
      String versionUrl,
      String toolName,
      Consumer<LogEvent> log) {
    Path lastUpdateCheck = configDir.resolve(LAST_UPDATE_CHECK_FILENAME);

    try {
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

        Path lastUpdateCheckTemp =
            Files.createTempFile(configDir, LAST_UPDATE_CHECK_FILENAME, null);
        lastUpdateCheckTemp.toFile().deleteOnExit();
        Files.write(lastUpdateCheckTemp, Instant.now().toString().getBytes(StandardCharsets.UTF_8));
        Files.move(lastUpdateCheckTemp, lastUpdateCheck, StandardCopyOption.REPLACE_EXISTING);

        if (version.latest == null || version.latest.isEmpty()) {
          return Optional.empty();
        }

        if (compareVersions(currentVersion, version.latest) < 0) {
          return Optional.of(version.latest); // only report if newer
        }

        return Optional.empty(); // otherwise equal or older

      } finally {
        httpClient.shutDown();
      }

    } catch (IOException ex) {
      log.accept(LogEvent.debug("Update check failed; " + ex.getMessage()));
    }

    return Optional.empty();
  }

  /**
   * Returns the latest Jib version available if the check succeeded and the current version is
   * outdated, or returns {@code Optional.empty()} if the check was interrupted or did not determine
   * that a later version was available.
   *
   * @param updateMessageFuture the {@link Future} returned by {@link UpdateChecker#checkForUpdate}
   * @return the latest version, if found, else {@code Optional.empty()}.
   */
  public static Optional<String> finishUpdateCheck(Future<Optional<String>> updateMessageFuture) {
    if (updateMessageFuture.isDone()) {
      try {
        return updateMessageFuture.get();
      } catch (InterruptedException | ExecutionException ex) {
        // No need to restore the interrupted status. The intention here is to silently consume any
        // kind of error
      }
    }
    updateMessageFuture.cancel(true);
    return Optional.empty();
  }

  private UpdateChecker() {}
}
