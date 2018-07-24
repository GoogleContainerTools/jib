/*
 * Copyright 2018 Google LLC. All rights reserved.
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

package com.google.cloud.tools.jib.maven;

import com.google.cloud.tools.jib.configuration.LayerConfiguration;
import com.google.cloud.tools.jib.frontend.JavaEntrypointConstructor;
import com.google.cloud.tools.jib.image.LayerEntry;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.project.MavenProject;

/** Builds {@link LayerConfiguration}s based on inputs from a {@link MavenProject}. */
class MavenLayerConfigurations {

  private static final String DEPENDENCIES_LAYER_LABEL = "dependencies";
  private static final String SNAPSHOT_DEPENDENCIES_LAYER_LABEL = "snapshot dependencies";
  private static final String RESOURCES_LAYER_LABEL = "resources";
  private static final String CLASSES_LAYER_LABEL = "classes";
  private static final String EXTRA_FILES_LAYER_LABEL = "extra files";

  /**
   * Resolves the source files configuration for a {@link MavenProject}.
   *
   * @param project the {@link MavenProject}
   * @param extraDirectory path to the directory for the extra files layer
   * @return a new {@link MavenLayerConfigurations} for the project
   * @throws IOException if collecting the project files fails
   */
  static MavenLayerConfigurations getForProject(MavenProject project, Path extraDirectory)
      throws IOException {
    Path classesSourceDirectory = Paths.get(project.getBuild().getSourceDirectory());
    Path classesOutputDirectory = Paths.get(project.getBuild().getOutputDirectory());

    List<Path> dependenciesFiles = new ArrayList<>();
    List<Path> snapshotDependenciesFiles = new ArrayList<>();
    List<Path> resourcesFiles = new ArrayList<>();
    List<Path> classesFiles = new ArrayList<>();
    List<Path> extraFiles = new ArrayList<>();

    // Gets all the dependencies.
    for (Artifact artifact : project.getArtifacts()) {
      if (artifact.isSnapshot()) {
        snapshotDependenciesFiles.add(artifact.getFile().toPath());
      } else {
        dependenciesFiles.add(artifact.getFile().toPath());
      }
    }

    // Gets the classes files in the 'classes' output directory. It finds the files that are classes
    // files by matching them against the .java source files. All other files are deemed resources.
    try (Stream<Path> classFileStream = Files.list(classesOutputDirectory)) {
      classFileStream.forEach(
          classFile -> {
            /*
             * Adds classFile to classesFiles if it is a .class file or is a directory that also
             * exists in the classes source directory; otherwise, adds file to resourcesFiles.
             */
            if (Files.isDirectory(classFile)
                && Files.exists(
                    classesSourceDirectory.resolve(classesOutputDirectory.relativize(classFile)))) {
              classesFiles.add(classFile);
              return;
            }

            if (FileSystems.getDefault().getPathMatcher("glob:**.class").matches(classFile)) {
              classesFiles.add(classFile);
              return;
            }

            resourcesFiles.add(classFile);
          });
    }

    // Adds all the extra files.
    if (Files.exists(extraDirectory)) {
      try (Stream<Path> extraFilesLayerDirectoryFiles = Files.list(extraDirectory)) {
        extraFiles = extraFilesLayerDirectoryFiles.collect(Collectors.toList());

      } catch (IOException ex) {
        throw new IOException("Failed to list directory for extra files: " + extraDirectory, ex);
      }
    }

    // Sort all files by path for consistent ordering.
    Collections.sort(dependenciesFiles);
    Collections.sort(snapshotDependenciesFiles);
    Collections.sort(resourcesFiles);
    Collections.sort(classesFiles);
    Collections.sort(extraFiles);

    return new MavenLayerConfigurations(
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

  /** Instantiate with {@link #getForProject}. */
  private MavenLayerConfigurations(
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

  /**
   * Gets the list of {@link LayerConfiguration}s to use to build the container image.
   *
   * @return the list of {@link LayerConfiguration}s
   */
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
