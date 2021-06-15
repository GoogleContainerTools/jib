/*
 * Copyright 2021 Google LLC.
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

package com.google.cloud.tools.jib.cli.war;

import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer;
import com.google.cloud.tools.jib.cli.ArtifactLayers;
import com.google.cloud.tools.jib.cli.ArtifactProcessor;
import com.google.cloud.tools.jib.plugins.common.ZipUtil;
import com.google.common.collect.ImmutableList;
import com.google.common.io.MoreFiles;
import com.google.common.io.RecursiveDeleteOption;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public class StandardWarExplodedProcessor implements ArtifactProcessor {

  private final Path warPath;
  private final Path targetExplodedWarRoot;
  private final AbsoluteUnixPath appRoot;

  /**
   * Constructor for {@link StandardWarExplodedProcessor}.
   *
   * @param warPath path to WAR file
   * @param targetExplodedWarRoot path to exploded-war root
   * @param appRoot the absolute path of the app on the container
   */
  public StandardWarExplodedProcessor(
      Path warPath, Path targetExplodedWarRoot, AbsoluteUnixPath appRoot) {
    this.warPath = warPath;
    this.targetExplodedWarRoot = targetExplodedWarRoot;
    this.appRoot = appRoot;
  }

  @Override
  public List<FileEntriesLayer> createLayers() throws IOException {
    // Clear the exploded-artifact root first
    if (Files.exists(targetExplodedWarRoot)) {
      MoreFiles.deleteRecursively(targetExplodedWarRoot, RecursiveDeleteOption.ALLOW_INSECURE);
    }

    ZipUtil.unzip(warPath, targetExplodedWarRoot, true);
    Predicate<Path> isFile = Files::isRegularFile;
    Predicate<Path> isInWebInfLib =
        path -> path.startsWith(targetExplodedWarRoot.resolve("WEB-INF").resolve("lib"));
    Predicate<Path> isSnapshot = path -> path.getFileName().toString().contains("SNAPSHOT");

    // Non-snapshot layer
    Predicate<Path> isInWebInfLibAndIsNotSnapshot = isInWebInfLib.and(isSnapshot.negate());
    FileEntriesLayer nonSnapshotLayer =
        ArtifactLayers.getDirectoryContentsAsLayer(
            ArtifactLayers.DEPENDENCIES,
            targetExplodedWarRoot,
            isFile.and(isInWebInfLibAndIsNotSnapshot),
            appRoot);

    // Snapshot layer
    Predicate<Path> isInWebInfLibAndIsSnapshot = isInWebInfLib.and(isSnapshot);
    FileEntriesLayer snapshotLayer =
        ArtifactLayers.getDirectoryContentsAsLayer(
            ArtifactLayers.SNAPSHOT_DEPENDENCIES,
            targetExplodedWarRoot,
            isFile.and(isInWebInfLibAndIsSnapshot),
            appRoot);

    // Classes layer.
    Predicate<Path> isClass = path -> path.getFileName().toString().endsWith(".class");
    Predicate<Path> isInWebInfClasses =
        path -> path.startsWith(targetExplodedWarRoot.resolve("WEB-INF").resolve("classes"));
    Predicate<Path> classesPredicate = isInWebInfClasses.and(isClass);
    FileEntriesLayer classesLayer =
        ArtifactLayers.getDirectoryContentsAsLayer(
            ArtifactLayers.CLASSES, targetExplodedWarRoot, classesPredicate, appRoot);

    // Resources layer.
    Predicate<Path> resourcesPredicate = isInWebInfLib.or(isClass).negate();
    FileEntriesLayer resourcesLayer =
        ArtifactLayers.getDirectoryContentsAsLayer(
            ArtifactLayers.RESOURCES,
            targetExplodedWarRoot,
            isFile.and(resourcesPredicate),
            appRoot);

    ArrayList<FileEntriesLayer> layers = new ArrayList<>();
    if (!nonSnapshotLayer.getEntries().isEmpty()) {
      layers.add(nonSnapshotLayer);
    }
    if (!snapshotLayer.getEntries().isEmpty()) {
      layers.add(snapshotLayer);
    }
    if (!resourcesLayer.getEntries().isEmpty()) {
      layers.add(resourcesLayer);
    }
    if (!classesLayer.getEntries().isEmpty()) {
      layers.add(classesLayer);
    }

    return layers;
  }

  @Override
  public ImmutableList<String> computeEntrypoint(List<String> jvmFlags) {
    throw new UnsupportedOperationException("Computing the entrypoint is currently not supported.");
  }

  @Override
  public Integer getJavaVersion() {
    throw new UnsupportedOperationException(
        "Getting the java version from a WAR file is currently not supported.");
  }
}
