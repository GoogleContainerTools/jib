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
import com.google.cloud.tools.jib.frontend.MultipleClassesFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.logging.Logger;
import org.gradle.jvm.tasks.Jar;

/** Obtains information about a Gradle {@link Project} that uses Jib. */
class ProjectProperties {

  private final Project project;
  private final Logger logger;

  ProjectProperties(Project project, Logger logger) {
    this.project = project;
    this.logger = logger;
  }

  /**
   * @param mainClass the configured main class
   * @return the main class to use for the container entrypoint.
   */
  String getMainClass(@Nullable String mainClass) {
    if (mainClass == null) {
      mainClass = getMainClassFromJarTask();
      if (mainClass == null) {
        getLogger()
            .info(
                "Could not find main class specified in a 'jar' task; attempting to "
                    + "infer main class.");
        try {
          mainClass =
              MainClassFinder.findMainClass(
                  project
                      .getBuildDir()
                      .toPath()
                      .resolve("classes")
                      .resolve("java")
                      .resolve("main")
                      .toFile()
                      .getAbsolutePath());
        } catch (MultipleClassesFoundException | IOException ex) {
          throw new GradleException(
              HelpfulSuggestionsProvider.get("Failed to get main class: " + ex.getMessage())
                  .suggest("add a `mainClass` configuration to jib"));
        }
        if (mainClass == null) {
          throw new GradleException(
              HelpfulSuggestionsProvider.get("Could not infer main class")
                  .suggest("add a `mainClass` configuration to jib"));
        }
      }
    }
    if (!BuildConfiguration.isValidJavaClass(mainClass)) {
      getLogger().warn("'mainClass' is not a valid Java class : " + mainClass);
    }
    return mainClass;
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
