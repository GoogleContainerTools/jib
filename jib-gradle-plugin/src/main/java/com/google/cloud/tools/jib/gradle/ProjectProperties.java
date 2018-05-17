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
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;
import org.gradle.jvm.tasks.Jar;

/** Obtains information about a Gradle {@link Project} that uses Jib. */
class ProjectProperties {

  private final Project project;
  private final GradleBuildLogger gradleBuildLogger;

  ProjectProperties(Project project, GradleBuildLogger gradleBuildLogger) {
    this.project = project;
    this.gradleBuildLogger = gradleBuildLogger;
  }

  /**
   * @param mainClass the configured main class
   * @return the main class to use for the container entrypoint.
   */
  String getMainClass(@Nullable String mainClass) {
    if (mainClass == null) {
      mainClass = getMainClassFromJarTask();
      if (mainClass == null) {
        gradleBuildLogger.debug(
            "Could not find main class specified in a 'jar' task; attempting to "
                + "infer main class.");

        final String mainClassSuggestion = "add a `mainClass` configuration to jib";
        try {
          // Adds each file in each classes output directory to the classes files list.
          JavaPluginConvention javaPluginConvention =
              project.getConvention().getPlugin(JavaPluginConvention.class);
          SourceSet mainSourceSet = javaPluginConvention.getSourceSets().getByName("main");
          Path classesDirs = Paths.get(mainSourceSet.getOutput().getClassesDirs().getAsPath());
          List<String> mainClasses = new ArrayList<>(MainClassFinder.findMainClass(classesDirs));

          if (mainClasses.size() == 1) {
            mainClass = mainClasses.get(0);
          } else if (mainClasses.size() == 0) {
            throw new GradleException(
                HelpfulSuggestionsProvider.get("Main class was not found")
                    .suggest(mainClassSuggestion));
          } else {
            throw new GradleException(
                HelpfulSuggestionsProvider.get(
                        "Multiple valid main classes were found: " + String.join(", ", mainClasses))
                    .suggest(mainClassSuggestion));
          }
        } catch (IOException ex) {
          throw new GradleException(
              HelpfulSuggestionsProvider.get("Failed to get main class")
                  .suggest(mainClassSuggestion),
              ex);
        }
      }
    }
    if (!BuildConfiguration.isValidJavaClass(mainClass)) {
      gradleBuildLogger.warn("'mainClass' is not a valid Java class : " + mainClass);
    }
    return mainClass;
  }

  /** @return the {@link SourceFilesConfiguration} based on the current project */
  SourceFilesConfiguration getSourceFilesConfiguration() {
    try {
      return GradleSourceFilesConfiguration.getForProject(project);

    } catch (IOException ex) {
      throw new GradleException("Obtaining project build output files failed", ex);
    }
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
