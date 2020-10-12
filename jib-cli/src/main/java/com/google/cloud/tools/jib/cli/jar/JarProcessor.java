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
import com.google.cloud.tools.jib.filesystem.TempDirectoryProvider;
import com.google.cloud.tools.jib.plugins.common.ZipUtil;
import com.google.common.base.Predicate;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;
import java.util.stream.Stream;

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
   * @return list of {@link FileEntriesLayer}.
   * @throws IOException if I/O error occurs when opening the file.
   */
  public static List<FileEntriesLayer> explodeStandardJar(Path jarPath) throws IOException {
    TempDirectoryProvider tempDirectoryProvider = new TempDirectoryProvider();
    Path tempDirectoryPath = tempDirectoryProvider.newDirectory();
    ZipUtil.unzip(jarPath, tempDirectoryPath);

    List<FileEntriesLayer> layers = new ArrayList<>();
    Predicate<Path> isClassFile = path -> path.getFileName().toString().endsWith(".class");
    Predicate<Path> isResourceFile = path -> !path.getFileName().toString().endsWith(".class");

    // Determine class and resource files in the directory containing jar contents and create
    // FileEntriesLayer for each type of layer (class or resource), while maintaining the
    // file's original project structure.
    FileEntriesLayer classesLayer =
        addDirectoryContentsToLayer(
                FileEntriesLayer.builder(),
                tempDirectoryPath,
                isClassFile,
                APP_ROOT.resolve(RelativeUnixPath.get("explodedJar")))
            .setName("Classes")
            .build();
    FileEntriesLayer resourcesLayer =
        addDirectoryContentsToLayer(
                FileEntriesLayer.builder(),
                tempDirectoryPath,
                isResourceFile,
                APP_ROOT.resolve(RelativeUnixPath.get("explodedJar")))
            .setName("Resources")
            .build();

    // Get dependencies from Class-Path in the jar's manifest and create a FileEntriesLayer.Builder
    // with these dependencies as entries.
    List<Path> dependencies = new ArrayList<>();
    JarFile jarFile = new JarFile(jarPath.toFile());
    String classPath = jarFile.getManifest().getMainAttributes().getValue("Class-Path");
    if (classPath != null) {
      for (String dependency : Splitter.on(" ").split(classPath)) {
        Path depPath = Paths.get(dependency);
        dependencies.add(depPath);
      }
    } else {
      throw new IllegalStateException("Class path is not specified.");
    }
    FileEntriesLayer.Builder dependenciesLayerBuilder = FileEntriesLayer.builder();
    dependencies.forEach(
        path ->
            dependenciesLayerBuilder.addEntry(
                path, APP_ROOT.resolve(RelativeUnixPath.get("dependencies")).resolve(path)));
    dependenciesLayerBuilder.setName("Dependencies");

    layers.add(classesLayer);
    layers.add(resourcesLayer);
    layers.add(dependenciesLayerBuilder.build());

    return layers;
  }

  private static FileEntriesLayer.Builder addDirectoryContentsToLayer(
      FileEntriesLayer.Builder builder,
      Path sourceRoot,
      Predicate<Path> pathFilter,
      AbsoluteUnixPath basePathInContainer)
      throws IOException {
    try (Stream<Path> fileStream = Files.walk(sourceRoot)) {
      ImmutableList<Path> directoryPaths =
          fileStream
              .filter(path -> !path.equals(sourceRoot))
              .filter(path -> Files.isDirectory(path) || pathFilter.test(path))
              .collect(ImmutableList.toImmutableList());
      for (Path path : directoryPaths) {
        AbsoluteUnixPath pathOnContainer = basePathInContainer.resolve(sourceRoot.relativize(path));
        builder.addEntry(path, pathOnContainer);
      }
    }
    return builder;
  }
}
