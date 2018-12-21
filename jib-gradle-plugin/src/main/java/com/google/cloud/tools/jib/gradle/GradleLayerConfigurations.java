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

package com.google.cloud.tools.jib.gradle;

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
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.logging.Logger;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;

/** Builds {@link JavaLayerConfigurations} based on inputs from a {@link Project}. */
class GradleLayerConfigurations {

  /** Name of the `main` {@link SourceSet} to use as source files. */
  private static final String MAIN_SOURCE_SET_NAME = "main";

  /**
   * Resolves the {@link JavaLayerConfigurations} for a Gradle {@link Project}.
   *
   * @param project the Gradle {@link Project}
   * @param containerizeWar whether to do WAR containerization
   * @param logger the logger for providing feedback about the resolution
   * @param extraDirectory path to the source directory for the extra files layer
   * @param extraDirectoryPermissions map from path on container to file permissions for extra-layer
   *     files
   * @param appRoot root directory in the image where the app will be placed
   * @return {@link JavaLayerConfigurations} for the layers for the Gradle {@link Project}
   * @throws IOException if an I/O exception occurred during resolution
   */
  static JavaLayerConfigurations getForProject(
      Project project,
      boolean containerizeWar,
      Logger logger,
      Path extraDirectory,
      Map<AbsoluteUnixPath, FilePermissions> extraDirectoryPermissions,
      AbsoluteUnixPath appRoot)
      throws IOException {
    if (containerizeWar) {
      logger.info("WAR project identified, creating WAR image: " + project.getDisplayName());
      return getForWarProject(project, extraDirectory, extraDirectoryPermissions, appRoot);
    } else {
      return getForNonWarProject(
          project, logger, extraDirectory, extraDirectoryPermissions, appRoot);
    }
  }

  /**
   * Resolves the {@link JavaLayerConfigurations} for a non-WAR Gradle {@link Project}.
   *
   * @param project the Gradle {@link Project}
   * @param logger the logger for providing feedback about the resolution
   * @param extraDirectory path to the source directory for the extra files layer
   * @param extraDirectoryPermissions map from path on container to file permissions for extra-layer
   *     files
   * @param appRoot root directory in the image where the app will be placed
   * @return {@link JavaLayerConfigurations} for the layers for the Gradle {@link Project}
   * @throws IOException if an I/O exception occurred during resolution
   */
  private static JavaLayerConfigurations getForNonWarProject(
      Project project,
      Logger logger,
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

    JavaPluginConvention javaPluginConvention =
        project.getConvention().getPlugin(JavaPluginConvention.class);
    SourceSet mainSourceSet = javaPluginConvention.getSourceSets().getByName(MAIN_SOURCE_SET_NAME);

    FileCollection classesOutputDirectories = mainSourceSet.getOutput().getClassesDirs();
    Path resourcesOutputDirectory = mainSourceSet.getOutput().getResourcesDir().toPath();
    FileCollection allFiles = mainSourceSet.getRuntimeClasspath();
    FileCollection dependencyFiles =
        allFiles
            .minus(classesOutputDirectories)
            .filter(file -> !file.toPath().equals(resourcesOutputDirectory));

    // Adds class files.
    logger.info("Adding corresponding output directories of source sets to image");
    for (File classesOutputDirectory : classesOutputDirectories) {
      if (Files.notExists(classesOutputDirectory.toPath())) {
        logger.info("\t'" + classesOutputDirectory + "' (not found, skipped)");
        continue;
      }
      logger.info("\t'" + classesOutputDirectory + "'");
      layerBuilder.addDirectoryContents(
          LayerType.CLASSES, classesOutputDirectory.toPath(), path -> true, classesExtractionPath);
    }
    if (classesOutputDirectories.filter(File::exists).isEmpty()) {
      logger.warn("No classes files were found - did you compile your project?");
    }

    // Adds resource files.
    if (Files.exists(resourcesOutputDirectory)) {
      layerBuilder.addDirectoryContents(
          LayerType.RESOURCES, resourcesOutputDirectory, path -> true, resourcesExtractionPath);
    }

    // Adds dependency files.
    List<String> duplicates =
        dependencyFiles
            .getFiles()
            .stream()
            .map(File::getName)
            .collect(Collectors.groupingBy(filename -> filename, Collectors.counting()))
            .entrySet()
            .stream()
            .filter(entry -> entry.getValue() > 1)
            .map(Entry::getKey)
            .collect(Collectors.toList());
    for (File dependencyFile : dependencyFiles) {
      if (dependencyFile.exists()) {
        boolean isSnapshot = dependencyFile.getName().contains("SNAPSHOT");
        LayerType layerType = isSnapshot ? LayerType.SNAPSHOT_DEPENDENCIES : LayerType.DEPENDENCIES;
        layerBuilder.addFile(
            layerType,
            dependencyFile.toPath(),
            dependenciesExtractionPath.resolve(
                duplicates.contains(dependencyFile.getName())
                    ? dependencyFile
                            .getName()
                            .replaceFirst("\\.jar$", "-" + Files.size(dependencyFile.toPath()))
                        + ".jar"
                    : dependencyFile.getName()));
      } else {
        logger.info("\t'" + dependencyFile + "' (not found, skipped)");
      }
    }

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
   * Resolves the {@link JavaLayerConfigurations} for a WAR Gradle {@link Project}.
   *
   * @param project the Gradle {@link Project}
   * @param extraDirectory path to the source directory for the extra files layer
   * @param extraDirectoryPermissions map from path on container to file permissions for extra-layer
   *     files
   * @param appRoot root directory in the image where the app will be placed
   * @return {@link JavaLayerConfigurations} for the layers for the Gradle {@link Project}
   * @throws IOException if an I/O exception occurred during resolution
   */
  private static JavaLayerConfigurations getForWarProject(
      Project project,
      Path extraDirectory,
      Map<AbsoluteUnixPath, FilePermissions> extraDirectoryPermissions,
      AbsoluteUnixPath appRoot)
      throws IOException {
    Path explodedWarPath = GradleProjectProperties.getExplodedWarDirectory(project);
    return JavaLayerConfigurationsHelper.fromExplodedWar(
        explodedWarPath, appRoot, extraDirectory, extraDirectoryPermissions);
  }

  private GradleLayerConfigurations() {}
}
