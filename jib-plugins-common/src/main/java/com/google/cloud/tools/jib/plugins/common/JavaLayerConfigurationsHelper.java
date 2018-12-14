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

import com.google.cloud.tools.jib.configuration.FilePermissions;
import com.google.cloud.tools.jib.filesystem.AbsoluteUnixPath;
import com.google.cloud.tools.jib.frontend.JavaLayerConfigurations;
import com.google.cloud.tools.jib.frontend.JavaLayerConfigurations.LayerType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Predicate;

/** Helper for constructing {@link JavaLayerConfigurations}. */
public class JavaLayerConfigurationsHelper {

  /**
   * Constructs a new {@link JavaLayerConfigurations} for a WAR project.
   *
   * @param explodedWar the exploded WAR directory
   * @param appRoot root directory in the image where the app will be placed
   * @param extraFilesDirectory path to the source directory for the extra files layer
   * @param extraDirectoryPermissions map from path on container to file permissions
   * @return {@link JavaLayerConfigurations} for the layers for the exploded WAR
   * @throws IOException if adding layer contents fails
   */
  public static JavaLayerConfigurations fromExplodedWar(
      Path explodedWar,
      AbsoluteUnixPath appRoot,
      Path extraFilesDirectory,
      Map<AbsoluteUnixPath, FilePermissions> extraDirectoryPermissions)
      throws IOException {
    Path webInfLib = explodedWar.resolve("WEB-INF/lib");
    Path webInfClasses = explodedWar.resolve("WEB-INF/classes");

    Predicate<Path> nameHasSnapshot = path -> path.getFileName().toString().contains("SNAPSHOT");
    Predicate<Path> isSnapshotDependency =
        path -> path.startsWith(webInfLib) && nameHasSnapshot.test(path);
    Predicate<Path> isNonSnapshotDependency =
        path -> path.startsWith(webInfLib) && !nameHasSnapshot.test(path);
    Predicate<Path> isClassFile =
        path -> path.startsWith(webInfClasses) && path.getFileName().toString().endsWith(".class");
    Predicate<Path> isResource =
        isSnapshotDependency.or(isNonSnapshotDependency).or(isClassFile).negate();

    JavaLayerConfigurations.Builder layerBuilder = JavaLayerConfigurations.builder();

    // Gets all the dependencies.
    if (Files.exists(webInfLib)) {
      AbsoluteUnixPath basePathInContainer = appRoot.resolve("WEB-INF/lib");
      layerBuilder.addDirectoryContents(
          LayerType.DEPENDENCIES, webInfLib, isNonSnapshotDependency, basePathInContainer);
      layerBuilder.addDirectoryContents(
          LayerType.SNAPSHOT_DEPENDENCIES, webInfLib, isSnapshotDependency, basePathInContainer);
    }

    // Gets the classes files in the 'WEB-INF/classes' output directory.
    if (Files.exists(webInfClasses)) {
      layerBuilder.addDirectoryContents(
          LayerType.CLASSES, webInfClasses, isClassFile, appRoot.resolve("WEB-INF/classes"));
    }

    // Gets the resources.
    layerBuilder.addDirectoryContents(LayerType.RESOURCES, explodedWar, isResource, appRoot);

    // Adds all the extra files.
    if (Files.exists(extraFilesDirectory)) {
      layerBuilder.addDirectoryContents(
          LayerType.EXTRA_FILES,
          extraFilesDirectory,
          path -> true,
          AbsoluteUnixPath.get("/"),
          extraDirectoryPermissions);
    }

    return layerBuilder.build();
  }

  private JavaLayerConfigurationsHelper() {}
}
