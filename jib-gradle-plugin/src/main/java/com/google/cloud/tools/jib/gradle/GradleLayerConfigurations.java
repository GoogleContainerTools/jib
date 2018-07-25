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

import com.google.cloud.tools.jib.configuration.LayerConfiguration;
import com.google.cloud.tools.jib.frontend.JavaEntrypointConstructor;
import com.google.cloud.tools.jib.image.LayerEntry;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;

/** Builds {@link LayerConfiguration}s based on inputs from a {@link Project}. */
class GradleLayerConfigurations {

  /** Name of the `main` {@link SourceSet} to use as source files. */
  private static final String MAIN_SOURCE_SET_NAME = "main";

  private static final String DEPENDENCIES_LAYER_LABEL = "dependencies";
  private static final String SNAPSHOT_DEPENDENCIES_LAYER_LABEL = "snapshot dependencies";
  private static final String RESOURCES_LAYER_LABEL = "resources";
  private static final String CLASSES_LAYER_LABEL = "classes";
  private static final String EXTRA_FILES_LAYER_LABEL = "extra files";

  /**
   * Resolves the source files configuration for a Gradle {@link Project}.
   *
   * @param project the Gradle {@link Project}
   * @param gradleBuildLogger the build logger for providing feedback about the resolution
   * @param extraDirectory path to the directory for the extra files layer
   * @return a {@link GradleLayerConfigurations} for building the layers for the Gradle {@link
   *     Project}
   * @throws IOException if an I/O exception occurred during resolution
   */
  static GradleLayerConfigurations getForProject(
      Project project, GradleBuildLogger gradleBuildLogger, Path extraDirectory)
      throws IOException {
    JavaPluginConvention javaPluginConvention =
        project.getConvention().getPlugin(JavaPluginConvention.class);

    SourceSet mainSourceSet = javaPluginConvention.getSourceSets().getByName(MAIN_SOURCE_SET_NAME);

    List<Path> dependenciesFiles = new ArrayList<>();
    List<Path> snapshotDependenciesFiles = new ArrayList<>();
    List<Path> resourcesFiles = new ArrayList<>();
    List<Path> classesFiles = new ArrayList<>();
    List<Path> extraFiles = new ArrayList<>();

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

    // Adds all the extra files.
    if (Files.exists(extraDirectory)) {
      try (Stream<Path> extraFilesLayerDirectoryFiles = Files.list(extraDirectory)) {
        extraFiles = extraFilesLayerDirectoryFiles.collect(Collectors.toList());
      }
    }

    // Sorts all files by path for consistent ordering.
    Collections.sort(dependenciesFiles);
    Collections.sort(snapshotDependenciesFiles);
    Collections.sort(resourcesFiles);
    Collections.sort(classesFiles);
    Collections.sort(extraFiles);

    return new GradleLayerConfigurations(
        LayerConfiguration.builder()
            .addEntry(
                dependenciesFiles, JavaEntrypointConstructor.DEFAULT_DEPENDENCIES_PATH_ON_IMAGE)
            .setLabel(DEPENDENCIES_LAYER_LABEL)
            .build(),
        LayerConfiguration.builder()
            .addEntry(
                snapshotDependenciesFiles,
                JavaEntrypointConstructor.DEFAULT_DEPENDENCIES_PATH_ON_IMAGE)
            .setLabel(SNAPSHOT_DEPENDENCIES_LAYER_LABEL)
            .build(),
        LayerConfiguration.builder()
            .addEntry(resourcesFiles, JavaEntrypointConstructor.DEFAULT_RESOURCES_PATH_ON_IMAGE)
            .setLabel(RESOURCES_LAYER_LABEL)
            .build(),
        LayerConfiguration.builder()
            .addEntry(classesFiles, JavaEntrypointConstructor.DEFAULT_CLASSES_PATH_ON_IMAGE)
            .setLabel(CLASSES_LAYER_LABEL)
            .build(),
        LayerConfiguration.builder()
            .addEntry(extraFiles, "/")
            .setLabel(EXTRA_FILES_LAYER_LABEL)
            .build());
  }

  private final LayerConfiguration dependenciesLayerConfiguration;
  private final LayerConfiguration snapshotDependenciesLayerConfiguration;
  private final LayerConfiguration resourcesLayerConfiguration;
  private final LayerConfiguration classesLayerConfiguration;
  private final LayerConfiguration extraFilesLayerConfiguration;

  // TODO: Consolidate with MavenLayerConfigurations.
  /** Instantiate with {@link #getForProject}. */
  private GradleLayerConfigurations(
      LayerConfiguration dependenciesLayerConfiguration,
      LayerConfiguration snapshotDependenciesLayerConfiguration,
      LayerConfiguration resourcesLayerConfiguration,
      LayerConfiguration classesLayerConfiguration,
      LayerConfiguration extraFilesLayerConfiguration) {
    this.dependenciesLayerConfiguration = dependenciesLayerConfiguration;
    this.snapshotDependenciesLayerConfiguration = snapshotDependenciesLayerConfiguration;
    this.resourcesLayerConfiguration = resourcesLayerConfiguration;
    this.classesLayerConfiguration = classesLayerConfiguration;
    this.extraFilesLayerConfiguration = extraFilesLayerConfiguration;
  }

  ImmutableList<LayerConfiguration> getLayerConfigurations() {
    return ImmutableList.of(
        dependenciesLayerConfiguration,
        snapshotDependenciesLayerConfiguration,
        resourcesLayerConfiguration,
        classesLayerConfiguration,
        extraFilesLayerConfiguration);
  }

  LayerEntry getDependenciesLayerEntry() {
    return dependenciesLayerConfiguration.getLayerEntries().get(0);
  }

  LayerEntry getSnapshotDependenciesLayerEntry() {
    return snapshotDependenciesLayerConfiguration.getLayerEntries().get(0);
  }

  LayerEntry getResourcesLayerEntry() {
    return resourcesLayerConfiguration.getLayerEntries().get(0);
  }

  LayerEntry getClassesLayerEntry() {
    return classesLayerConfiguration.getLayerEntries().get(0);
  }

  LayerEntry getExtraFilesLayerEntry() {
    return extraFilesLayerConfiguration.getLayerEntries().get(0);
  }
}
