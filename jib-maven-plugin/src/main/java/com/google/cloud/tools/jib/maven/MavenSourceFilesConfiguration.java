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

package com.google.cloud.tools.jib.maven;

import com.google.cloud.tools.jib.builder.SourceFilesConfiguration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.project.MavenProject;

class MavenSourceFilesConfiguration implements SourceFilesConfiguration {

  private final List<Path> dependenciesFiles = new ArrayList<>();
  private final List<Path> resourcesFiles = new ArrayList<>();
  private final List<Path> classesFiles = new ArrayList<>();

  MavenSourceFilesConfiguration(MavenProject project) throws IOException {
    Path classesOutputDir = Paths.get(project.getBuild().getOutputDirectory());

    // Gets all the dependencies in path-sorted order.
    for (Artifact artifact : project.getArtifacts()) {
      dependenciesFiles.add(artifact.getFile().toPath());
    }
    Collections.sort(dependenciesFiles);

    Path classesSourceDir = Paths.get(project.getBuild().getSourceDirectory());

    // Gets the classes files in the 'classes' output directory. It finds the files that are classes
    // files by matching them against the .java source files.
    // TODO: Make this actually match against the .java source files.
    Files.list(classesOutputDir)
        .forEach(
            classesOutputDirFile -> {
              Path correspondingSourceDirFile =
                  classesSourceDir.resolve(classesOutputDir.relativize(classesOutputDirFile));
              if (Files.exists(correspondingSourceDirFile)) {
                classesFiles.add(classesOutputDirFile);
              } else {
                // Adds the file as a resource since it is not a class file.
                resourcesFiles.add(classesOutputDirFile);
              }
            });
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
  public Path getDependenciesPathOnImage() {
    return Paths.get("app", "libs");
  }

  @Override
  public Path getResourcesPathOnImage() {
    return Paths.get("app", "resources");
  }

  @Override
  public Path getClassesPathOnImage() {
    return Paths.get("app", "classes");
  }
}
