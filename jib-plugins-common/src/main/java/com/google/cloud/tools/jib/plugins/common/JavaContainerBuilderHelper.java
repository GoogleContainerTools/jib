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

import com.google.cloud.tools.jib.api.JavaContainerBuilder;
import com.google.cloud.tools.jib.api.JibContainerBuilder;
import com.google.cloud.tools.jib.api.RegistryImage;
import com.google.cloud.tools.jib.configuration.FilePermissions;
import com.google.cloud.tools.jib.configuration.LayerConfiguration;
import com.google.cloud.tools.jib.filesystem.AbsoluteUnixPath;
import com.google.cloud.tools.jib.filesystem.DirectoryWalker;
import com.google.cloud.tools.jib.filesystem.RelativeUnixPath;
import com.google.cloud.tools.jib.frontend.JavaLayerConfigurations.LayerType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Predicate;

/** Helper for constructing {@link JavaContainerBuilder}-based {@link JibContainerBuilder}s. */
public class JavaContainerBuilderHelper {

  /**
   * Returns a {@link LayerConfiguration} for adding the extra directory to the container.
   *
   * @param extraDirectory the source extra directory path
   * @param extraDirectoryPermissions map from path on container to file permissions
   * @return a {@link LayerConfiguration} for adding the extra directory to the container
   * @throws IOException if walking the extra directory fails
   */
  public static LayerConfiguration extraDirectoryLayerConfiguration(
      Path extraDirectory, Map<AbsoluteUnixPath, FilePermissions> extraDirectoryPermissions)
      throws IOException {
    LayerConfiguration.Builder builder =
        LayerConfiguration.builder().setName(LayerType.EXTRA_FILES.getName());
    new DirectoryWalker(extraDirectory)
        .filterRoot()
        .walk(
            path -> {
              AbsoluteUnixPath pathOnContainer =
                  AbsoluteUnixPath.get("/").resolve(extraDirectory.relativize(path));
              builder.addEntry(
                  path, pathOnContainer, extraDirectoryPermissions.get(pathOnContainer));
            });
    return builder.build();
  }

  /**
   * Constructs a new {@link JibContainerBuilder} for a WAR project.
   *
   * @param baseImage the base image of the container
   * @param explodedWar the exploded WAR directory
   * @param appRoot root directory in the image where the app will be placed
   * @return {@link JibContainerBuilder} containing the layers for the exploded WAR
   * @throws IOException if adding layer contents fails
   */
  public static JibContainerBuilder fromExplodedWar(
      RegistryImage baseImage, Path explodedWar, AbsoluteUnixPath appRoot) throws IOException {
    Path webInfLib = explodedWar.resolve("WEB-INF/lib");
    Path webInfClasses = explodedWar.resolve("WEB-INF/classes");
    Predicate<Path> isDependency = path -> path.startsWith(webInfLib);
    Predicate<Path> isClassFile =
        path -> path.startsWith(webInfClasses) && path.getFileName().toString().endsWith(".class");
    Predicate<Path> isResource = isDependency.or(isClassFile).negate();

    JavaContainerBuilder javaContainerBuilder =
        JavaContainerBuilder.from(baseImage)
            .setAppRoot(appRoot)
            .setResourcesDestination(RelativeUnixPath.get(""))
            .setClassesDestination(RelativeUnixPath.get("WEB-INF/classes"))
            .setDependenciesDestination(RelativeUnixPath.get("WEB-INF/lib"));

    if (Files.exists(explodedWar)) {
      javaContainerBuilder.addResources(explodedWar, isResource);
    }
    if (Files.exists(webInfClasses)) {
      javaContainerBuilder.addClasses(webInfClasses, isClassFile);
    }
    if (Files.exists(webInfLib)) {
      javaContainerBuilder.addDependencies(webInfLib);
    }
    return javaContainerBuilder.toContainerBuilder();
  }

  private JavaContainerBuilderHelper() {}
}
