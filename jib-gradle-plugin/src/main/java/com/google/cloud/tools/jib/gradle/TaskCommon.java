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
import com.google.cloud.tools.jib.api.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.FilePermissions;
import com.google.cloud.tools.jib.api.InvalidImageReferenceException;
import com.google.cloud.tools.jib.plugins.common.HelpfulSuggestions;
import com.google.cloud.tools.jib.plugins.common.IncompatibleBaseImageJavaVersionException;
import com.google.cloud.tools.jib.plugins.common.InvalidAppRootException;
import com.google.cloud.tools.jib.plugins.common.InvalidContainerVolumeException;
import com.google.cloud.tools.jib.plugins.common.InvalidContainerizingModeException;
import com.google.cloud.tools.jib.plugins.common.InvalidCreationTimeException;
import com.google.cloud.tools.jib.plugins.common.InvalidFilesModificationTimeException;
import com.google.cloud.tools.jib.plugins.common.InvalidWorkingDirectoryException;
import com.google.cloud.tools.jib.plugins.common.PropertyNames;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import javax.annotation.Nullable;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.UnknownTaskException;
import org.gradle.api.logging.Logger;
import org.gradle.api.plugins.WarPlugin;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.internal.logging.events.LogEvent;
import org.gradle.internal.logging.events.OutputEventListener;
import org.gradle.internal.logging.slf4j.OutputEventListenerBackedLoggerContext;
import org.slf4j.LoggerFactory;

/** Collection of common methods to share between Gradle tasks. */
class TaskCommon {

  @Nullable
  static TaskProvider<Task> getWarTaskProvider(Project project) {
    if (!project.getPlugins().hasPlugin(WarPlugin.class)) {
      return null;
    }

    if (project.getPlugins().hasPlugin("org.springframework.boot")) {
      try {
        return project.getTasks().named("bootWar");
      } catch (UnknownTaskException ignored) { // fall through
      }
    }

    return project.getTasks().named(WarPlugin.WAR_TASK_NAME);
  }

  /** Disables annoying Apache HTTP client logging. */
  static void disableHttpLogging() {
    // Disables Apache HTTP client logging.
    OutputEventListenerBackedLoggerContext context =
        (OutputEventListenerBackedLoggerContext) LoggerFactory.getILoggerFactory();
    OutputEventListener defaultOutputEventListener = context.getOutputEventListener();
    context.setOutputEventListener(
        event -> {
          LogEvent logEvent = (LogEvent) event;
          if (!logEvent.getCategory().contains("org.apache")) {
            defaultOutputEventListener.onOutput(event);
          }
        });

    // Disables Google HTTP client logging.
    java.util.logging.Logger.getLogger(HttpTransport.class.getName()).setLevel(Level.OFF);
  }

  @Deprecated
  static void checkDeprecatedUsage(JibExtension jibExtension, Logger logger) {
    if (jibExtension.extraDirectoryConfigured
        || System.getProperty(PropertyNames.EXTRA_DIRECTORY_PATH) != null
        || System.getProperty(PropertyNames.EXTRA_DIRECTORY_PERMISSIONS) != null) {
      logger.warn(
          "'jib.extraDirectory', 'jib.extraDirectory.path', and 'jib.extraDirectory.permissions' "
              + "are deprecated; use 'jib.extraDirectories.paths' and "
              + "'jib.extraDirectories.permissions'");

      if (jibExtension.extraDirectoriesConfigured
          || System.getProperty(PropertyNames.EXTRA_DIRECTORIES_PATHS) != null
          || System.getProperty(PropertyNames.EXTRA_DIRECTORIES_PERMISSIONS) != null) {
        throw new IllegalArgumentException(
            "You cannot configure both 'jib.extraDirectory.path' and 'jib.extraDirectories.paths'");
      }
    }

    if (jibExtension.getContainer().getUseCurrentTimestamp()) {
      if (!jibExtension.getContainer().getCreationTime().equals("EPOCH")) {
        throw new IllegalArgumentException(
            "You cannot configure both 'jib.container.useCurrentTimestamp' and "
                + "'jib.container.creationTime'");
      }
      logger.warn(
          "'jib.container.useCurrentTimestamp' is deprecated; use 'jib.container.creationTime' "
              + "with the value 'USE_CURRENT_TIMESTAMP' instead");
    }
  }

