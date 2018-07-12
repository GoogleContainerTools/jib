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

import com.google.cloud.tools.jib.builder.SourceFilesConfiguration;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.project.MavenProject;

/** {@link SourceFilesConfiguration} implementation based on inputs from a {@link MavenProject}. */
class MavenSourceFilesConfiguration implements SourceFilesConfiguration {

  /**
   * Resolves the source files configuration for a Maven {@link MavenProject}.
   *
   * @param project the {@link MavenProject}
   * @return a new {@link MavenSourceFilesConfiguration} for the project
   * @throws IOException if collecting the project files fails
   */
  static MavenSourceFilesConfiguration getForProject(MavenProject project) throws IOException {
    return new MavenSourceFilesConfiguration(project);
  }

  private final ImmutableList<Path> dependenciesFiles;
  private final ImmutableList<Path> snapshotDependenciesFiles;
  private final ImmutableList<Path> resourcesFiles;
  private final ImmutableList<Path> classesFiles;

  /** Instantiate with {@link #getForProject}. */
  private MavenSourceFilesConfiguration(MavenProject project) throws IOException {
    Path classesSourceDirectory = Paths.get(project.getBuild().getSourceDirectory());
    Path classesOutputDirectory = Paths.get(project.getBuild().getOutputDirectory());

    List<Path> dependenciesFiles = new ArrayList<>();
    List<Path> snapshotDependenciesFiles = new ArrayList<>();
    List<Path> resourcesFiles = new ArrayList<>();
    List<Path> classesFiles = new ArrayList<>();

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

    // Sort all files by path for consistent ordering.
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
