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

import com.google.cloud.tools.jib.ProjectInfo;
import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer;
import com.google.cloud.tools.jib.filesystem.DirectoryWalker;
import com.google.cloud.tools.jib.plugins.common.ZipUtil;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Predicates;
import com.google.common.base.Splitter;
import com.google.common.base.Verify;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
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
    SPRING_BOOT
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
            CLASSES, localExplodedJarRoot, isClassFile, APP_ROOT.resolve("explodedJar"));
    FileEntriesLayer resourcesLayer =
        addDirectoryContentsToLayer(
            RESOURCES, localExplodedJarRoot, isResourceFile, APP_ROOT.resolve("explodedJar"));

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

  /**
   * Creates layers as specified in BOOT-INF/layers.idx (if present) or for dependencies,
   * spring-boot-loader, snapshot dependencies, resource and classes for a spring boot fat jar.
   *
   * @param jarPath path to jar file
   * @param tempDirPath path to temporary jib local directory
   * @return list of {@link FileEntriesLayer}
   * @throws IOException if I/O error occurs when opening the jar file or if temporary directory
   *     provided doesn't exist
   */
  static List<FileEntriesLayer> createLayersForExplodedSpringBootFat(Path jarPath, Path tempDirPath)
      throws IOException {

    try (JarFile jarFile = new JarFile(jarPath.toFile())) {
      ZipEntry layerIndex = jarFile.getEntry("BOOT-INF/layers.idx");
      Path localExplodedJarRoot = tempDirPath;
      ZipUtil.unzip(jarPath, localExplodedJarRoot);
      if (layerIndex != null) {
        return createLayersForLayeredSpringBootJar(localExplodedJarRoot);
      }

      // Non-snapshot layer
      Predicate<Path> isInBootInfLib =
          path -> path.startsWith(localExplodedJarRoot.resolve("BOOT-INF").resolve("lib"));
      Predicate<Path> isSnapshot = path -> path.getFileName().toString().contains("SNAPSHOT");
      Predicate<Path> nonSnapshotPredicate = isInBootInfLib.and(isSnapshot.negate());
      FileEntriesLayer nonSnapshotLayer =
          addDirectoryContentsToLayer(
              DEPENDENCIES, localExplodedJarRoot, nonSnapshotPredicate, APP_ROOT);

      // Snapshot layer
      Predicate<Path> snapshotPredicate = isInBootInfLib.and(isSnapshot);
      FileEntriesLayer snapshotLayer =
          addDirectoryContentsToLayer(
              SNAPSHOT_DEPENDENCIES, localExplodedJarRoot, snapshotPredicate, APP_ROOT);

      // Spring-boot-loader layer.
      Predicate<Path> isLoader = path -> path.startsWith(localExplodedJarRoot.resolve("org"));
      FileEntriesLayer loaderLayer =
          addDirectoryContentsToLayer(
              "spring-boot-loader", localExplodedJarRoot, isLoader, APP_ROOT);

      // Classes layer.
      Predicate<Path> isClass = path -> path.getFileName().toString().endsWith(".class");
      Predicate<Path> isInBootInfClasses =
          path -> path.startsWith(localExplodedJarRoot.resolve("BOOT-INF").resolve("classes"));
      Predicate<Path> finalPredicateClasses = isInBootInfClasses.and(isClass);
      FileEntriesLayer classesLayer =
          addDirectoryContentsToLayer(
              CLASSES, localExplodedJarRoot, finalPredicateClasses, APP_ROOT);

      // Resources layer.
      Predicate<Path> isInMetaInf =
          path -> path.startsWith(localExplodedJarRoot.resolve("META-INF"));
      Predicate<Path> isFile = path -> !path.toFile().isDirectory();
      Predicate<Path> finalPredicateResources =
          isFile.and(isInMetaInf.or(isInBootInfClasses.and(isClass.negate())));
      FileEntriesLayer resourcesLayer =
          addDirectoryContentsToLayer(
              RESOURCES, localExplodedJarRoot, finalPredicateResources, APP_ROOT);

      return Arrays.asList(
          nonSnapshotLayer, loaderLayer, snapshotLayer, resourcesLayer, classesLayer);
    }
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

  /**
   * Computes the entrypoint for a spring boot fat jar in exploded mode.
   *
   * @return list of {@link String} representing entrypoint
   */
  static ImmutableList<String> computeEntrypointForExplodedSpringBoot() {
    return ImmutableList.of(
        "java", "-cp", APP_ROOT.toString(), "org.springframework.boot.loader.JarLauncher");
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
      }
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
          allDependencies.stream().filter(isSnapshot).map(Paths::get).collect(Collectors.toList());
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

  /**
   * Creates layers as specified by the layers.idx file (located in the BOOT-INF/ directory of the
   * JAR).
   *
   * @param localExplodedJarRoot Path to exploded JAR content root
   * @return list of {@link FileEntriesLayer}
   * @throws IOException when an IO error occurs
   */
  private static List<FileEntriesLayer> createLayersForLayeredSpringBootJar(
      Path localExplodedJarRoot) throws IOException {
    Path layerIndexPath = localExplodedJarRoot.resolve("BOOT-INF").resolve("layers.idx");
    Pattern layerNamePattern = Pattern.compile("- \"(.*)\":");
    Pattern layerEntryPattern = Pattern.compile("  - \"(.*)\"");
    Map<String, List<String>> layersMap = new LinkedHashMap<>();
    List<String> layerEntries = null;
    for (String line : Files.readAllLines(layerIndexPath, StandardCharsets.UTF_8)) {
      Matcher layerMatcher = layerNamePattern.matcher(line);
      Matcher entryMatcher = layerEntryPattern.matcher(line);
      if (layerMatcher.matches()) {
        layerEntries = new ArrayList<>();
        String layerName = layerMatcher.group(1);
        layersMap.put(layerName, layerEntries);
      } else if (entryMatcher.matches()) {
        Verify.verifyNotNull(layerEntries).add(entryMatcher.group(1));
      } else {
        throw new IllegalStateException(
            "Unable to parse BOOT-INF/layers.idx file in the JAR. Please check the format of "
                + "layers.idx. If this is a Jib CLI bug, file an issue at "
                + ProjectInfo.GITHUB_NEW_ISSUE_URL);
      }
    }

    // If the layers.idx file looks like this, for example:
    // - "dependencies":
    //   - "BOOT-INF/lib/dependency1.jar"
    // - "application":
    //   - "BOOT-INF/classes/"
    //   - "META-INF/"
    // The predicate for the "dependencies" layer will be true if `path` is equal to
    // `BOOT-INF/lib/dependency1.jar` and the predicate for the "spring-boot-loader" layer will be
    // true if `path` is in either 'BOOT-INF/classes/` or `META-INF/`.
    List<FileEntriesLayer> layers = new ArrayList<>();
    for (Map.Entry<String, List<String>> entry : layersMap.entrySet()) {
      String layerName = entry.getKey();
      List<String> contents = entry.getValue();
      if (!contents.isEmpty()) {
        Predicate<Path> belongsToThisLayer =
            isInListedDirectoryOrIsSameFile(contents, localExplodedJarRoot);
        layers.add(
            addDirectoryContentsToLayer(
                layerName, localExplodedJarRoot, belongsToThisLayer, APP_ROOT));
      }
    }
    return layers;
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
      String layerName,
      Path sourceRoot,
      Predicate<Path> pathFilter,
      AbsoluteUnixPath basePathInContainer)
      throws IOException {
    FileEntriesLayer.Builder builder = FileEntriesLayer.builder().setName(layerName);
    HashSet<Path> addedPaths = new HashSet<>();
    new DirectoryWalker(sourceRoot)
        .filterRoot()
        .filter(path -> pathFilter.test(path))
        .walk(
            path -> {
              Path relativePath = sourceRoot.relativize(path);
              int nameCount = relativePath.getNameCount();

              // Add the parent directories and the path itself
              for (int i = 1; i <= nameCount; i++) {
                Path subPath = relativePath.subpath(0, i);
                if (!addedPaths.contains(subPath)) {
                  builder.addEntry(
                      sourceRoot.resolve(subPath), basePathInContainer.resolve(subPath));
                  addedPaths.add(subPath);
                }
              }
            });
    return builder.build();
  }

  private static Predicate<Path> isInListedDirectoryOrIsSameFile(
      List<String> layerContents, Path localExplodedJarRoot) {
    Predicate<Path> predicate = Predicates.alwaysFalse();
    for (String pathName : layerContents) {
      if (pathName.endsWith("/")) {
        predicate = predicate.or(path -> path.startsWith(localExplodedJarRoot.resolve(pathName)));
      } else {
        predicate = predicate.or(path -> path.equals(localExplodedJarRoot.resolve(pathName)));
      }
    }
    return predicate;
  }
}
