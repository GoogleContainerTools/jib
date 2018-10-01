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
import com.google.cloud.tools.jib.filesystem.DirectoryWalker;
import com.google.cloud.tools.jib.frontend.JavaEntrypointConstructor;
import com.google.cloud.tools.jib.frontend.JavaLayerConfigurations;
import com.google.cloud.tools.jib.frontend.JavaLayerConfigurations.Builder;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.logging.Logger;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;

/** Builds {@link JavaLayerConfigurations} based on inputs from a {@link Project}. */
class GradleLayerConfigurations {

  /** Name of the `main` {@link SourceSet} to use as source files. */
  private static final String MAIN_SOURCE_SET_NAME = "main";

  /** The filename suffix for snapshot dependency JARs. */
  private static final String SNAPSHOT = "SNAPSHOT";

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
   * Resolves the {@link JavaLayerConfigurations} for a non-war Gradle {@link Project}.
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
    JavaPluginConvention javaPluginConvention =
        project.getConvention().getPlugin(JavaPluginConvention.class);

    SourceSet mainSourceSet = javaPluginConvention.getSourceSets().getByName(MAIN_SOURCE_SET_NAME);

    List<Path> dependenciesFiles = new ArrayList<>();
    List<Path> snapshotDependenciesFiles = new ArrayList<>();
    List<Path> classesFiles = new ArrayList<>();
    List<Path> extraFiles = new ArrayList<>();

    // Adds each file in each classes output directory to the classes files list.
    FileCollection classesOutputDirectories = mainSourceSet.getOutput().getClassesDirs();
    logger.info("Adding corresponding output directories of source sets to image");
    for (File classesOutputDirectory : classesOutputDirectories) {
      if (Files.notExists(classesOutputDirectory.toPath())) {
        logger.info("\t'" + classesOutputDirectory + "' (not found, skipped)");
        continue;
      }
      logger.info("\t'" + classesOutputDirectory + "'");
      try (Stream<Path> classFileStream = Files.list(classesOutputDirectory.toPath())) {
        classFileStream.forEach(classesFiles::add);
      }
    }
    if (classesFiles.isEmpty()) {
      logger.warn("No classes files were found - did you compile your project?");
    }

    Path resourcesOutputDirectory = mainSourceSet.getOutput().getResourcesDir().toPath();

    // Adds all other files to the dependencies files list.
    FileCollection allFiles = mainSourceSet.getRuntimeClasspath();
    // Removes the classes output directories.
    allFiles = allFiles.minus(classesOutputDirectories);
    for (File dependencyFile : allFiles) {
      // Removes the resources output directory.
      if (resourcesOutputDirectory.equals(dependencyFile.toPath())) {
        continue;
      }
      if (dependencyFile.getName().contains(SNAPSHOT)) {
        snapshotDependenciesFiles.add(dependencyFile.toPath());
      } else {
        dependenciesFiles.add(dependencyFile.toPath());
      }
    }

    // Adds all the extra files.
    if (Files.exists(extraDirectory)) {
      try (Stream<Path> extraFilesLayerDirectoryFiles = Files.list(extraDirectory)) {
        extraFiles = extraFilesLayerDirectoryFiles.collect(Collectors.toList());
      }
    }

    AbsoluteUnixPath dependenciesExtractionPath =
        appRoot.resolve(JavaEntrypointConstructor.DEFAULT_RELATIVE_DEPENDENCIES_PATH_ON_IMAGE);
    AbsoluteUnixPath resourcesExtractionPath =
        appRoot.resolve(JavaEntrypointConstructor.DEFAULT_RELATIVE_RESOURCES_PATH_ON_IMAGE);
    AbsoluteUnixPath classesExtractionPath =
        appRoot.resolve(JavaEntrypointConstructor.DEFAULT_RELATIVE_CLASSES_PATH_ON_IMAGE);

