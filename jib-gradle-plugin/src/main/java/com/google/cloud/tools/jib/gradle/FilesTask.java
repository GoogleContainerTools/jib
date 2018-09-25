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
   * Recursive function for printing out a project's artifacts. Calls itself when it encounters a
   * project dependency.
   *
   * @param project the project to list the artifacts for
   * @param projectDependencyJars the set of jar files associated with each project dependency
   *     encountered
   */
  private static void listFilesForProject(Project project, Set<File> projectDependencyJars) {
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
    mainSourceSet.getAllSource().getSourceDirectories().forEach(System.out::println);

    // Find project dependencies
    for (Configuration configuration :
        project
            .getConfigurations()
            .getByName(mainSourceSet.getRuntimeConfigurationName())
            .getHierarchy()) {
      for (Dependency dependency : configuration.getDependencies()) {
        if (dependency instanceof ProjectDependency) {
          // Keep track of project dependency jars
          ProjectDependency projectDependency = (ProjectDependency) dependency;
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

          // Print project dependency's build/source/resource files
          listFilesForProject(dependencyProject, projectDependencyJars);
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

    // Print extra layer
    System.out.println(jibExtension.getExtraDirectoryPath());

    // Print sub-project sources
    Set<File> skippedJars = new HashSet<>();
    listFilesForProject(project, skippedJars);

    // Print out SNAPSHOT non-project dependency jars
    for (File file : project.getConfigurations().getByName("runtime")) {
      if (!skippedJars.contains(file) && file.toString().contains("SNAPSHOT")) {
        System.out.println(file);
        skippedJars.add(file);
      }
    }
  }
}
