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
import com.google.common.base.Preconditions;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.file.FileCollection;
import org.gradle.api.initialization.Settings;
import org.gradle.api.provider.Provider;
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

  /**
   * Build files and source directories of the project and its project dependencies. Collected at
   * configuration time, as the {@link Project} model must not be accessed at execution time.
   */
  private static class ProjectFiles {

    private final List<File> buildFiles = new ArrayList<>();
    private final List<File> sourceDirectories = new ArrayList<>();
    private final List<File> dependencySourceDirectories = new ArrayList<>();
    private final Set<File> projectDependencyJars = new HashSet<>();
  }

  @Nullable private JibExtension jibExtension;
  @Nullable private FileCollection configurationFiles;
  @Nullable private Provider<ProjectFiles> projectFiles;

  /**
   * Sets the task's {@link JibExtension} and captures the project state needed at execution time.
   *
   * @param jibExtension the {@link JibExtension}
   * @return this
   */
  public FilesTaskV2 setJibExtension(JibExtension jibExtension) {
    this.jibExtension = jibExtension;
    Project project = getProject();
    configurationFiles =
        project.files(
            (Callable<FileCollection>)
                () ->
                    project
                        .getConfigurations()
                        .getByName(jibExtension.getConfigurationName().get()));
    projectFiles =
        project
            .getProviders()
            .provider(
                () -> collectProjectFiles(project, jibExtension.getConfigurationName().get()));
    return this;
  }

  /**
   * Task Action, print files.
   *
   * @throws IOException if an error occurs generating the json string
   */
  @TaskAction
  public void listFiles() throws IOException {
    Preconditions.checkNotNull(jibExtension);
    ProjectFiles files = Preconditions.checkNotNull(projectFiles).get();
    SkaffoldFilesOutput skaffoldFilesOutput = new SkaffoldFilesOutput();

    // Add build config, settings, etc. of the project, its root project and project dependencies
    files.buildFiles.forEach(file -> skaffoldFilesOutput.addBuild(file.toPath()));

    // Add sources + resources
    files.sourceDirectories.stream()
        .filter(File::exists)
        .forEach(directory -> skaffoldFilesOutput.addInput(directory.toPath()));

    // Add extra layer
    List<Path> extraDirectories =
        jibExtension.getExtraDirectories().getPaths().stream()
            .map(ExtraDirectoryParameters::getFrom)
            .collect(Collectors.toList());
    extraDirectories.stream().filter(Files::exists).forEach(skaffoldFilesOutput::addInput);

    // Add sources + resources of project dependencies
    files.dependencySourceDirectories.stream()
        .filter(File::exists)
        .forEach(directory -> skaffoldFilesOutput.addInput(directory.toPath()));

    // Add SNAPSHOT, non-project dependency jars
    Set<File> projectDependencyJars = new HashSet<>(files.projectDependencyJars);
    for (File file : Preconditions.checkNotNull(configurationFiles)) {
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
   * Collects the build files and source directories of a project and its project dependencies.
   *
   * @param project the project
   * @param configurationName the name of the configuration to search for project dependencies
   * @return the collected files
   */
  private ProjectFiles collectProjectFiles(Project project, String configurationName) {
    ProjectFiles files = new ProjectFiles();

    // If this is not the root project, add the root project's build.gradle and settings.gradle
    if (project != project.getRootProject()) {
      addGradleFiles(project.getRootProject(), files.buildFiles);
    }

    addProjectFiles(project, files.buildFiles, files.sourceDirectories);

    // Find project dependencies
    Set<ProjectDependency> projectDependencies =
        findProjectDependencies(project, configurationName);
    for (ProjectDependency projectDependency : projectDependencies) {
      Project dependentProject = getDependentProject(project, projectDependency);
      addProjectFiles(dependentProject, files.buildFiles, files.dependencySourceDirectories);

      // Keep track of project dependency jars for filtering out later
      String targetConfigurationName = projectDependency.getTargetConfiguration();
      if (targetConfigurationName == null) {
        targetConfigurationName = "default";
      }
      for (Configuration targetConfiguration :
          dependentProject.getConfigurations().getByName(targetConfigurationName).getHierarchy()) {
        for (PublishArtifact artifact : targetConfiguration.getArtifacts()) {
          files.projectDependencyJars.add(artifact.getFile());
        }
      }
    }
    return files;
  }

  /**
   * Adds the locations of a project's build.gradle, settings.gradle, and gradle.properties.
   *
   * @param project the project
   * @param buildFiles the list to add the files to
   */
  private static void addGradleFiles(Project project, List<File> buildFiles) {
    Path projectPath = project.getProjectDir().toPath();

    // Add build.gradle
    buildFiles.add(project.getBuildFile());

    // Add settings.gradle
    addSettingsFile(project, projectPath, buildFiles);

    // Add gradle.properties
    if (Files.exists(projectPath.resolve("gradle.properties"))) {
      buildFiles.add(projectPath.resolve("gradle.properties").toFile());
    }
  }

  /**
   * Adds the settings.gradle file for a project.
   *
   * <p>Uses reflection to call getSettingsFile() for compatibility with both Gradle 6 and 9
   * (getSettingsFile() was removed in Gradle 9).
   *
   * @param project the project
   * @param projectPath the project directory path
   * @param buildFiles the list to add the file to
   */
  private static void addSettingsFile(Project project, Path projectPath, List<File> buildFiles) {
    boolean settingsFileAdded = false;
    try {
      Object startParameter = project.getGradle().getStartParameter();
      java.lang.reflect.Method getSettingsFileMethod =
          startParameter.getClass().getMethod("getSettingsFile");
      File settingsFile = (File) getSettingsFileMethod.invoke(startParameter);
      if (settingsFile != null) {
        buildFiles.add(settingsFile);
        settingsFileAdded = true;
      }
    } catch (ReflectiveOperationException e) {
      // Fall through to default settings file check
    }

    // Fall back to default settings file location if not already added
    if (!settingsFileAdded && Files.exists(projectPath.resolve(Settings.DEFAULT_SETTINGS_FILE))) {
      buildFiles.add(projectPath.resolve(Settings.DEFAULT_SETTINGS_FILE).toFile());
    }
  }

  /**
   * Adds build files, sources, and resources associated with a project.
   *
   * @param project the project
   * @param buildFiles the list to add the build files to
   * @param sourceDirectories the list to add the source and resource directories to
   */
  private static void addProjectFiles(
      Project project, List<File> buildFiles, List<File> sourceDirectories) {
    // Add build config, settings, etc.
    addGradleFiles(project, buildFiles);

    // Add sources + resources
    SourceSetContainer sourceSetContainer =
        project.getExtensions().findByType(SourceSetContainer.class);
    if (sourceSetContainer != null) {
      SourceSet mainSourceSet = sourceSetContainer.findByName(SourceSet.MAIN_SOURCE_SET_NAME);
      if (mainSourceSet != null) {
        sourceDirectories.addAll(mainSourceSet.getAllSource().getSourceDirectories().getFiles());
      }
    }
  }

  /**
   * Collects a project's project dependencies, including all transitive project dependencies.
   *
   * @param project the project to find the project dependencies for
   * @param configurationName the name of the configuration to search for project dependencies
   * @return the set of project dependencies
   */
  private static Set<ProjectDependency> findProjectDependencies(
      Project project, String configurationName) {
    Set<ProjectDependency> projectDependencies = new HashSet<>();
    Deque<Project> projects = new ArrayDeque<>();
    projects.push(project);

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
                projects.push(getDependentProject(project, projectDependency));
                projectDependencies.add(projectDependency);
              }
            }
          }
        }
      }
    }

    return projectDependencies;
  }

  /**
   * Resolves a {@link ProjectDependency} to its corresponding {@link Project} instance.
   *
   * <p>Uses reflection to handle both Gradle 6 (getDependencyProject()) and Gradle 9+ (getPath()).
   *
   * @param project any project of the build, used to look up the dependent project by path
   * @param projectDependency the project dependency to resolve
   * @return the resolved project
   * @throws RuntimeException if the dependent project could not be resolved
   */
  private static Project getDependentProject(Project project, ProjectDependency projectDependency) {
    // Try getDependencyProject() first (Gradle 6-8)
    try {
      java.lang.reflect.Method getDependencyProjectMethod =
          projectDependency.getClass().getMethod("getDependencyProject");
      return (Project) getDependencyProjectMethod.invoke(projectDependency);
    } catch (NoSuchMethodException e) {
      // Fall through to getPath() approach (Gradle 9+)
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(
          "Failed to resolve dependent project from " + projectDependency, e);
    }

    // Try getPath() approach (Gradle 9+)
    try {
      java.lang.reflect.Method getPathMethod = projectDependency.getClass().getMethod("getPath");
      String path = (String) getPathMethod.invoke(projectDependency);
      return project.project(path);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(
          "Failed to resolve dependent project from " + projectDependency, e);
    }
  }
}