    Builder layerBuilder = JavaLayerConfigurations.builder();
    for (Path file : dependenciesFiles) {
      layerBuilder.addDependencyFile(file, dependenciesExtractionPath.resolve(file.getFileName()));
    }
    for (Path file : snapshotDependenciesFiles) {
      layerBuilder.addSnapshotDependencyFile(
          file, dependenciesExtractionPath.resolve(file.getFileName()));
    }
    if (Files.exists(resourcesOutputDirectory)) {
      layerBuilder.addResourceFile(resourcesOutputDirectory, resourcesExtractionPath);
    }
    for (Path file : classesFiles) {
      layerBuilder.addClassFile(file, classesExtractionPath.resolve(file.getFileName()));
    }
    for (Path file : extraFiles) {
      layerBuilder.addExtraFile(file, AbsoluteUnixPath.get("/").resolve(file.getFileName()));
    }
    return layerBuilder.build();
  }

  /**
   * Resolves the {@link JavaLayerConfigurations} for a War Gradle {@link Project}.
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
    List<Path> dependenciesFiles = new ArrayList<>();
    List<Path> snapshotDependenciesFiles = new ArrayList<>();
    List<Path> resourcesFiles = new ArrayList<>();
    List<Path> resourcesWebInfFiles = new ArrayList<>();
    List<Path> extraFiles = new ArrayList<>();

    Path explodedWarPath = GradleProjectProperties.getExplodedWarDirectory(project);

    Path libOutputDirectory = explodedWarPath.resolve("WEB-INF/lib");
    try (Stream<Path> dependencyFileStream = Files.list(libOutputDirectory)) {
      dependencyFileStream.forEach(
          path -> {
            if (path.toString().contains(SNAPSHOT)) {
              snapshotDependenciesFiles.add(path);
            } else {
              dependenciesFiles.add(path);
            }
          });
    }

    // First, all files except WEB-INF go into the resources layer.
    try (Stream<Path> fileStream = Files.list(explodedWarPath)) {
      fileStream.filter(path -> !path.endsWith("WEB-INF")).forEach(resourcesFiles::add);
    }
    // Some files in WEB-INF/ (e.g. web.xml, ...) need to go into the resources layer. However,
    // don't add or look into WEB-INF/classes and WEB-INF/lib.
    Path webInfOutputDirectory = explodedWarPath.resolve("WEB-INF");
    try (Stream<Path> fileStream = Files.list(webInfOutputDirectory)) {
      fileStream
          .filter(path -> !path.endsWith("classes") && !path.endsWith("lib"))
          .forEach(resourcesWebInfFiles::add);
    }

    // Adds all the extra files.
    if (Files.exists(extraDirectory)) {
      try (Stream<Path> extraFilesLayerDirectoryFiles = Files.list(extraDirectory)) {
        extraFiles = extraFilesLayerDirectoryFiles.collect(Collectors.toList());
      }
    }

    Builder layerBuilder = JavaLayerConfigurations.builder();
    AbsoluteUnixPath dependenciesExtractionPath = appRoot.resolve("WEB-INF/lib");
    AbsoluteUnixPath classesExtractionPath = appRoot.resolve("WEB-INF/classes");
    AbsoluteUnixPath webInfExtractionPath = appRoot.resolve("WEB-INF");

    // For "WEB-INF/classes", *.class go into the class layer. All other files and empty directories
    // go into the resource layer.
    Path webInfClasses = explodedWarPath.resolve("WEB-INF/classes");
    new DirectoryWalker(webInfClasses)
        .walk(
            path -> {
              AbsoluteUnixPath pathInContainer =
                  classesExtractionPath.resolve(webInfClasses.relativize(path));

              if (FileSystems.getDefault().getPathMatcher("glob:**.class").matches(path)) {
                layerBuilder.addClassFile(path, pathInContainer);

              } else if (Files.isDirectory(path)) {
                try (Stream<Path> stream = Files.list(path)) {
                  if (!stream.findAny().isPresent()) {
                    // The directory is empty
                    layerBuilder.addResourceFile(path, pathInContainer);
                  }
                }
              } else {
                layerBuilder.addResourceFile(path, pathInContainer);
              }
            });

    for (Path file : dependenciesFiles) {
      layerBuilder.addDependencyFile(file, dependenciesExtractionPath.resolve(file.getFileName()));
    }
    for (Path file : snapshotDependenciesFiles) {
      layerBuilder.addSnapshotDependencyFile(
          file, dependenciesExtractionPath.resolve(file.getFileName()));
    }
    for (Path file : resourcesFiles) {
      layerBuilder.addResourceFile(file, appRoot.resolve(file.getFileName()));
    }
    for (Path file : resourcesWebInfFiles) {
      layerBuilder.addResourceFile(file, webInfExtractionPath.resolve(file.getFileName()));
    }
    for (Path file : extraFiles) {
      layerBuilder.addExtraFile(file, AbsoluteUnixPath.get("/").resolve(file.getFileName()));
    }
    return layerBuilder.build();
  }

  private GradleLayerConfigurations() {}
}
