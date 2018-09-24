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
    SourceSet mainSourceSet = javaConvention.getSourceSets().findByName("main");
    if (mainSourceSet == null) {
      return;
    }

    // Print build.gradle
    System.out.println(project.getBuildFile());

    // Print settings.gradle
    Path settingsFile = project.getProjectDir().toPath().resolve(Settings.DEFAULT_SETTINGS_FILE);
    if (Files.exists(settingsFile)) {
      System.out.println(settingsFile);
    }

    // Print sources
    mainSourceSet.getAllJava().getSourceDirectories().forEach(System.out::println);

    // Print resources
    mainSourceSet.getResources().getSourceDirectories().forEach(System.out::println);

    // Iterate over dependencies
    for (Configuration configuration :
        project.getConfigurations().getByName("runtime").getHierarchy()) {
      for (Dependency dependency : configuration.getDependencies()) {
        if (dependency instanceof ProjectDependency) {
          // If project dependency, find and keep track of jars associated with project dependency
          ProjectDependency projectDependency = (ProjectDependency) dependency;
          String configurationName = projectDependency.getTargetConfiguration();
          if (configurationName == null) {
            configurationName = "default";
          }
          Project dependencyProject = projectDependency.getDependencyProject();
          for (Configuration targetConfiguration :
              dependencyProject.getConfigurations().getByName(configurationName).getHierarchy()) {
            for (PublishArtifact artifact : targetConfiguration.getArtifacts()) {
              if (projectDependencyJars.contains(artifact.getFile())) {
                continue;
              }
              projectDependencyJars.add(artifact.getFile());
            }
          }

          // Find project dependency's sources files and print those
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
  public void listFilesForProject() {
    Preconditions.checkNotNull(jibExtension);
    Project project = getProject();

    // If this is not the root project, be sure to print the root project's build.gradle and
    // settings.gradle
    if (project != project.getRootProject()) {
      System.out.println(project.getRootProject().getBuildFile());
      Path settingsFiles = project.getRootDir().toPath().resolve(Settings.DEFAULT_SETTINGS_FILE);
      if (Files.exists(settingsFiles)) {
        System.out.println(settingsFiles);
      }
    }

    // Print extra layer
    if (project.getPlugins().hasPlugin(JibPlugin.class)) {
      System.out.println(jibExtension.getExtraDirectoryPath());
    }

    // Print subproject sources
    Set<File> skippedJars = new HashSet<>();
    listFilesForProject(project, skippedJars);

    // Print out dependency jars
    for (File file : project.getConfigurations().getByName("runtime")) {
      // Avoid printing non-SNAPSHOT's/duplicates
      if (skippedJars.contains(file) || !file.toString().contains("SNAPSHOT")) {
        continue;
      }
      System.out.println(file);
      skippedJars.add(file);
    }
  }
}
