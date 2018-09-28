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

  /** The filename suffix for a maven/gradle snapshot dependency */
  private static final String SNAPSHOT = "SNAPSHOT";
  /** The standard directory name containing libs, classes, web.xml, etc... in a War Project */
  private static final String WEB_INF = "WEB-INF";
  /** The standard directory name containing libs and snapshot-libs in a War Project */
  private static final String WEB_INF_LIB = WEB_INF + "/lib/";
  /** The standard directory name containing classes and some resources in a War Project */
  private static final String WEB_INF_CLASSES = WEB_INF + "/classes/";

  /**
   * Resolves the {@link JavaLayerConfigurations} for a Gradle {@link Project}.
   *
   * @param project the Gradle {@link Project}
   * @param logger the logger for providing feedback about the resolution
   * @param extraDirectory path to the directory for the extra files layer
   * @param appRoot root directory in the image where the app will be placed
   * @return a {@link JavaLayerConfigurations} for the layers for the Gradle {@link Project}
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
   * @param extraDirectory path to the directory for the extra files layer
   * @param appRoot root directory in the image where the app will be placed
   * @return a {@link JavaLayerConfigurations} for the layers for the Gradle {@link Project}
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
   * @param extraDirectory path to the directory for the extra files layer
   * @param appRoot root directory in the image where the app will be placed
   * @return a {@link JavaLayerConfigurations} for the layers for the Gradle {@link Project}
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

    Path libOutputDirectory = explodedWarPath.resolve(WEB_INF_LIB);
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
    // All files except classes and libs in resources
    try (Stream<Path> fileStream = Files.list(explodedWarPath)) {
      fileStream.filter(path -> !path.endsWith(WEB_INF)).forEach(resourcesFiles::add);
    }
    // Some files in WEB-INF need to be in resources directory (e.g. web.xml, ...)
    Path webinfOutputDirectory = explodedWarPath.resolve(WEB_INF);
    try (Stream<Path> fileStream = Files.list(webinfOutputDirectory)) {
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
    AbsoluteUnixPath dependenciesExtractionPath = appRoot.resolve(WEB_INF_LIB);
    AbsoluteUnixPath classesExtractionPath = appRoot.resolve(WEB_INF_CLASSES);
    AbsoluteUnixPath webInfExtractionPath = appRoot.resolve(WEB_INF);

    // For "WEB-INF/classes", *.class in class layer, other files and empty directories in resource
    // layer
    Path srcWebInfClasses = explodedWarPath.resolve(WEB_INF_CLASSES);
    new DirectoryWalker(srcWebInfClasses)
        .walk(
            path -> {
              if (FileSystems.getDefault().getPathMatcher("glob:**.class").matches(path)) {
                layerBuilder.addClassFile(
                    path, classesExtractionPath.resolve(srcWebInfClasses.relativize(path)));

              } else if (Files.isDirectory(path)) {
                try (Stream<Path> dirStream = Files.list(path)) {
                  if (!dirStream.findAny().isPresent()) {
                    // The directory is empty
                    layerBuilder.addResourceFile(
                        path, classesExtractionPath.resolve(srcWebInfClasses.relativize(path)));
                  }
                }
              } else {
                layerBuilder.addResourceFile(
                    path, classesExtractionPath.resolve(srcWebInfClasses.relativize(path)));
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
