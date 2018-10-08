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
      Path explodedWar, AbsoluteUnixPath appRoot, Path extraDirectory) throws IOException {
    Path webInfClasses = explodedWar.resolve("WEB-INF/classes");
    Path webInfLib = explodedWar.resolve("WEB-INF/lib");

    AbsoluteUnixPath dependenciesExtractionPath = appRoot.resolve("WEB-INF/lib");
    AbsoluteUnixPath classesExtractionPath = appRoot.resolve("WEB-INF/classes");

    Builder layerBuilder = JavaLayerConfigurations.builder();

    // Gets all the dependencies.
    Predicate<Path> isSnapshot = path -> path.getFileName().toString().contains("SNAPSHOT");
    if (Files.exists(webInfLib)) {
      layerBuilder.addDependenciesRoot(webInfLib, isSnapshot.negate(), dependenciesExtractionPath);
      layerBuilder.addSnapshotDependenciesRoot(webInfLib, isSnapshot, dependenciesExtractionPath);
    }

    // Gets the classes files in the 'WEB-INF/classes' output directory.
    Predicate<Path> isClassFile = path -> path.getFileName().toString().endsWith(".class");
    if (Files.exists(webInfClasses)) {
      layerBuilder.addClassesRoot(webInfClasses, isClassFile, classesExtractionPath);
    }

    // Gets the resources.
    Predicate<Path> isResource =
        path -> {
          boolean inWebInfClasses = path.startsWith(webInfClasses);
          boolean inWebInfLib = path.startsWith(webInfLib);

          return (!inWebInfClasses && !inWebInfLib) || (inWebInfClasses && !isClassFile.test(path));
        };
    layerBuilder.addResourcesRoot(explodedWar, isResource, appRoot);

    // Adds all the extra files.
    if (Files.exists(extraDirectory)) {
      layerBuilder.addExtraFilesRoot(extraDirectory, path -> true, AbsoluteUnixPath.get("/"));
    }

    return layerBuilder.build();
  }
}
