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

import com.google.cloud.tools.jib.builder.BuildConfiguration;
import com.google.cloud.tools.jib.builder.SourceFilesConfiguration;
import com.google.cloud.tools.jib.frontend.MainClassFinder;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.jvm.tasks.Jar;

/** Obtains information about a Gradle {@link Project} that uses Jib. */
class ProjectProperties {

  private static final String PLUGIN_NAME = "jib";

  /** Directory name for the cache. The directory will be relative to the build output directory. */
  private static final String CACHE_DIRECTORY_NAME = "jib-cache";

  private final Project project;
  private final GradleBuildLogger gradleBuildLogger;
  private final SourceFilesConfiguration sourceFilesConfiguration;

  /** @return a ProjectProperties from the given project and logger. */
  static ProjectProperties getForProject(Project project, GradleBuildLogger gradleBuildLogger) {
    try {
      return new ProjectProperties(
          project, gradleBuildLogger, GradleSourceFilesConfiguration.getForProject(project));
    } catch (IOException ex) {
      throw new GradleException("Obtaining project build output files failed", ex);
    }
  }

  Path getCacheDirectory() {
    return project.getBuildDir().toPath().resolve(CACHE_DIRECTORY_NAME);
  }

  @VisibleForTesting
  ProjectProperties(
      Project project,
      GradleBuildLogger gradleBuildLogger,
      SourceFilesConfiguration sourceFilesConfiguration) {
    this.project = project;
    this.gradleBuildLogger = gradleBuildLogger;
    this.sourceFilesConfiguration = sourceFilesConfiguration;
  }

  /**
   * If {@code mainClass} is {@code null}, tries to infer main class in this order:
   *
   * <ul>
   *   <li>1. Looks in a {@code jar} task.
   *   <li>2. Searches for a class defined with a main method.
   * </ul>
   *
   * <p>Warns if main class is not valid.
   *
   * @throws GradleException if no valid main class is not found.
   */
  String getMainClass(@Nullable String mainClass) {
    if (mainClass == null) {
      gradleBuildLogger.info(
          "Searching for main class... Add a 'mainClass' configuration to '"
              + PLUGIN_NAME
              + "' to improve build speed.");
      mainClass = getMainClassFromJarTask();
      if (mainClass == null || !BuildConfiguration.isValidJavaClass(mainClass)) {
        gradleBuildLogger.debug(
            "Could not find a valid main class specified in a 'jar' task; attempting to infer main "
                + "class.");

        try {
          // Adds each file in each classes output directory to the classes files list.
          List<String> mainClasses = new ArrayList<>();
          for (Path classPath : sourceFilesConfiguration.getClassesFiles()) {
            mainClasses.addAll(MainClassFinder.findMainClasses(classPath));
          }

          if (mainClasses.size() == 1) {
            // Valid class found; use inferred main class
            mainClass = mainClasses.get(0);
          } else if (mainClasses.size() == 0 && mainClass == null) {
            // No main class found anywhere
            throw new GradleException(
                HelpfulSuggestionsProvider.get("Main class was not found")
                    .forMainClassNotFound(PLUGIN_NAME));
          } else if (mainClasses.size() > 1 && mainClass == null) {
            // More than one main class found with no jar plugin to fall back on; error
            throw new GradleException(
                HelpfulSuggestionsProvider.get(
                        "Multiple valid main classes were found: " + String.join(", ", mainClasses))
                    .forMainClassNotFound(PLUGIN_NAME));
          }
        } catch (IOException ex) {
          throw new GradleException(
              HelpfulSuggestionsProvider.get("Failed to get main class")
                  .forMainClassNotFound(PLUGIN_NAME),
              ex);
        }
      }
    }
    Preconditions.checkNotNull(mainClass);
    if (!BuildConfiguration.isValidJavaClass(mainClass)) {
      gradleBuildLogger.warn("'mainClass' is not a valid Java class : " + mainClass);
    }

    return mainClass;
  }

  /** @return the {@link SourceFilesConfiguration} based on the current project */
  SourceFilesConfiguration getSourceFilesConfiguration() {
    return sourceFilesConfiguration;
  }

  /** Extracts main class from 'jar' task, if available. */
  @Nullable
  private String getMainClassFromJarTask() {
    List<Task> jarTasks = new ArrayList<>(project.getTasksByName("jar", false));
    if (jarTasks.size() != 1) {
      return null;
    }
    return (String) ((Jar) jarTasks.get(0)).getManifest().getAttributes().get("Main-Class");
  }
}
