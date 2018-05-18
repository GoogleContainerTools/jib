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
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.Task;
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
        throw new GradleException(
            HelpfulSuggestionsProvider.get("Could not find main class specified in a 'jar' task")
                .suggest("add a `mainClass` configuration to jib"));
      }
    }
    if (!BuildConfiguration.isValidJavaClass(mainClass)) {
      getLogger().warn("'mainClass' is not a valid Java class : " + mainClass);
    }
    return mainClass;
  }

  GradleBuildLogger getLogger() {
    return gradleBuildLogger;
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
