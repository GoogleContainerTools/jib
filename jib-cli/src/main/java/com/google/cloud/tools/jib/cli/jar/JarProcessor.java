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
   * Determines whether the jar is a spring boot or standard jar.
   *
   * @param jarPath path to the jar
   * @return the jar type
   * @throws IOException if I/O error occurs when opening the file
   */
  public static JarType determineJarType(Path jarPath) throws IOException {
    JarFile jarFile = new JarFile(jarPath.toFile());
    if (jarFile.getEntry("BOOT-INF") != null) {
      return JarType.SPRING_BOOT;
    }
    return JarType.STANDARD;
  }

  /**
   * Explodes jar and create three layers for classes, resources and dependencies on container.
   *
   * @param jarPath path to jar file
   * @param tempDirPath path to temporary jib local directory
   * @return list of {@link FileEntriesLayer}
   * @throws IOException if I/O error occurs when opening the jar file or if temporary directory
   *     provided doesn't exist
   */
  public static List<FileEntriesLayer> explodeStandardJar(Path jarPath, Path tempDirPath)
      throws IOException {
    Path localExplodedJarRoot = tempDirPath;
    ZipUtil.unzip(jarPath, localExplodedJarRoot);
    List<FileEntriesLayer> layers = new ArrayList<>();

    // Get dependencies from Class-Path in the jar's manifest and add a layer with these
    // dependencies as entries. If Class-Path is not present in the jar's manifest then skip adding
    // a dependencies layer.
    JarFile jarFile = new JarFile(jarPath.toFile());
    String classPath =
        jarFile.getManifest().getMainAttributes().getValue(Attributes.Name.CLASS_PATH);
    if (classPath != null) {
      Predicate<String> isSnapshotDependency = name -> name.contains("SNAPSHOT");
      Predicate<String> isNotSnapshotDependency = isSnapshotDependency.negate();
      List<Path> nonSnapshotDependencies =
          Splitter.onPattern("\\s+")
              .splitToList(classPath.trim())
              .stream()
              .filter(isNotSnapshotDependency)
              .map(Paths::get)
              .collect(Collectors.toList());
      List<Path> snapshotDependencies =
          Splitter.onPattern("\\s+")
              .splitToList(classPath.trim())
              .stream()
              .filter(isSnapshotDependency)
              .map(Paths::get)
              .collect(Collectors.toList());
      if (!nonSnapshotDependencies.isEmpty()) {
        FileEntriesLayer.Builder nonSnapshotDependenciesLayerBuilder =
            FileEntriesLayer.builder().setName("nonSnapshotDependencies");
        nonSnapshotDependencies.forEach(
            path ->
                nonSnapshotDependenciesLayerBuilder.addEntry(
                    path, APP_ROOT.resolve(RelativeUnixPath.get("dependencies")).resolve(path)));
        layers.add(nonSnapshotDependenciesLayerBuilder.build());
      }
      if (!snapshotDependencies.isEmpty()) {
        FileEntriesLayer.Builder snapshotDependenciesLayerBuilder =
            FileEntriesLayer.builder().setName("snapshotDependencies");
        snapshotDependencies.forEach(
            path ->
                snapshotDependenciesLayerBuilder.addEntry(
                    path, APP_ROOT.resolve(RelativeUnixPath.get("dependencies")).resolve(path)));
        layers.add(snapshotDependenciesLayerBuilder.build());
      }
    }

    Predicate<Path> isClassFile = path -> path.getFileName().toString().endsWith(".class");
    Predicate<Path> isResourceFile = isClassFile.negate();

    // Determine class and resource files in the directory containing jar contents and create
    // FileEntriesLayer for each type of layer (classes or resources), while maintaining the
    // file's original project structure.
    FileEntriesLayer classesLayer =
        addDirectoryContentsToLayer(
            "classes",
            localExplodedJarRoot,
            isClassFile,
            APP_ROOT.resolve(RelativeUnixPath.get("explodedJar")));
    FileEntriesLayer resourcesLayer =
        addDirectoryContentsToLayer(
            "resources",
            localExplodedJarRoot,
            isResourceFile,
            APP_ROOT.resolve(RelativeUnixPath.get("explodedJar")));

    layers.add(resourcesLayer);
    layers.add(classesLayer);
    return layers;
  }

  private static FileEntriesLayer addDirectoryContentsToLayer(
      String layerName,
      Path sourceRoot,
      Predicate<Path> pathFilter,
      AbsoluteUnixPath basePathInContainer)
      throws IOException {
    FileEntriesLayer.Builder builder = FileEntriesLayer.builder().setName(layerName);
    new DirectoryWalker(sourceRoot)
        .filterRoot()
        .filter(path -> Files.isDirectory(path) || pathFilter.test(path))
        .walk(
            path -> {
              AbsoluteUnixPath pathOnContainer =
                  basePathInContainer.resolve(sourceRoot.relativize(path));
              builder.addEntry(path, pathOnContainer);
            });
    return builder.build();
  }
}
