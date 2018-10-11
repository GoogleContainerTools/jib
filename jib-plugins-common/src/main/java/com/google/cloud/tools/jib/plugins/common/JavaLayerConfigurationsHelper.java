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
import com.google.cloud.tools.jib.frontend.JavaLayerConfigurations;
import com.google.cloud.tools.jib.frontend.JavaLayerConfigurations.Builder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Predicate;

/** Helper for constructing {@link JavaLayerConfigurations}. */
public class JavaLayerConfigurationsHelper {

  public static JavaLayerConfigurations fromExplodedWar(
      Path explodedWar, AbsoluteUnixPath appRoot, Path extraFilesDirectory) throws IOException {
    Path webInfLib = explodedWar.resolve("WEB-INF/lib");
    Path webInfClasses = explodedWar.resolve("WEB-INF/classes");

    Builder layerBuilder = JavaLayerConfigurations.builder();

    // Gets all the dependencies.
    if (Files.exists(webInfLib)) {
      Predicate<Path> isSnapshot = path -> path.getFileName().toString().contains("SNAPSHOT");
      layerBuilder.addDirectoryContents(
          JavaLayerConfigurations.LayerType.DEPENDENCIES,
          webInfLib,
          isSnapshot.negate(),
          appRoot.resolve("WEB-INF/lib"));
      layerBuilder.addDirectoryContents(
          JavaLayerConfigurations.LayerType.SNAPSHOT_DEPENDENCIES,
          webInfLib,
          isSnapshot,
          appRoot.resolve("WEB-INF/lib"));
    }

    // Gets the classes files in the 'WEB-INF/classes' output directory.
    Predicate<Path> isClassFile = path -> path.getFileName().toString().endsWith(".class");
    if (Files.exists(webInfClasses)) {
      layerBuilder.addDirectoryContents(
          JavaLayerConfigurations.LayerType.CLASSES,
          webInfClasses,
          isClassFile,
          appRoot.resolve("WEB-INF/classes"));
    }

    // Gets the resources.
    Predicate<Path> isResource =
        path -> {
          boolean inWebInfClasses = path.startsWith(webInfClasses);
          boolean inWebInfLib = path.startsWith(webInfLib);

          return (!inWebInfClasses && !inWebInfLib) || (inWebInfClasses && !isClassFile.test(path));
        };
    layerBuilder.addDirectoryContents(
        JavaLayerConfigurations.LayerType.RESOURCES, explodedWar, isResource, appRoot);

    // Adds all the extra files.
    if (Files.exists(extraFilesDirectory)) {
      layerBuilder.addDirectoryContents(
          JavaLayerConfigurations.LayerType.EXTRA_FILES,
          extraFilesDirectory,
          path -> true,
          AbsoluteUnixPath.get("/"));
    }

    return layerBuilder.build();
  }

  private JavaLayerConfigurationsHelper() {}
}
