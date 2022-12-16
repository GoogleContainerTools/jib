/*
 * Copyright 2019 Google LLC.
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

package com.google.cloud.tools.jib.gradle.skaffold;

import com.google.cloud.tools.jib.gradle.ExtraDirectoryParameters;
import com.google.cloud.tools.jib.gradle.JibExtension;
import com.google.cloud.tools.jib.plugins.common.SkaffoldFilesOutput;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.initialization.Settings;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskAction;

/**
 * Prints out changing source dependencies on a project.
 *
 * <p>Expected use: {@code ./gradlew _jibSkaffoldFilesV2 -q} or {@code ./gradlew
 * :<subproject>:_jibSkaffoldFilesV2 -q}
 */
public class FilesTaskV2 extends DefaultTask {

  private final SkaffoldFilesOutput skaffoldFilesOutput = new SkaffoldFilesOutput();

  private final JibExtension jibExtension;

  @Inject
  public FilesTaskV2(JibExtension jibExtension) {
    this.jibExtension = jibExtension;
  }

  /**
   * Task Action, print files.
   *
   * @throws IOException if an error occurs generating the json string
   */
  @TaskAction
  public void listFiles() throws IOException {
    Project project = getProject();

    // If this is not the root project, add the root project's build.gradle and settings.gradle
    if (project != project.getRootProject()) {
      addGradleFiles(project.getRootProject());
    }

    addProjectFiles(project);

    // Add extra layer
    List<Path> extraDirectories =
        jibExtension.getExtraDirectories().getPaths().stream()
            .map(ExtraDirectoryParameters::getFrom)
            .collect(Collectors.toList());
    extraDirectories.stream().filter(Files::exists).forEach(skaffoldFilesOutput::addInput);

    // Find project dependencies
    Set<ProjectDependency> projectDependencies = findProjectDependencies(project);

    Set<File> projectDependencyJars = new HashSet<>();
    for (ProjectDependency projectDependency : projectDependencies) {
      addProjectFiles(projectDependency.getDependencyProject());

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

    // Add SNAPSHOT, non-project dependency jars
    for (File file :
        project.getConfigurations().getByName(jibExtension.getConfigurationName().get())) {
      if (!projectDependencyJars.contains(file) && file.toString().contains("SNAPSHOT")) {
        skaffoldFilesOutput.addInput(file.toPath());
        projectDependencyJars.add(file); // Add to set to avoid printing the same files twice
      }
    }

    // Configure other files from config
    SkaffoldWatchParameters watch = jibExtension.getSkaffold().getWatch();
    watch.getBuildIncludes().forEach(skaffoldFilesOutput::addBuild);
    watch.getIncludes().forEach(skaffoldFilesOutput::addInput);
    // we don't do any special pre-processing for ignore (input and ignore can overlap with exact
    // matches)
    watch.getExcludes().forEach(skaffoldFilesOutput::addIgnore);

    // Print files
    System.out.println();
    System.out.println("BEGIN JIB JSON");
    System.out.println(skaffoldFilesOutput.getJsonString());
  }

  /**
   * Adds the locations of a project's build.gradle, settings.gradle, and gradle.properties.
   *
   * @param project the project
   */
  private void addGradleFiles(Project project) {
    Path projectPath = project.getProjectDir().toPath();

    // Add build.gradle
    skaffoldFilesOutput.addBuild(project.getBuildFile().toPath());

    // Add settings.gradle
    if (project.getGradle().getStartParameter().getSettingsFile() != null) {
      skaffoldFilesOutput.addBuild(
          project.getGradle().getStartParameter().getSettingsFile().toPath());
    } else if (Files.exists(projectPath.resolve(Settings.DEFAULT_SETTINGS_FILE))) {
      skaffoldFilesOutput.addBuild(projectPath.resolve(Settings.DEFAULT_SETTINGS_FILE));
    }

    // Add gradle.properties
    if (Files.exists(projectPath.resolve("gradle.properties"))) {
      skaffoldFilesOutput.addBuild(projectPath.resolve("gradle.properties"));
    }
  }

  /**
   * Prints build files, sources, and resources associated with a project.
   *
   * @param project the project
   */
  private void addProjectFiles(Project project) {
    // Add build config, settings, etc.
    addGradleFiles(project);

    // Add sources + resources
    SourceSetContainer sourceSetContainer =
        project.getExtensions().findByType(SourceSetContainer.class);
    if (sourceSetContainer != null) {
      SourceSet mainSourceSet = sourceSetContainer.findByName(SourceSet.MAIN_SOURCE_SET_NAME);
      if (mainSourceSet != null) {
        mainSourceSet
            .getAllSource()
            .getSourceDirectories()
            .forEach(
                sourceDirectory -> {
                  if (sourceDirectory.exists()) {
                    skaffoldFilesOutput.addInput(sourceDirectory.toPath());
                  }
                });
      }
    }
  }

  /**
   * Collects a project's project dependencies, including all transitive project dependencies.
   *
   * @param project the project to find the project dependencies for
   * @return the set of project dependencies
   */
  private Set<ProjectDependency> findProjectDependencies(Project project) {
    Set<ProjectDependency> projectDependencies = new HashSet<>();
    Deque<Project> projects = new ArrayDeque<>();
    projects.push(project);

    String configurationName = jibExtension.getConfigurationName().get();

    while (!projects.isEmpty()) {
      Project currentProject = projects.pop();

      // Search through all dependencies
      Configuration runtimeClasspath =
          currentProject.getConfigurations().findByName(configurationName);
      if (runtimeClasspath != null) {
        for (Configuration configuration : runtimeClasspath.getHierarchy()) {
          for (Dependency dependency : configuration.getDependencies()) {
            if (dependency instanceof ProjectDependency) {
              // If this is a project dependency, save it
              ProjectDependency projectDependency = (ProjectDependency) dependency;
              if (!projectDependencies.contains(projectDependency)) {
                projects.push(projectDependency.getDependencyProject());
                projectDependencies.add(projectDependency);
              }
            }
          }
        }
      }
    }
    return projectDependencies;
  }
}
