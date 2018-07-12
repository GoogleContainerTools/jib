/*
 * Copyright 2018 Google Inc.
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
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;

/** {@link SourceFilesConfiguration} implementation based on inputs from a {@link Project}. */
class GradleSourceFilesConfiguration implements SourceFilesConfiguration {

  /** Name of the `main` {@link SourceSet} to use as source files. */
  private static final String MAIN_SOURCE_SET_NAME = "main";

  /** Resolves the source files configuration for a Gradle {@link Project}. */
  static GradleSourceFilesConfiguration getForProject(
      Project project, GradleBuildLogger gradleBuildLogger) throws IOException {
    return new GradleSourceFilesConfiguration(project, gradleBuildLogger);
  }

  private final ImmutableList<Path> dependenciesFiles;
  private final ImmutableList<Path> snapshotDependenciesFiles;
  private final ImmutableList<Path> resourcesFiles;
  private final ImmutableList<Path> classesFiles;

  /** Instantiate with {@link #getForProject}. */
  private GradleSourceFilesConfiguration(Project project, GradleBuildLogger gradleBuildLogger)
      throws IOException {
    JavaPluginConvention javaPluginConvention =
        project.getConvention().getPlugin(JavaPluginConvention.class);

    SourceSet mainSourceSet = javaPluginConvention.getSourceSets().getByName(MAIN_SOURCE_SET_NAME);

    List<Path> dependenciesFiles = new ArrayList<>();
    List<Path> snapshotDependenciesFiles = new ArrayList<>();
    List<Path> resourcesFiles = new ArrayList<>();
    List<Path> classesFiles = new ArrayList<>();

    // Adds each file in each classes output directory to the classes files list.
    FileCollection classesOutputDirectories = mainSourceSet.getOutput().getClassesDirs();
    for (File classesOutputDirectory : classesOutputDirectories) {
      if (Files.notExists(classesOutputDirectory.toPath())) {
        // Warns that output directory was not found.
        gradleBuildLogger.warn(
            "Could not find build output directory '" + classesOutputDirectory + "'");
        continue;
      }
      try (Stream<Path> classFileStream = Files.list(classesOutputDirectory.toPath())) {
        classFileStream.forEach(classesFiles::add);
      }
    }
    if (classesFiles.isEmpty()) {
      gradleBuildLogger.warn("No classes files were found - did you compile your project?");
    }

    // Adds each file in the resources output directory to the resources files list.
    Path resourcesOutputDirectory = mainSourceSet.getOutput().getResourcesDir().toPath();
    if (Files.exists(resourcesOutputDirectory)) {
      try (Stream<Path> resourceFileStream = Files.list(resourcesOutputDirectory)) {
        resourceFileStream.forEach(resourcesFiles::add);
      }
    }

    // Adds all other files to the dependencies files list.
    FileCollection allFiles = mainSourceSet.getRuntimeClasspath();
    // Removes the classes output directories.
    allFiles = allFiles.minus(classesOutputDirectories);
    for (File dependencyFile : allFiles) {
      // Removes the resources output directory.
      if (resourcesOutputDirectory.equals(dependencyFile.toPath())) {
        continue;
      }
      if (dependencyFile.getName().contains("SNAPSHOT")) {
        snapshotDependenciesFiles.add(dependencyFile.toPath());
      } else {
        dependenciesFiles.add(dependencyFile.toPath());
      }
    }

    // Sorts all files by path for consistent ordering.
    this.dependenciesFiles = ImmutableList.sortedCopyOf(dependenciesFiles);
    this.snapshotDependenciesFiles = ImmutableList.sortedCopyOf(snapshotDependenciesFiles);
    this.resourcesFiles = ImmutableList.sortedCopyOf(resourcesFiles);
    this.classesFiles = ImmutableList.sortedCopyOf(classesFiles);
  }

  @Override
  public ImmutableList<Path> getDependenciesFiles() {
    return dependenciesFiles;
  }

  @Override
  public ImmutableList<Path> getSnapshotDependenciesFiles() {
    return snapshotDependenciesFiles;
  }

  @Override
  public ImmutableList<Path> getResourcesFiles() {
    return resourcesFiles;
  }

  @Override
  public ImmutableList<Path> getClassesFiles() {
    return classesFiles;
  }

  @Override
  public String getDependenciesPathOnImage() {
    return DEFAULT_DEPENDENCIES_PATH_ON_IMAGE;
  }

  @Override
  public String getSnapshotDependenciesPathOnImage() {
    return DEFAULT_SNAPSHOT_DEPENDENCIES_PATH_ON_IMAGE;
  }

  @Override
  public String getResourcesPathOnImage() {
    return DEFAULT_RESOURCES_PATH_ON_IMAGE;
  }

  @Override
  public String getClassesPathOnImage() {
    return DEFAULT_CLASSES_PATH_ON_IMAGE;
  }
}
