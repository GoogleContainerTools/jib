/*
 * Copyright 2020 Google LLC.
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

package com.google.cloud.tools.jib.cli.jar;

import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer;
import com.google.cloud.tools.jib.api.buildplan.RelativeUnixPath;
import com.google.cloud.tools.jib.filesystem.DirectoryWalker;
import com.google.cloud.tools.jib.filesystem.TempDirectoryProvider;
import com.google.cloud.tools.jib.plugins.common.ZipUtil;
import com.google.common.base.Splitter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/** Process jar file contents and create layers. */
public class JarProcessor {

  private static final AbsoluteUnixPath APP_ROOT = AbsoluteUnixPath.get("/app");

  /**
   * Jar Type.
   *
   * <ul>
   *   <li>{@code STANDARD} a regular jar.
   *   <li>{@code SPRING_BOOT} a spring boot fat jar.
   * </ul>
   */
  public enum JarType {
    STANDARD,
    SPRING_BOOT;
  }

  /**
   * Determines whether the jar is a spring boot or regular jar, given a path to the jar.
   *
   * @param jarPath path to the jar.
   * @return the jar type.
   * @throws IOException if I/O error occurs when opening the file.
   */
  public static JarType determineJarType(Path jarPath) throws IOException {
    JarFile jarFile = new JarFile(jarPath.toFile());
    if (jarFile.getEntry("BOOT-INF") != null) {
      return JarType.SPRING_BOOT;
    }
    return JarType.STANDARD;
  }

  /**
   * Explode jar and create three layers for classes, resources and dependencies on container.
   *
   * @param jarPath path to jar file.
   * @param tempDirPath path to temporary jib local directory to use.
   * @return list of {@link FileEntriesLayer}.
   * @throws IOException if I/O error occurs when opening the jar file or if the directory for the
   *     temporary directory path provided, doesn't exist.
   */
  public static List<FileEntriesLayer> explodeStandardJar(Path jarPath, @Nullable Path tempDirPath)
      throws IOException {
    Path localExplodedJarRoot = tempDirPath;
    try (TempDirectoryProvider tempDirectoryProvider = new TempDirectoryProvider()) {
      if (localExplodedJarRoot == null) {
        localExplodedJarRoot = tempDirectoryProvider.newDirectory();
      }
      ZipUtil.unzip(jarPath, localExplodedJarRoot);

      List<FileEntriesLayer> layers = new ArrayList<>();
      Predicate<Path> isClassFile = path -> path.getFileName().toString().endsWith(".class");
      Predicate<Path> isResourceFile = isClassFile.negate();

      // Determine class and resource files in the directory containing jar contents and create
      // FileEntriesLayer for each type of layer (class or resource), while maintaining the
      // file's original project structure.
      FileEntriesLayer classesLayer =
          addDirectoryContentsToLayer(
                  FileEntriesLayer.builder(),
                  localExplodedJarRoot,
                  isClassFile,
                  APP_ROOT.resolve(RelativeUnixPath.get("explodedJar")))
              .setName("classes")
              .build();
      FileEntriesLayer resourcesLayer =
          addDirectoryContentsToLayer(
                  FileEntriesLayer.builder(),
                  localExplodedJarRoot,
                  isResourceFile,
                  APP_ROOT.resolve(RelativeUnixPath.get("explodedJar")))
              .setName("resources")
              .build();

      // Get dependencies from Class-Path in the jar's manifest and create a
      // FileEntriesLayer.Builder with these dependencies as entries. If Class-Path in the jar's
      // manifest is not present then skip adding a dependencies layer.
      JarFile jarFile = new JarFile(jarPath.toFile());
      String classPath =
          jarFile.getManifest().getMainAttributes().getValue(Attributes.Name.CLASS_PATH);
      if (classPath != null) {
        List<Path> dependencies =
            Splitter.onPattern("\\s+")
                .splitToList(classPath.trim())
                .stream()
                .map(Paths::get)
                .collect(Collectors.toList());
        FileEntriesLayer.Builder dependenciesLayerBuilder = FileEntriesLayer.builder();
        dependencies.forEach(
            path ->
                dependenciesLayerBuilder.addEntry(
                    path, APP_ROOT.resolve(RelativeUnixPath.get("dependencies")).resolve(path)));
        dependenciesLayerBuilder.setName("dependencies");
        layers.add(dependenciesLayerBuilder.build());
      }

      layers.add(resourcesLayer);
      layers.add(classesLayer);
      return layers;
    }
  }

  private static FileEntriesLayer.Builder addDirectoryContentsToLayer(
      FileEntriesLayer.Builder builder,
      Path sourceRoot,
      Predicate<Path> pathFilter,
      AbsoluteUnixPath basePathInContainer)
      throws IOException {
    new DirectoryWalker(sourceRoot)
        .filterRoot()
        .filter(path -> Files.isDirectory(path) || pathFilter.test(path))
        .walk(
            path -> {
              AbsoluteUnixPath pathOnContainer =
                  basePathInContainer.resolve(sourceRoot.relativize(path));
              builder.addEntry(path, pathOnContainer);
            });
    return builder;
  }
}
