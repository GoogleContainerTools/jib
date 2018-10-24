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

import com.google.cloud.tools.jib.filesystem.AbsoluteUnixPath;
import com.google.cloud.tools.jib.frontend.JavaEntrypointConstructor;
import com.google.cloud.tools.jib.frontend.JavaLayerConfigurations;
import com.google.cloud.tools.jib.frontend.JavaLayerConfigurations.Builder;
import com.google.cloud.tools.jib.frontend.JavaLayerConfigurations.LayerType;
import com.google.cloud.tools.jib.plugins.common.JavaLayerConfigurationsHelper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.function.Predicate;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.project.MavenProject;

/** Builds {@link JavaLayerConfigurations} based on inputs from a {@link MavenProject}. */
class MavenLayerConfigurations {

  /**
   * Resolves the {@link JavaLayerConfigurations} for a {@link MavenProject}.
   *
   * @param project the {@link MavenProject}
   * @param extraDirectory path to the directory for the extra files layer
   * @param appRoot root directory in the image where the app will be placed
   * @return a {@link JavaLayerConfigurations} for the project
   * @throws IOException if collecting the project files fails
   */
  static JavaLayerConfigurations getForProject(
      MavenProject project, Path extraDirectory, AbsoluteUnixPath appRoot) throws IOException {
    if ("war".equals(project.getPackaging())) {
      return getForWarProject(project, extraDirectory, appRoot);
    } else {
      return getForNonWarProject(project, extraDirectory, appRoot);
    }
  }

  /**
   * Resolves the {@link JavaLayerConfigurations} for a non-WAR {@link MavenProject}.
   *
   * @param project the {@link MavenProject}
   * @param extraDirectory path to the directory for the extra files layer
   * @param appRoot root directory in the image where the app will be placed
   * @return a {@link JavaLayerConfigurations} for the project
   * @throws IOException if collecting the project files fails
   */
  private static JavaLayerConfigurations getForNonWarProject(
      MavenProject project, Path extraDirectory, AbsoluteUnixPath appRoot) throws IOException {

    AbsoluteUnixPath dependenciesExtractionPath =
        appRoot.resolve(JavaEntrypointConstructor.DEFAULT_RELATIVE_DEPENDENCIES_PATH_ON_IMAGE);
    AbsoluteUnixPath resourcesExtractionPath =
        appRoot.resolve(JavaEntrypointConstructor.DEFAULT_RELATIVE_RESOURCES_PATH_ON_IMAGE);
    AbsoluteUnixPath classesExtractionPath =
        appRoot.resolve(JavaEntrypointConstructor.DEFAULT_RELATIVE_CLASSES_PATH_ON_IMAGE);

    Builder layerBuilder = JavaLayerConfigurations.builder();

    // Gets all the dependencies.
    for (Artifact artifact : project.getArtifacts()) {
      Path artifactPath = artifact.getFile().toPath();
      LayerType layerType =
          artifact.isSnapshot() ? LayerType.SNAPSHOT_DEPENDENCIES : LayerType.DEPENDENCIES;
      layerBuilder.addFile(
          layerType, artifactPath, dependenciesExtractionPath.resolve(artifactPath.getFileName()));
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
          LayerType.EXTRA_FILES, extraDirectory, path -> true, AbsoluteUnixPath.get("/"));
    }

    return layerBuilder.build();
  }

  /**
   * Resolves the {@link JavaLayerConfigurations} for a WAR {@link MavenProject}.
   *
   * @param project the {@link MavenProject}
   * @param extraDirectory path to the directory for the extra files layer
   * @param appRoot root directory in the image where the app will be placed
   * @return a {@link JavaLayerConfigurations} for the project
   * @throws IOException if collecting the project files fails
   */
  private static JavaLayerConfigurations getForWarProject(
      MavenProject project, Path extraDirectory, AbsoluteUnixPath appRoot) throws IOException {

    // TODO explode the WAR file rather than using this directory. The contents of the final WAR may
    // be different from this directory (it's possible to include or exclude files when packaging a
    // WAR). Also the exploded WAR directory is configurable with <webappDirectory> and may not be
    // at build.getFinalName().
    Path explodedWarPath =
        Paths.get(project.getBuild().getDirectory()).resolve(project.getBuild().getFinalName());
    // TODO: Replace Collections.emptyMap() with configured permissions
    return JavaLayerConfigurationsHelper.fromExplodedWar(
        explodedWarPath, appRoot, extraDirectory, Collections.emptyMap());
  }

  private MavenLayerConfigurations() {}
}
