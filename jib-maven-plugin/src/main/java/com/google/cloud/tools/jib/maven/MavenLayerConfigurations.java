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

package com.google.cloud.tools.jib.maven;

import com.google.cloud.tools.jib.frontend.JavaLayerConfigurations;
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

/** Builds {@link JavaLayerConfigurations} based on inputs from a {@link MavenProject}. */
class MavenLayerConfigurations {

  /**
   * Resolves the source files configuration for a {@link MavenProject}.
   *
   * @param project the {@link MavenProject}
   * @param extraDirectory path to the directory for the extra files layer
   * @return a {@link JavaLayerConfigurations} for the project
   * @throws IOException if collecting the project files fails
   */
  static JavaLayerConfigurations getForProject(MavenProject project, Path extraDirectory)
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

    return JavaLayerConfigurations.builder()
        .setDependencyFiles(dependenciesFiles)
        .setSnapshotDependencyFiles(snapshotDependenciesFiles)
        .setResourceFiles(resourcesFiles)
        .setClassFiles(classesFiles)
        .setExtraFiles(extraFiles)
        .build();
  }

  private MavenLayerConfigurations() {}
}
