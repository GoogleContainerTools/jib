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
import com.google.cloud.tools.jib.cli.ArtifactLayers;
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

public class JarLayers {

  static final AbsoluteUnixPath APP_ROOT = AbsoluteUnixPath.get("/app");
  static final String JAR = "jar";

  static List<FileEntriesLayer> getDependenciesLayers(Path jarPath, ProcessingMode mode)
      throws IOException {
    // Get dependencies from Class-Path in the jar's manifest and add a layer each for non-snapshot
    // and snapshot dependencies. If Class-Path is not present in the JAR's manifest then skip
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
            FileEntriesLayer.builder().setName(ArtifactLayers.DEPENDENCIES);
        nonSnapshots.forEach(
            path ->
                addDependency(
                    nonSnapshotLayer,
                    jarParent.resolve(path),
                    mode.equals(ProcessingMode.packaged)
                        ? APP_ROOT.resolve(path)
                        : APP_ROOT
                            .resolve(ArtifactLayers.DEPENDENCIES)
                            .resolve(path.getFileName())));
        layers.add(nonSnapshotLayer.build());
      }
      if (!snapshots.isEmpty()) {
        FileEntriesLayer.Builder snapshotLayer =
            FileEntriesLayer.builder().setName(ArtifactLayers.SNAPSHOT_DEPENDENCIES);
        snapshots.forEach(
            path ->
                addDependency(
                    snapshotLayer,
                    jarParent.resolve(path),
                    mode.equals(ProcessingMode.packaged)
                        ? APP_ROOT.resolve(path)
                        : APP_ROOT
                            .resolve(ArtifactLayers.DEPENDENCIES)
                            .resolve(path.getFileName())));
        layers.add(snapshotLayer.build());
      }
      return layers;
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
}
