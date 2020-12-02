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
import com.google.cloud.tools.jib.filesystem.DirectoryWalker;
import com.google.cloud.tools.jib.plugins.common.ZipUtil;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.base.Verify;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;

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
    List<FileEntriesLayer> layers = getDependenciesLayers(jarPath, ProcessingMode.exploded);

    // Determine class and resource files in the directory containing jar contents and create
    // FileEntriesLayer for each type of layer (classes or resources), while maintaining the
    // file's original project structure.
    Path localExplodedJarRoot = tempDirPath;
    ZipUtil.unzip(jarPath, localExplodedJarRoot);
    Predicate<Path> isClassFile = path -> path.getFileName().toString().endsWith(".class");
    Predicate<Path> isResourceFile = isClassFile.negate();
    FileEntriesLayer classesLayer =
        addDirectoryContentsToLayer(
            localExplodedJarRoot,
            isClassFile,
            APP_ROOT.resolve("explodedJar"),
            FileEntriesLayer.builder().setName(CLASSES));
    FileEntriesLayer resourcesLayer =
        addDirectoryContentsToLayer(
            localExplodedJarRoot,
            isResourceFile,
            APP_ROOT.resolve("explodedJar"),
            FileEntriesLayer.builder().setName(RESOURCES));

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
    List<FileEntriesLayer> layers = getDependenciesLayers(jarPath, ProcessingMode.packaged);

    // Add layer for jar.
    FileEntriesLayer jarLayer =
        FileEntriesLayer.builder()
            .setName(JAR)
            .addEntry(jarPath, APP_ROOT.resolve(jarPath.getFileName()))
            .build();
    layers.add(jarLayer);

    return layers;
  }

  static List<FileEntriesLayer> createLayersForExplodedSpringBootFat(Path jarPath, Path tempDirPath)
      throws IOException {

    ZipEntry layerIndex = null;
    try (JarFile jarFile = new JarFile(jarPath.toFile())) {
      layerIndex = jarFile.getEntry("BOOT-INF/layers.idx");
    }
    Path localExplodedJarRoot = tempDirPath;
    ZipUtil.unzip(jarPath, localExplodedJarRoot);
    if (layerIndex != null) {
      Path layerIndexPath = localExplodedJarRoot.resolve(Paths.get("BOOT-INF/layers.idx"));
      try (Stream stream = Files.lines(layerIndexPath)) {
        Pattern layerNamePattern = Pattern.compile("-\\s(.*):");
        Pattern fileNamePattern = Pattern.compile("\\s\\s-(.*)");
        List<String> contents = null;
        List<String> layerNames = new ArrayList<>();
        Map<String, List<String>> layers = new LinkedHashMap<>();
        List<String> lines = (List<String>) stream.collect(Collectors.toList());
        for (String line : lines) {
          String cleanedUpLine = line.replace("\"", "");
          Matcher layerMatcher = layerNamePattern.matcher(cleanedUpLine);
          Matcher fileNameMatcher = fileNamePattern.matcher(cleanedUpLine);
          if (layerMatcher.matches()) {
            contents = new ArrayList<>();
            String layerName = layerMatcher.group(1);
            layerNames.add(layerName);
            layers.put(layerName, contents);
          } else if (fileNameMatcher.matches()) {
            Verify.verifyNotNull(contents).add(fileNameMatcher.group(1).replace(" ", ""));
          } else {
            throw new IllegalStateException(
                "Unable to parse layers.idx file. Please check the format of layers.idx");
          }
        }
        ArrayList<FileEntriesLayer> allLayers = new ArrayList<>();
        for (String layerKey : layerNames) {
          FileEntriesLayer.Builder layer = FileEntriesLayer.builder().setName(layerKey);
          for (String value : layers.getOrDefault(layerKey, new ArrayList<>())) {
            if (value.endsWith("/")) {
              Predicate<Path> directoryPredicate =
                  path -> path.getParent().startsWith(localExplodedJarRoot.resolve(value));
              addDirectoryContentsToLayer(
                  localExplodedJarRoot, directoryPredicate, APP_ROOT.resolve(layerKey), layer);
            } else {
              Predicate<Path> filePredicate =
                  path -> path.equals(localExplodedJarRoot.resolve(Paths.get(value)));
              addDirectoryContentsToLayer(
                  localExplodedJarRoot, filePredicate, APP_ROOT.resolve(layerKey), layer);
            }
          }
          allLayers.add(layer.build());
        }
        return allLayers;
      }
    }

    // Dependencies -- BOOT-INF/lib and doesn't contain `SNAPSHOT`
    // Snapshot Dependencies -- BOOT-INF/lib and filename contains `SNAPSHOT`
    // Spring Boot Loader -- loader class
    // Resource Layer -- BOOT-INF/classes and META-INF and not .class file
    // Classes Layer -- BOOT-INF/classes and META-INF and .class file

    return new ArrayList<>();
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

  private static List<FileEntriesLayer> getDependenciesLayers(Path jarPath, ProcessingMode mode)
      throws IOException {

    // Get dependencies from Class-Path in the jar's manifest and add a layer each for non-snapshot
    // and snapshot dependencies. If Class-Path is not present in the jar's manifest then skip
    // adding the dependencies layers.
    try (JarFile jarFile = new JarFile(jarPath.toFile())) {
      String classPath =
          jarFile.getManifest().getMainAttributes().getValue(Attributes.Name.CLASS_PATH);
      if (classPath == null) {
        return new ArrayList<>();
      } else {
        List<FileEntriesLayer> layers = new ArrayList<>();
        Path jarParent = jarPath.getParent() == null ? Paths.get("") : jarPath.getParent();
        Predicate<String> isSnapshot = name -> name.contains("SNAPSHOT");
        List<String> allDependencies = Splitter.onPattern("\\s+").splitToList(classPath.trim());
        List<Path> nonSnapshots =
            allDependencies
                .stream()
                .filter(isSnapshot.negate())
                .map(Paths::get)
                .collect(Collectors.toList());
        List<Path> snapshots =
            allDependencies
                .stream()
                .filter(isSnapshot)
                .map(Paths::get)
                .collect(Collectors.toList());
        if (!nonSnapshots.isEmpty()) {
          FileEntriesLayer.Builder nonSnapshotLayer =
              FileEntriesLayer.builder().setName(DEPENDENCIES);
          nonSnapshots.forEach(
              path ->
                  addDependency(
                      nonSnapshotLayer,
                      jarParent.resolve(path),
                      mode.equals(ProcessingMode.packaged)
                          ? APP_ROOT.resolve(path)
                          : APP_ROOT.resolve("dependencies").resolve(path.getFileName())));
          layers.add(nonSnapshotLayer.build());
        }
        if (!snapshots.isEmpty()) {
          FileEntriesLayer.Builder snapshotLayer =
              FileEntriesLayer.builder().setName(SNAPSHOT_DEPENDENCIES);
          snapshots.forEach(
              path ->
                  addDependency(
                      snapshotLayer,
                      jarParent.resolve(path),
                      mode.equals(ProcessingMode.packaged)
                          ? APP_ROOT.resolve(path)
                          : APP_ROOT.resolve("dependencies").resolve(path.getFileName())));
          layers.add(snapshotLayer.build());
        }
        return layers;
      }
    }
  }

  private static void addDependency(
      FileEntriesLayer.Builder layerbuilder, Path fullDepPath, AbsoluteUnixPath pathOnContainer) {
    if (!Files.exists(fullDepPath)) {
      throw new IllegalArgumentException(
          String.format(
              "Dependency required by the JAR (as specified in `Class-Path` in the JAR manifest) doesn't exist: %s",
              fullDepPath));
    }
    layerbuilder.addEntry(fullDepPath, pathOnContainer);
  }

  private static FileEntriesLayer addDirectoryContentsToLayer(
      Path sourceRoot,
      Predicate<Path> pathFilter,
      AbsoluteUnixPath basePathInContainer,
      FileEntriesLayer.Builder builder)
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
    return builder.build();
  }
}
