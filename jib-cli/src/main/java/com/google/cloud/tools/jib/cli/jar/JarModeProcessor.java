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
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
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
public class JarModeProcessor {

  private static final AbsoluteUnixPath APP_ROOT = AbsoluteUnixPath.get("/app");
  private static final String JAR = "jar";
  private static final String CLASSES = "classes";
  private static final String RESOURCES = "resources";
  private static final String DEPENDENCIES = "dependencies";
  private static final String SNAPSHOT_DEPENDENCIES = "snapshot dependencies";

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
  @VisibleForTesting
  static JarType determineJarType(Path jarPath) throws IOException {
    try (JarFile jarFile = new JarFile(jarPath.toFile())) {
      if (jarFile.getEntry("BOOT-INF") != null) {
        return JarType.SPRING_BOOT;
      }
      return JarType.STANDARD;
    }
  }

  /**
   * Creates layers for dependencies, snapshot dependencies, resources and classes on container for
   * a standard jar.
   *
   * @param jarPath path to jar file
   * @param tempDirPath path to temporary jib local directory
   * @return list of {@link FileEntriesLayer}
   * @throws IOException if I/O error occurs when opening the jar file or if temporary directory
   *     provided doesn't exist
   */
  static List<FileEntriesLayer> createLayersForExplodedStandard(Path jarPath, Path tempDirPath)
      throws IOException {
    // Add dependencies layers.
    List<FileEntriesLayer> layers =
        getDependenciesLayers(jarPath, APP_ROOT.resolve(RelativeUnixPath.get("dependencies")));

    Path localExplodedJarRoot = tempDirPath;
    ZipUtil.unzip(jarPath, localExplodedJarRoot);

    // Determine class and resource files in the directory containing jar contents and create
    // FileEntriesLayer for each type of layer (classes or resources), while maintaining the
    // file's original project structure.
    Predicate<Path> isClassFile = path -> path.getFileName().toString().endsWith(".class");
    Predicate<Path> isResourceFile = isClassFile.negate();
    FileEntriesLayer classesLayer =
        addDirectoryContentsToLayer(
            CLASSES,
            localExplodedJarRoot,
            isClassFile,
            APP_ROOT.resolve(RelativeUnixPath.get("explodedJar")));
    FileEntriesLayer resourcesLayer =
        addDirectoryContentsToLayer(
            RESOURCES,
            localExplodedJarRoot,
            isResourceFile,
            APP_ROOT.resolve(RelativeUnixPath.get("explodedJar")));

    layers.add(resourcesLayer);
    layers.add(classesLayer);
    return layers;
  }

  /**
   * Creates layers for dependencies, snapshot dependencies and the jar itself on container for a
   * standard jar.
   *
   * @param jarPath path to jar file
   * @return list of {@link FileEntriesLayer}
   * @throws IOException if I/O error occurs when opening the jar file
   */
  static List<FileEntriesLayer> createLayersForPackagedStandard(Path jarPath) throws IOException {
    // Add dependencies layers.
    List<FileEntriesLayer> layers = getDependenciesLayers(jarPath, APP_ROOT);

    // Add layer for jar.
    FileEntriesLayer jarLayer =
        FileEntriesLayer.builder()
            .setName(JAR)
            .addEntry(jarPath, APP_ROOT.resolve(jarPath.getFileName()))
            .build();

    layers.add(jarLayer);
    return layers;
  }

  /**
   * Computes the entrypoint for a standard jar in exploded mode.
   *
   * @param jarPath path to jar file
   * @return list of {@link String} representing entrypoint
   * @throws IOException if I/O error occurs when opening the jar file
   */
  static ImmutableList<String> computeEntrypointForExplodedStandard(Path jarPath)
      throws IOException {
    try (JarFile jarFile = new JarFile(jarPath.toFile())) {
      String mainClass =
          jarFile.getManifest().getMainAttributes().getValue(Attributes.Name.MAIN_CLASS);
      if (mainClass == null) {
        throw new IllegalArgumentException(
            "`Main-Class:` attribute for an application main class not defined in the input JAR's "
                + "manifest (`META-INF/MANIFEST.MF` in the JAR).");
      }
      String classpath = APP_ROOT + "/explodedJar:" + APP_ROOT + "/dependencies/*";
      return ImmutableList.of("java", "-cp", classpath, mainClass);
    }
  }

  /**
   * Computes the entrypoint for a standard jar in packaged mode.
   *
   * @param jarPath path to jar file
   * @return list of {@link String} representing entrypoint
   * @throws IOException if I/O error occurs when opening the jar file
   */
  static ImmutableList<String> computeEntrypointForPackagedStandard(Path jarPath)
      throws IOException {
    try (JarFile jarFile = new JarFile(jarPath.toFile())) {
      String mainClass =
          jarFile.getManifest().getMainAttributes().getValue(Attributes.Name.MAIN_CLASS);
      if (mainClass == null) {
        throw new IllegalArgumentException(
            "`Main-Class:` attribute for an application main class not defined in the input JAR's "
                + "manifest (`META-INF/MANIFEST.MF` in the JAR).");
      }
      return ImmutableList.of("java", "-jar", APP_ROOT + "/" + jarPath.getFileName().toString());
    }
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

  private static List<FileEntriesLayer> getDependenciesLayers(
      Path jarPath, AbsoluteUnixPath pathOnContainer) throws IOException {
    List<FileEntriesLayer> layers = new ArrayList<>();
    String classPath = null;

    // Get dependencies from Class-Path in the jar's manifest and add a layer each for non-snapshot
    // and snapshot dependencies. If Class-Path is not present in the jar's manifest then skip
    // adding the dependencies layers.
    try (JarFile jarFile = new JarFile(jarPath.toFile())) {
      classPath = jarFile.getManifest().getMainAttributes().getValue(Attributes.Name.CLASS_PATH);
      if (classPath == null) {
        return new ArrayList<>();
      } else {
        Predicate<String> isSnapshot = name -> name.contains("SNAPSHOT");
        List<String> allDependencies = Splitter.onPattern("\\s+").splitToList(classPath.trim());
        List<Path> nonSnapshotDependencies =
            allDependencies
                .stream()
                .filter(isSnapshot.negate())
                .map(Paths::get)
                .collect(Collectors.toList());
        List<Path> snapshotDependencies =
            allDependencies
                .stream()
                .filter(isSnapshot)
                .map(Paths::get)
                .collect(Collectors.toList());
        Path jarParent = jarPath.getParent() == null ? Paths.get("") : jarPath.getParent();
        if (!nonSnapshotDependencies.isEmpty()) {
          FileEntriesLayer.Builder nonSnapshotDependenciesLayerBuilder =
              FileEntriesLayer.builder().setName(DEPENDENCIES);
          nonSnapshotDependencies.forEach(
              path ->
                  nonSnapshotDependenciesLayerBuilder.addEntry(
                      jarParent.resolve(path), pathOnContainer.resolve(path)));
          layers.add(nonSnapshotDependenciesLayerBuilder.build());
        }
        if (!snapshotDependencies.isEmpty()) {
          FileEntriesLayer.Builder snapshotDependenciesLayerBuilder =
              FileEntriesLayer.builder().setName(SNAPSHOT_DEPENDENCIES);
          snapshotDependencies.forEach(
              path ->
                  snapshotDependenciesLayerBuilder.addEntry(
                      jarParent.resolve(path), pathOnContainer.resolve(path)));
          layers.add(snapshotDependenciesLayerBuilder.build());
        }
      }
      return layers;
    }
  }
}
