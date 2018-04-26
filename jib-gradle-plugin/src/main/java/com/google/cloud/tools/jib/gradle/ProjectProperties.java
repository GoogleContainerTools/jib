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

import com.google.cloud.tools.jib.builder.SourceFilesConfiguration;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.logging.Logger;
import org.gradle.jvm.tasks.Jar;

/** Obtains information about a Gradle {@link Project}. */
class ProjectProperties {

  private final Project project;
  private final Logger logger;

  ProjectProperties(Project project, Logger logger) {
    this.project = project;
    this.logger = logger;
  }

  /** Extracts main class from 'jar' task, if available. */
  @Nullable
  String getMainClassFromJarTask() {
    List<Task> jarTasks = new ArrayList<>(project.getTasksByName("jar", false));
    if (jarTasks.size() != 1) {
      return null;
    }
    return (String) ((Jar) jarTasks.get(0)).getManifest().getAttributes().get("Main-Class");
  }

  Logger getLogger() {
    return logger;
  }

  /** @return the {@link SourceFilesConfiguration} based on the current project */
  SourceFilesConfiguration getSourceFilesConfiguration() {
    try {
      SourceFilesConfiguration sourceFilesConfiguration =
          GradleSourceFilesConfiguration.getForProject(project);

      // Logs the different source files used.
      getLogger().lifecycle("");
      getLogger().lifecycle("Containerizing application with the following files:");
      getLogger().lifecycle("");

      getLogger().lifecycle("\tDependencies:");
      getLogger().lifecycle("");
      sourceFilesConfiguration
          .getDependenciesFiles()
          .forEach(dependencyFile -> getLogger().lifecycle("\t\t" + dependencyFile));

      getLogger().lifecycle("\tResources:");
      getLogger().lifecycle("");
      sourceFilesConfiguration
          .getResourcesFiles()
          .forEach(resourceFile -> getLogger().lifecycle("\t\t" + resourceFile));

      getLogger().lifecycle("\tClasses:");
      getLogger().lifecycle("");
      sourceFilesConfiguration
          .getClassesFiles()
          .forEach(classesFile -> getLogger().lifecycle("\t\t" + classesFile));

      getLogger().lifecycle("");

      return sourceFilesConfiguration;

    } catch (IOException ex) {
      throw new GradleException("Obtaining project build output files failed", ex);
    }
  }
}
