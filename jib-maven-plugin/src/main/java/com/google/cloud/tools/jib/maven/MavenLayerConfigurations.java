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

import com.google.cloud.tools.jib.configuration.FilePermissions;
import com.google.cloud.tools.jib.filesystem.AbsoluteUnixPath;
import com.google.cloud.tools.jib.frontend.JavaEntrypointConstructor;
import com.google.cloud.tools.jib.frontend.JavaLayerConfigurations;
import com.google.cloud.tools.jib.frontend.JavaLayerConfigurations.Builder;
import com.google.cloud.tools.jib.frontend.JavaLayerConfigurations.LayerType;
import com.google.cloud.tools.jib.plugins.common.JavaLayerConfigurationsHelper;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.project.MavenProject;

/** Builds {@link JavaLayerConfigurations} based on inputs from a {@link MavenProject}. */
class MavenLayerConfigurations {

  /**
   * Resolves the {@link JavaLayerConfigurations} for a {@link MavenProject}.
   *
   * @param project the {@link MavenProject}
   * @param containerizeWar whether to do WAR containerization
   * @param extraDirectory path to the directory for the extra files layer
   * @param extraDirectoryPermissions map from path on container to file permissions for extra-layer
   *     files
   * @param appRoot root directory in the image where the app will be placed
   * @return a {@link JavaLayerConfigurations} for the project
   * @throws IOException if collecting the project files fails
   */
  static JavaLayerConfigurations getForProject(
      MavenProject project,
      boolean containerizeWar,
      Path extraDirectory,
      Map<AbsoluteUnixPath, FilePermissions> extraDirectoryPermissions,
      AbsoluteUnixPath appRoot)
      throws IOException {
    if (containerizeWar) {
      return getForWarProject(project, extraDirectory, extraDirectoryPermissions, appRoot);
    } else {
      return getForNonWarProject(project, extraDirectory, extraDirectoryPermissions, appRoot);
    }
  }

  /**
   * Resolves the {@link JavaLayerConfigurations} for a non-WAR {@link MavenProject}.
   *
   * @param project the {@link MavenProject}
   * @param extraDirectory path to the directory for the extra files layer
   * @param extraDirectoryPermissions map from path on container to file permissions for extra-layer
   *     files
   * @param appRoot root directory in the image where the app will be placed
   * @return a {@link JavaLayerConfigurations} for the project
   * @throws IOException if collecting the project files fails
   */
  private static JavaLayerConfigurations getForNonWarProject(
      MavenProject project,
      Path extraDirectory,
      Map<AbsoluteUnixPath, FilePermissions> extraDirectoryPermissions,
      AbsoluteUnixPath appRoot)
      throws IOException {

    AbsoluteUnixPath dependenciesExtractionPath =
        appRoot.resolve(JavaEntrypointConstructor.DEFAULT_RELATIVE_DEPENDENCIES_PATH_ON_IMAGE);
    AbsoluteUnixPath resourcesExtractionPath =
        appRoot.resolve(JavaEntrypointConstructor.DEFAULT_RELATIVE_RESOURCES_PATH_ON_IMAGE);
    AbsoluteUnixPath classesExtractionPath =
        appRoot.resolve(JavaEntrypointConstructor.DEFAULT_RELATIVE_CLASSES_PATH_ON_IMAGE);

    Builder layerBuilder = JavaLayerConfigurations.builder();

    // Gets all the dependencies.
    List<String> duplicates =
        project
            .getArtifacts()
            .stream()
            .map(Artifact::getFile)
            .map(File::getName)
            .collect(Collectors.groupingBy(filename -> filename, Collectors.counting()))
            .entrySet()
            .stream()
            .filter(entry -> entry.getValue() > 1)
            .map(Entry::getKey)
            .collect(Collectors.toList());
    for (Artifact artifact : project.getArtifacts()) {
      Path artifactPath = artifact.getFile().toPath();
      LayerType layerType =
          artifact.isSnapshot() ? LayerType.SNAPSHOT_DEPENDENCIES : LayerType.DEPENDENCIES;
      String filename = artifactPath.getFileName().toString();
      layerBuilder.addFile(
          layerType,
          artifactPath,
          dependenciesExtractionPath.resolve(
              duplicates.contains(filename)
                  ? filename.replaceFirst("\\.jar$", "-" + Files.size(artifactPath)) + ".jar"
                  : filename));
    }

    Path classesOutputDirectory = Paths.get(project.getBuild().getOutputDirectory());

    // Gets the classes files in the 'classes' output directory.
    Predicate<Path> isClassFile = path -> path.getFileName().toString().endsWith(".class");
    layerBuilder.addDirectoryContents(
        LayerType.CLASSES, classesOutputDirectory, isClassFile, classesExtractionPath);

    // Gets the resources files in the 'classes' output directory.
    layerBuilder.addDirectoryContents(
        LayerType.RESOURCES, classesOutputDirectory, isClassFile.negate(), resourcesExtractionPath);

    // Adds all the extra files.
    if (Files.exists(extraDirectory)) {
      layerBuilder.addDirectoryContents(
          LayerType.EXTRA_FILES,
          extraDirectory,
          path -> true,
          AbsoluteUnixPath.get("/"),
          extraDirectoryPermissions);
    }

    return layerBuilder.build();
  }

  /**
   * Resolves the {@link JavaLayerConfigurations} for a WAR {@link MavenProject}.
   *
   * @param project the {@link MavenProject}
   * @param extraDirectory path to the directory for the extra files layer
   * @param extraDirectoryPermissions map from path on container to file permissions for extra-layer
   *     files
   * @param appRoot root directory in the image where the app will be placed
   * @return a {@link JavaLayerConfigurations} for the project
   * @throws IOException if collecting the project files fails
   */
  private static JavaLayerConfigurations getForWarProject(
      MavenProject project,
      Path extraDirectory,
      Map<AbsoluteUnixPath, FilePermissions> extraDirectoryPermissions,
      AbsoluteUnixPath appRoot)
      throws IOException {

    // TODO explode the WAR file rather than using this directory. The contents of the final WAR may
    // be different from this directory (it's possible to include or exclude files when packaging a
    // WAR). Also the exploded WAR directory is configurable with <webappDirectory> and may not be
    // at build.getFinalName().
    Path explodedWarPath =
        Paths.get(project.getBuild().getDirectory()).resolve(project.getBuild().getFinalName());
    return JavaLayerConfigurationsHelper.fromExplodedWar(
        explodedWarPath, appRoot, extraDirectory, extraDirectoryPermissions);
  }

  private MavenLayerConfigurations() {}
}
