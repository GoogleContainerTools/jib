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

import com.google.cloud.tools.crepecake.builder.SourceFilesConfiguration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Resource;
import org.apache.maven.project.MavenProject;

class MavenSourceFilesConfiguration implements SourceFilesConfiguration {

  private final Set<Path> dependenciesFiles = new HashSet<>();
  private final Set<Path> resourcesFiles = new HashSet<>();
  private final Set<Path> classesFiles = new HashSet<>();

  MavenSourceFilesConfiguration(MavenProject project) throws IOException {
    Path classesOutputDir = Paths.get(project.getBuild().getOutputDirectory());

    // TODO: Fix null pointer exception possibility for getSystemPath.
    for (Artifact artifact : project.getArtifacts()) {
      dependenciesFiles.add(artifact.getFile().toPath());
    }

    for (Resource resource : project.getResources()) {
      Path resourceSourceDir = Paths.get(resource.getDirectory());
      Files.list(resourceSourceDir)
          .forEach(
              resourceSourceDirFIle -> {
                Path correspondingOutputDirFile =
                    classesOutputDir.resolve(resourceSourceDir.relativize(resourceSourceDirFIle));
                if (Files.exists(correspondingOutputDirFile)) {
                  resourcesFiles.add(correspondingOutputDirFile);
                }
              });
    }

    Path classesSourceDir = Paths.get(project.getBuild().getSourceDirectory());

    Files.list(classesSourceDir)
        .forEach(
            classesSourceDirFile -> {
              Path correspondingOutputDirFile =
                  classesOutputDir.resolve(classesSourceDir.relativize(classesSourceDirFile));
              if (Files.exists(correspondingOutputDirFile)) {
                classesFiles.add(correspondingOutputDirFile);
              }
            });

    // TODO: Check if there are still unaccounted-for files in the runtime classpath.
  }

  @Override
  public Set<Path> getDependenciesFiles() {
    return dependenciesFiles;
  }

  @Override
  public Set<Path> getResourcesFiles() {
    return resourcesFiles;
  }

  @Override
  public Set<Path> getClassesFiles() {
    return classesFiles;
  }

  @Override
  public Path getDependenciesExtractionPath() {
    return Paths.get("app", "libs");
  }

  @Override
  public Path getResourcesExtractionPath() {
    return Paths.get("app", "resources");
  }

  @Override
  public Path getClassesExtractionPath() {
    return Paths.get("app", "classes");
  }
}
