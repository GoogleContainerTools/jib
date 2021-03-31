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

package com.google.cloud.tools.jib.gradle;

import com.google.api.client.http.HttpTransport;
import com.google.cloud.tools.jib.ProjectInfo;
import com.google.cloud.tools.jib.api.LogEvent;
import com.google.cloud.tools.jib.api.buildplan.FilePermissions;
import com.google.cloud.tools.jib.plugins.common.ProjectProperties;
import com.google.cloud.tools.jib.plugins.common.UpdateChecker;
import com.google.cloud.tools.jib.plugins.common.globalconfig.GlobalConfig;
import com.google.common.util.concurrent.Futures;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Level;
import javax.annotation.Nullable;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.UnknownTaskException;
import org.gradle.api.logging.Logger;
import org.gradle.api.plugins.WarPlugin;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.internal.logging.events.OutputEventListener;
import org.gradle.internal.logging.slf4j.OutputEventListenerBackedLoggerContext;
import org.slf4j.LoggerFactory;

/** Collection of common methods to share between Gradle tasks. */
class TaskCommon {

  public static final String VERSION_URL = "https://storage.googleapis.com/jib-versions/jib-gradle";

  static Future<Optional<String>> newUpdateChecker(
          ProjectProperties projectProperties, GlobalConfig globalConfig, Logger logger) {
    if (projectProperties.isOffline()
            || !logger.isLifecycleEnabled()
            || globalConfig.isDisableUpdateCheck()) {
      return Futures.immediateFuture(Optional.empty());
    }
    ExecutorService executorService = Executors.newSingleThreadExecutor();
    try {
      return UpdateChecker.checkForUpdate(
              executorService,
              VERSION_URL,
              projectProperties.getToolName(),
              projectProperties.getToolVersion(),
              projectProperties::log);
    } finally {
      executorService.shutdown();
    }
  }

  static void finishUpdateChecker(
          ProjectProperties projectProperties, Future<Optional<String>> updateCheckFuture) {
    UpdateChecker.finishUpdateCheck(updateCheckFuture)
            .ifPresent(
                    updateMessage ->
                            projectProperties.log(
                                    LogEvent.lifecycle(
                                            "\n\u001B[33m"
                                                    + updateMessage
                                                    + "\n"
                                                    + ProjectInfo.GITHUB_URL
                                                    + "/blob/master/jib-gradle-plugin/CHANGELOG.md\u001B[0m\n"
                                                    + "Please see https://github.com/GoogleContainerTools/jib/blob/master/docs/privacy.md for info on disabling this update check.\n")));
  }

  @Nullable
  static TaskProvider<Task> getWarTaskProvider(Project project) {
    if (project.getPlugins().hasPlugin(WarPlugin.class)) {
      return project.getTasks().named(WarPlugin.WAR_TASK_NAME);
    }
    return null;
  }

  @Nullable
  static TaskProvider<Task> getBootWarTaskProvider(Project project) {
    if (project.getPlugins().hasPlugin("org.springframework.boot")) {
      try {
        return project.getTasks().named("bootWar");
      } catch (UnknownTaskException ignored) { // fall through
      }
    }
    return null;
  }

  /** Disables annoying Apache HTTP client logging. */
  static void disableHttpLogging() {
    // Disables Apache HTTP client logging.
    OutputEventListenerBackedLoggerContext context =
            (OutputEventListenerBackedLoggerContext) LoggerFactory.getILoggerFactory();
    OutputEventListener defaultOutputEventListener = context.getOutputEventListener();
    context.setOutputEventListener(
            event -> {
              org.gradle.internal.logging.events.LogEvent logEvent =
                      (org.gradle.internal.logging.events.LogEvent) event;
              if (!logEvent.getCategory().contains("org.apache")) {
                defaultOutputEventListener.onOutput(event);
              }
            });

    // Disable Google HTTP Client network logging only when 'java.util.logging.config.file' system
    // property is undefined: https://github.com/GoogleContainerTools/jib/issues/2356
    if (System.getProperty("java.util.logging.config.file") == null) {
      // Disables Google HTTP client logging.
      java.util.logging.Logger.getLogger(HttpTransport.class.getName()).setLevel(Level.OFF);
    }
  }

  /**
   * Converts a {@code String->String} file-path-to-file-permissions map to an equivalent {@code
   * String->FilePermission} map.
   *
   * @param stringMap the map to convert (example entry: {@code "/path/on/container" -> "755"})
   * @return the converted map
   */
  static Map<String, FilePermissions> convertPermissionsMap(Map<String, String> stringMap) {
    // Order is important, so use a LinkedHashMap
    Map<String, FilePermissions> permissionsMap = new LinkedHashMap<>();
    for (Map.Entry<String, String> entry : stringMap.entrySet()) {
      permissionsMap.put(entry.getKey(), FilePermissions.fromOctalString(entry.getValue()));
    }
    return permissionsMap;
  }

  private TaskCommon() {}
}