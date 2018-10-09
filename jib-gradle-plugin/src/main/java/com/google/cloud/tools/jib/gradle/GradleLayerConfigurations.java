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

import com.google.cloud.tools.jib.filesystem.AbsoluteUnixPath;
import com.google.cloud.tools.jib.frontend.JavaEntrypointConstructor;
import com.google.cloud.tools.jib.frontend.JavaLayerConfigurations;
import com.google.cloud.tools.jib.frontend.JavaLayerConfigurations.Builder;
import com.google.cloud.tools.jib.plugins.common.JavaLayerConfigurationsHelper;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
   * @param logger the logger for providing feedback about the resolution
   * @param extraDirectory path to the source directory for the extra files layer
   * @param appRoot root directory in the image where the app will be placed
   * @return {@link JavaLayerConfigurations} for the layers for the Gradle {@link Project}
   * @throws IOException if an I/O exception occurred during resolution
   */
  static JavaLayerConfigurations getForProject(
      Project project, Logger logger, Path extraDirectory, AbsoluteUnixPath appRoot)
      throws IOException {
    if (GradleProjectProperties.getWarTask(project) != null) {
      logger.info("WAR project identified, creating WAR image: " + project.getDisplayName());
      return getForWarProject(project, logger, extraDirectory, appRoot);
    } else {
      return getForNonWarProject(project, logger, extraDirectory, appRoot);
    }
  }

  /**
   * Resolves the {@link JavaLayerConfigurations} for a non-WAR Gradle {@link Project}.
   *
   * @param project the Gradle {@link Project}
   * @param logger the logger for providing feedback about the resolution
   * @param extraDirectory path to the source directory for the extra files layer
   * @param appRoot root directory in the image where the app will be placed
   * @return {@link JavaLayerConfigurations} for the layers for the Gradle {@link Project}
   * @throws IOException if an I/O exception occurred during resolution
   */
  private static JavaLayerConfigurations getForNonWarProject(
      Project project, Logger logger, Path extraDirectory, AbsoluteUnixPath appRoot)
      throws IOException {
    Builder layerBuilder = JavaLayerConfigurations.builder();

    JavaPluginConvention javaPluginConvention =
        project.getConvention().getPlugin(JavaPluginConvention.class);
    SourceSet mainSourceSet = javaPluginConvention.getSourceSets().getByName(MAIN_SOURCE_SET_NAME);

    FileCollection allFiles = mainSourceSet.getRuntimeClasspath();
    FileCollection classesDirectories = mainSourceSet.getOutput().getClassesDirs();
    Path resourcesOutputDirectory = mainSourceSet.getOutput().getResourcesDir().toPath();

    // Adds class files.
    logger.info("Adding corresponding output directories of source sets to image");
    classesDirectories
        .filter(file -> !file.exists())
        .forEach(file -> logger.info("\t'" + file + "' (not found, skipped)"));

    FileCollection existingClassesDirectories = classesDirectories.filter(File::exists);
    for (File classesOutputDirectory : existingClassesDirectories) {
      layerBuilder.addFilesRoot(
          JavaLayerConfigurations.LayerType.CLASSES,
          classesOutputDirectory.toPath(),
          path -> true,
          appRoot.resolve(JavaEntrypointConstructor.DEFAULT_RELATIVE_CLASSES_PATH_ON_IMAGE));
    }
    if (existingClassesDirectories.isEmpty()) {
      logger.warn("No classes files were found - did you compile your project?");
    }

    if (Files.exists(resourcesOutputDirectory)) {
      layerBuilder.addFilesRoot(
          JavaLayerConfigurations.LayerType.RESOURCES,
          resourcesOutputDirectory,
          path -> true,
          appRoot.resolve(JavaEntrypointConstructor.DEFAULT_RELATIVE_RESOURCES_PATH_ON_IMAGE));
    }

    // Adds dependency files.
    FileCollection dependencyFiles =
        allFiles
            .minus(classesDirectories)
            .filter(file -> !file.toPath().equals(resourcesOutputDirectory));
    for (File dependencyFile : dependencyFiles) {
      AbsoluteUnixPath pathInContainer =
          appRoot
              .resolve(JavaEntrypointConstructor.DEFAULT_RELATIVE_DEPENDENCIES_PATH_ON_IMAGE)
              .resolve(dependencyFile.getName());

      if (dependencyFile.getName().contains("SNAPSHOT")) {
        layerBuilder.addFile(
            JavaLayerConfigurations.LayerType.SNAPSHOT_DEPENDENCIES,
            dependencyFile.toPath(),
            pathInContainer);
      } else {
        layerBuilder.addFile(
            JavaLayerConfigurations.LayerType.DEPENDENCIES,
            dependencyFile.toPath(),
            pathInContainer);
      }
    }

    // Adds all the extra files.
    if (Files.exists(extraDirectory)) {
      layerBuilder.addFilesRoot(
          JavaLayerConfigurations.LayerType.EXTRA_FILES,
          extraDirectory,
          path -> true,
          AbsoluteUnixPath.get("/"));
    }

    return layerBuilder.build();
  }

  /**
   * Resolves the {@link JavaLayerConfigurations} for a WAR Gradle {@link Project}.
   *
   * @param project the Gradle {@link Project}
   * @param logger the build logger for providing feedback about the resolution
   * @param extraDirectory path to the source directory for the extra files layer
   * @param appRoot root directory in the image where the app will be placed
   * @return {@link JavaLayerConfigurations} for the layers for the Gradle {@link Project}
   * @throws IOException if an I/O exception occurred during resolution
   */
  private static JavaLayerConfigurations getForWarProject(
      Project project, Logger logger, Path extraDirectory, AbsoluteUnixPath appRoot)
      throws IOException {
    Path explodedWarPath = GradleProjectProperties.getExplodedWarDirectory(project);
    return JavaLayerConfigurationsHelper.fromExplodedWar(explodedWarPath, appRoot, extraDirectory);
  }

  private GradleLayerConfigurations() {}
}
