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

import com.google.common.base.Preconditions;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nullable;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.initialization.Settings;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskAction;

/**
 * Print out changing source dependencies on a project.
 *
 * <p>Expected use: {@code ./gradlew _jibSkaffoldFiles -q} or {@code ./gradlew
 * :<subproject>:_jibSkaffoldFiles -q}
 */
public class FilesTask extends DefaultTask {

  /**
   * Prints the locations of a project's build.gradle, settings.gradle, and gradle.properties.
   *
   * @param project the project
   */
  private static void printGradleFiles(Project project) {
    Path projectPath = project.getProjectDir().toPath();

    // Print build.gradle
    System.out.println(project.getBuildFile());

    // Print settings.gradle
    Path settingsFiles = projectPath.resolve(Settings.DEFAULT_SETTINGS_FILE);
    if (Files.exists(settingsFiles)) {
      System.out.println(settingsFiles);
    }

    // Print gradle.properties
    Path propertiesFile = projectPath.resolve("gradle.properties");
    if (Files.exists(propertiesFile)) {
      System.out.println(projectPath.resolve("gradle.properties"));
    }
  }

  /**
   * Prints build files, sources, and resources associated with a project.
   *
   * @param project the project
   */
  private static void printProjectFiles(Project project) {
    JavaPluginConvention javaConvention =
        project.getConvention().getPlugin(JavaPluginConvention.class);
    SourceSet mainSourceSet =
        javaConvention.getSourceSets().findByName(SourceSet.MAIN_SOURCE_SET_NAME);
    if (mainSourceSet == null) {
      return;
    }

    // Print build config, settings, etc.
    printGradleFiles(project);

    // Print sources + resources
    mainSourceSet
        .getAllSource()
        .getSourceDirectories()
        .forEach(
            sourceDirectory -> {
              if (sourceDirectory.exists()) {
                System.out.println(sourceDirectory);
              }
            });
  }

  /**
   * Recursive function for printing out a project's artifacts. Calls itself when it encounters a
   * project dependency.
   *
   * @param project the project to list the artifacts for
   * @param projectDependencies the set of project dependencies encountered. When a project
   *     dependency is encountered, it is added to this set.
   */
  private static void findProjectDependencies(
      Project project, Set<ProjectDependency> projectDependencies) {
    for (Configuration configuration :
        project.getConfigurations().getByName("runtime").getHierarchy()) {
      for (Dependency dependency : configuration.getDependencies()) {
        if (dependency instanceof ProjectDependency) {
          projectDependencies.add((ProjectDependency) dependency);
          findProjectDependencies(
              ((ProjectDependency) dependency).getDependencyProject(), projectDependencies);
        }
      }
    }
  }

  @Nullable private JibExtension jibExtension;

  public FilesTask setJibExtension(JibExtension jibExtension) {
    this.jibExtension = jibExtension;
    return this;
  }

  @TaskAction
  public void listFiles() {
    Preconditions.checkNotNull(jibExtension);
    Project project = getProject();

    // If this is not the root project, print the root project's build.gradle and settings.gradle
    if (project != project.getRootProject()) {
      printGradleFiles(project.getRootProject());
    }

    printProjectFiles(project);

    // Print extra layer
    if (Files.exists(jibExtension.getExtraDirectoryPath())) {
      System.out.println(jibExtension.getExtraDirectoryPath());
    }

    // Find project dependencies
    Set<ProjectDependency> projectDependencies = new HashSet<>();
    findProjectDependencies(project, projectDependencies);

    Set<File> projectDependencyJars = new HashSet<>();
    for (ProjectDependency projectDependency : projectDependencies) {
      printProjectFiles(projectDependency.getDependencyProject());

      // Keep track of project dependency jars for filtering out later
      String configurationName = projectDependency.getTargetConfiguration();
      if (configurationName == null) {
        configurationName = "default";
      }
      Project dependencyProject = projectDependency.getDependencyProject();
      for (Configuration targetConfiguration :
          dependencyProject.getConfigurations().getByName(configurationName).getHierarchy()) {
        for (PublishArtifact artifact : targetConfiguration.getArtifacts()) {
          projectDependencyJars.add(artifact.getFile());
        }
      }
    }

    // Print out SNAPSHOT, non-project dependency jars
    for (File file : project.getConfigurations().getByName("runtime")) {
      if (!projectDependencyJars.contains(file) && file.toString().contains("SNAPSHOT")) {
        System.out.println(file);
        projectDependencyJars.add(file); // Add to set to avoid printing the same files twice
      }
    }
  }
}
