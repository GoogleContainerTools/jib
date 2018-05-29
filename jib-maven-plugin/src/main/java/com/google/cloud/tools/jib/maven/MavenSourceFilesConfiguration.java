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
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.project.MavenProject;

/** {@link SourceFilesConfiguration} implementation based on inputs from a {@link MavenProject}. */
class MavenSourceFilesConfiguration implements SourceFilesConfiguration {

  // TODO: This should be shared with GradleSourceFilesConfiguration.
  private static final String DEPENDENCIES_PATH_ON_IMAGE = "/app/libs/";
  private static final String RESOURCES_PATH_ON_IMAGE = "/app/resources/";
  private static final String CLASSES_PATH_ON_IMAGE = "/app/classes/";

  /** Resolves the source files configuration for a Maven {@link MavenProject}. */
  static MavenSourceFilesConfiguration getForProject(MavenProject project) throws IOException {
    return new MavenSourceFilesConfiguration(project);
  }

  private final List<Path> dependenciesFiles = new ArrayList<>();
  private final List<Path> resourcesFiles = new ArrayList<>();
  private final List<Path> classesFiles = new ArrayList<>();

  /** Instantiate with {@link #getForProject}. */
  private MavenSourceFilesConfiguration(MavenProject project) throws IOException {
    Path classesSourceDirectory = Paths.get(project.getBuild().getSourceDirectory());
    Path classesOutputDirectory = Paths.get(project.getBuild().getOutputDirectory());

    // Gets all the dependencies.
    for (Artifact artifact : project.getArtifacts()) {
      dependenciesFiles.add(artifact.getFile().toPath());
    }

    // Gets the classes files in the 'classes' output directory. It finds the files that are classes
    // files by matching them against the .java source files. All other files are deemed resources.
    try (Stream<Path> classFileStream = Files.list(classesOutputDirectory)) {
      classFileStream.forEach(
          classFile ->
              addFileToResourcesOrClasses(
                  classesSourceDirectory, classesOutputDirectory, classFile));
    }

    // Sort all files by path for consistent ordering.
    Collections.sort(dependenciesFiles);
    Collections.sort(resourcesFiles);
    Collections.sort(classesFiles);
  }

  @Override
  public List<Path> getDependenciesFiles() {
    return dependenciesFiles;
  }

  @Override
  public List<Path> getResourcesFiles() {
    return resourcesFiles;
  }

  @Override
  public List<Path> getClassesFiles() {
    return classesFiles;
  }

  @Override
  public String getDependenciesPathOnImage() {
    return DEPENDENCIES_PATH_ON_IMAGE;
  }

  @Override
  public String getResourcesPathOnImage() {
    return RESOURCES_PATH_ON_IMAGE;
  }

  @Override
  public String getClassesPathOnImage() {
    return CLASSES_PATH_ON_IMAGE;
  }

  /**
   * Adds {@code file} to {@link #classesFiles} if it is a {@code .class} file and there is a
   * corresponding {@code .java} file in the {@code classesSourceDirectory}; otherwise, adds {@code
   * file} to {@link #resourcesFiles}.
   */
  private void addFileToResourcesOrClasses(
      Path classesSourceDirectory, Path classesOutputDirectory, Path file) {
    // Checks if is .class file.
    if (FileSystems.getDefault().getPathMatcher("glob:**.class").matches(file)) {
      // Replaces the extension with '.java'.
      Path javaFile =
          file.resolveSibling(file.getFileName().toString().replaceAll("(.*?)\\.class", "$1.java"));

      // Resolves the file in the source directory.
      Path correspondingSourceDirFile =
          classesSourceDirectory.resolve(classesOutputDirectory.relativize(javaFile));
      if (Files.exists(correspondingSourceDirFile)) {
        // Adds the file as a classes file since it is in the source directory.
        classesFiles.add(file);
        return;
      }
    }

    // Adds the file as a resource since it is not a .class file or is not in the source directory.
    resourcesFiles.add(file);
  }
}