  /**
   * Validates and converts a {@code String->String} file-path-to-file-permissions map to an
   * equivalent {@code AbsoluteUnixPath->FilePermission} map.
   *
   * @param stringMap the map to convert (example entry: {@code "/path/on/container" -> "755"})
   * @return the converted map
   */
  static Map<AbsoluteUnixPath, FilePermissions> convertPermissionsMap(
      Map<String, String> stringMap) {
    Map<AbsoluteUnixPath, FilePermissions> permissionsMap = new HashMap<>();
    for (Map.Entry<String, String> entry : stringMap.entrySet()) {
      AbsoluteUnixPath key = AbsoluteUnixPath.get(entry.getKey());
      FilePermissions value = FilePermissions.fromOctalString(entry.getValue());
      permissionsMap.put(key, value);
    }
    return permissionsMap;
  }

  /**
   * Re-throws a Jib exception as a {@link GradleException} with an appropriate message.
   *
   * @param ex the exception to re-throw
   * @throws GradleException with the correct message
   */
  static void rethrowJibException(Exception ex) throws GradleException {
    if (ex instanceof InvalidAppRootException) {
      throw new GradleException(
          "container.appRoot is not an absolute Unix-style path: "
              + ((InvalidAppRootException) ex).getInvalidPathValue(),
          ex);
    }
    if (ex instanceof InvalidContainerizingModeException) {
      throw new GradleException(
          "invalid value for containerizingMode: "
              + ((InvalidContainerizingModeException) ex).getInvalidContainerizingMode(),
          ex);
    }
    if (ex instanceof InvalidWorkingDirectoryException) {
      throw new GradleException(
          "container.workingDirectory is not an absolute Unix-style path: "
              + ((InvalidWorkingDirectoryException) ex).getInvalidPathValue(),
          ex);
    }
    if (ex instanceof InvalidContainerVolumeException) {
      throw new GradleException(
          "container.volumes is not an absolute Unix-style path: "
              + ((InvalidContainerVolumeException) ex).getInvalidVolume(),
          ex);
    }
    if (ex instanceof InvalidFilesModificationTimeException) {
      throw new GradleException(
          "container.filesModificationTime should be an ISO 8601 date-time (see "
              + "DateTimeFormatter.ISO_DATE_TIME) or special keyword \"EPOCH_PLUS_SECOND\": "
              + ((InvalidFilesModificationTimeException) ex).getInvalidFilesModificationTime(),
          ex);
    }
    if (ex instanceof InvalidCreationTimeException) {
      throw new GradleException(
          "container.creationTime should be an ISO 8601 date-time (see "
              + "DateTimeFormatter.ISO_DATE_TIME) or a special keyword (\"EPOCH\", "
              + "\"USE_CURRENT_TIMESTAMP\"): "
              + ((InvalidCreationTimeException) ex).getInvalidCreationTime(),
          ex);
    }
    if (ex instanceof IncompatibleBaseImageJavaVersionException) {
      IncompatibleBaseImageJavaVersionException castedEx =
          (IncompatibleBaseImageJavaVersionException) ex;
      throw new GradleException(
          HelpfulSuggestions.forIncompatibleBaseImageJavaVersionForGradle(
              castedEx.getBaseImageMajorJavaVersion(), castedEx.getProjectMajorJavaVersion()),
          ex);
    }
    if (ex instanceof InvalidImageReferenceException) {
      throw new GradleException(
          HelpfulSuggestions.forInvalidImageReference(
              ((InvalidImageReferenceException) ex).getInvalidReference()),
          ex);
    }
    throw new GradleException(ex.getMessage(), ex);
  }

  private TaskCommon() {}
}
