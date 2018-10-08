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

package com.google.cloud.tools.jib.plugins.common;

import com.google.cloud.tools.jib.filesystem.AbsoluteUnixPath;
import com.google.cloud.tools.jib.filesystem.DirectoryWalker;
import com.google.cloud.tools.jib.frontend.JavaLayerConfigurations;
import com.google.cloud.tools.jib.frontend.JavaLayerConfigurations.Builder;
import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.util.function.Predicate;
import java.util.stream.Stream;

/** Helper for constructing {@link JavaLayerConfigurations}. */
public class JavaLayerConfigurationsHelper {

  @FunctionalInterface
  public static interface FileToLayerAdder {

    void add(Path sourcePath, AbsoluteUnixPath pathInContainer) throws IOException;
  }

  @VisibleForTesting
  static boolean isEmptyDirectory(Path path) throws IOException {
    if (Files.isDirectory(path)) {
      try (Stream<Path> stream = Files.list(path)) {
        return !stream.findAny().isPresent();
      }
    }
    return false;
  }

  /**
   * Adds files to a layer selectively and recursively. {@code sourceRoot} must be a directory.
   * Non-empty directories will be ignored, while empty directories will always be added. Note that
   * empty directories will always be added regardless of {@code pathFilter}.
   *
   * <p>The contents of {@code sourceRoot} will be placed into {@code basePathInContainer}. For
   * example, if {@code sourceRoot} is {@code /usr/home}, {@code /usr/home/passwd} exists locally,
   * and {@code basePathInContainer} is {@code /etc}, then the image will have {@code /etc/passwd}.
   *
   * @param sourceRoot root directory whose contents will be added
   * @param pathFilter only the files satisfying the filter will be added, unless the files are
   *     empty directories
   * @param basePathInContainer directory in the layer into which the source contents are added
   * @param addFileToLayer function that should add the file to the layer; the function gets the
   *     path of the source file (may be a directory) and the final destination path in the layer
   * @throws IOException error while listing directories
   * @throws NotDirectoryException if {@code sourceRoot} is not a directory
   */
  public static void addFilesToLayer(
      Path sourceRoot,
      Predicate<Path> pathFilter,
      AbsoluteUnixPath basePathInContainer,
      FileToLayerAdder addFileToLayer)
      throws IOException {

    new DirectoryWalker(sourceRoot)
        .walk(
            path -> {
              // Always add empty directories. However, ignore non-empty directories because
              // otherwise JavaLayerConfigurations will add files recursively.
              if (isEmptyDirectory(path) || (!Files.isDirectory(path) && pathFilter.test(path))) {
                addFileToLayer.add(path, basePathInContainer.resolve(sourceRoot.relativize(path)));
              }
            });
  }

  public static JavaLayerConfigurations fromExplodedWar(
      Path explodedWar, AbsoluteUnixPath appRoot, Path extraDirectory) throws IOException {
    Path webInfClasses = explodedWar.resolve("WEB-INF/classes");
    Path webInfLib = explodedWar.resolve("WEB-INF/lib");

    AbsoluteUnixPath dependenciesExtractionPath = appRoot.resolve("WEB-INF/lib");
    AbsoluteUnixPath classesExtractionPath = appRoot.resolve("WEB-INF/classes");

    Builder layerBuilder = JavaLayerConfigurations.builder();

    // Gets all the dependencies.
    Predicate<Path> isSnapshotDependency =
        path -> path.getFileName().toString().contains("SNAPSHOT");
    if (Files.exists(webInfLib)) {
      addFilesToLayer(
          webInfLib,
          isSnapshotDependency,
          dependenciesExtractionPath,
          layerBuilder::addSnapshotDependencyFile);
      addFilesToLayer(
          webInfLib,
          isSnapshotDependency.negate(),
          dependenciesExtractionPath,
          layerBuilder::addDependencyFile);
    }

    // Gets the classes files in the 'WEB-INF/classes' output directory.
    Predicate<Path> isClassFile = path -> path.getFileName().toString().endsWith(".class");
    if (Files.exists(webInfClasses)) {
      addFilesToLayer(
          webInfClasses, isClassFile, classesExtractionPath, layerBuilder::addClassFile);
    }

    // Gets the resources.
    Predicate<Path> isResource =
        path -> {
          boolean inWebInfClasses = path.startsWith(webInfClasses);
          boolean inWebInfLib = path.startsWith(webInfLib);

          return (!inWebInfClasses && !inWebInfLib) || (inWebInfClasses && !isClassFile.test(path));
        };
    addFilesToLayer(explodedWar, isResource, appRoot, layerBuilder::addResourceFile);

    // Adds all the extra files.
    if (Files.exists(extraDirectory)) {
      AbsoluteUnixPath extractionBase = AbsoluteUnixPath.get("/");
      addFilesToLayer(extraDirectory, path -> true, extractionBase, layerBuilder::addExtraFile);
    }

    return layerBuilder.build();
  }
}
