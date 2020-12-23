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
import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer;
import com.google.cloud.tools.jib.plugins.common.ZipUtil;
import com.google.common.base.Predicates;
import com.google.common.base.Verify;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import javax.annotation.Nullable;

public class SpringBootExplodedModeProcessor implements JarModeProcessor {

  @Nullable private static Path tempDirectoryPath = null;

  @Override
  public List<FileEntriesLayer> createLayers(Path jarPath) throws IOException {
    try (JarFile jarFile = new JarFile(jarPath.toFile())) {
      ZipEntry layerIndex = jarFile.getEntry("BOOT-INF/layers.idx");

      if (tempDirectoryPath == null) {
        return new ArrayList<>();
      }

      Path localExplodedJarRoot = tempDirectoryPath;
      ZipUtil.unzip(jarPath, localExplodedJarRoot);
      if (layerIndex != null) {
        return createLayersForLayeredSpringBootJar(localExplodedJarRoot);
      }

      // Non-snapshot layer
      Predicate<Path> isFile = Files::isRegularFile;
      Predicate<Path> isInBootInfLib =
          path -> path.startsWith(localExplodedJarRoot.resolve("BOOT-INF").resolve("lib"));
      Predicate<Path> isSnapshot = path -> path.getFileName().toString().contains("SNAPSHOT");
      Predicate<Path> isInBootInfLibAndIsNotSnapshot = isInBootInfLib.and(isSnapshot.negate());
      Predicate<Path> nonSnapshotPredicate = isFile.and(isInBootInfLibAndIsNotSnapshot);
      FileEntriesLayer nonSnapshotLayer =
          JarProcessorHelper.addDirectoryContentsToLayer(
              JarProcessorHelper.DEPENDENCIES,
              localExplodedJarRoot,
              nonSnapshotPredicate,
              JarProcessorHelper.APP_ROOT);

      // Snapshot layer
      Predicate<Path> isInBootInfLibAndIsSnapshot = isInBootInfLib.and(isSnapshot);
      Predicate<Path> snapshotPredicate = isFile.and(isInBootInfLibAndIsSnapshot);
      FileEntriesLayer snapshotLayer =
          JarProcessorHelper.addDirectoryContentsToLayer(
              JarProcessorHelper.SNAPSHOT_DEPENDENCIES,
              localExplodedJarRoot,
              snapshotPredicate,
              JarProcessorHelper.APP_ROOT);

      // Spring-boot-loader layer.
      Predicate<Path> isLoader = path -> path.startsWith(localExplodedJarRoot.resolve("org"));
      Predicate<Path> loaderPredicate = isFile.and(isLoader);
      FileEntriesLayer loaderLayer =
          JarProcessorHelper.addDirectoryContentsToLayer(
              "spring-boot-loader",
              localExplodedJarRoot,
              loaderPredicate,
              JarProcessorHelper.APP_ROOT);

      // Classes layer.
      Predicate<Path> isClass = path -> path.getFileName().toString().endsWith(".class");
      Predicate<Path> isInBootInfClasses =
          path -> path.startsWith(localExplodedJarRoot.resolve("BOOT-INF").resolve("classes"));
      Predicate<Path> classesPredicate = isInBootInfClasses.and(isClass);
      FileEntriesLayer classesLayer =
          JarProcessorHelper.addDirectoryContentsToLayer(
              JarProcessorHelper.CLASSES,
              localExplodedJarRoot,
              classesPredicate,
              JarProcessorHelper.APP_ROOT);

      // Resources layer.
      Predicate<Path> isInMetaInf =
          path -> path.startsWith(localExplodedJarRoot.resolve("META-INF"));
      Predicate<Path> isResource = isInMetaInf.or(isInBootInfClasses.and(isClass.negate()));
      Predicate<Path> resourcesPredicate = isFile.and(isResource);
      FileEntriesLayer resourcesLayer =
          JarProcessorHelper.addDirectoryContentsToLayer(
              JarProcessorHelper.RESOURCES,
              localExplodedJarRoot,
              resourcesPredicate,
              JarProcessorHelper.APP_ROOT);

      return Arrays.asList(
          nonSnapshotLayer, loaderLayer, snapshotLayer, resourcesLayer, classesLayer);
    }
  }

  @Override
  public ImmutableList<String> computeEntrypoint(Path jarPath) {
    return ImmutableList.of(
        "java",
        "-cp",
        JarProcessorHelper.APP_ROOT.toString(),
        "org.springframework.boot.loader.JarLauncher");
  }

  /**
   * Creates layers as specified by the layers.idx file (located in the BOOT-INF/ directory of the
   * JAR).
   *
   * @param localExplodedJarRoot Path to exploded JAR content root
   * @return list of {@link FileEntriesLayer}
   * @throws IOException when an IO error occurs
   */
  static List<FileEntriesLayer> createLayersForLayeredSpringBootJar(Path localExplodedJarRoot)
      throws IOException {
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
            JarProcessorHelper.addDirectoryContentsToLayer(
                layerName, localExplodedJarRoot, belongsToThisLayer, JarProcessorHelper.APP_ROOT));
      }
    }
    return layers;
  }

  static Predicate<Path> isInListedDirectoryOrIsSameFile(
      List<String> layerContents, Path localExplodedJarRoot) {
    Predicate<Path> predicate = Predicates.alwaysFalse();
    for (String pathName : layerContents) {
      if (pathName.endsWith("/")) {
        predicate = predicate.or(path -> path.startsWith(localExplodedJarRoot.resolve(pathName)));
      } else {
        predicate = predicate.or(path -> path.equals(localExplodedJarRoot.resolve(pathName)));
      }
    }
    return predicate.and(Files::isRegularFile);
  }

  public void setTempDirectoryPath(Path tempDirPath) {
    tempDirectoryPath = tempDirPath;
  }
}
