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

import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer;
import com.google.cloud.tools.jib.plugins.common.ZipUtil;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import javax.annotation.Nullable;

public class StandardExplodedProcessor implements JarProcessor {

  @Nullable private static Path tempDirectoryPath = null;
  @Nullable private static Path jarPath = null;

  @Override
  public List<FileEntriesLayer> createLayers() throws IOException {
    if (tempDirectoryPath == null || jarPath == null) {
      return new ArrayList<>();
    }
    // Add dependencies layers.
    List<FileEntriesLayer> layers =
        JarLayers.getDependenciesLayers(jarPath, ProcessingMode.exploded);

    // Determine class and resource files in the directory containing jar contents and create
    // FileEntriesLayer for each type of layer (classes or resources).
    Path localExplodedJarRoot = tempDirectoryPath;
    ZipUtil.unzip(jarPath, localExplodedJarRoot);
    Predicate<Path> isClassFile = path -> path.getFileName().toString().endsWith(".class");
    Predicate<Path> isResourceFile = isClassFile.negate().and(Files::isRegularFile);
    FileEntriesLayer classesLayer =
        JarLayers.getDirectoryContentsAsLayer(
            JarLayers.CLASSES,
            localExplodedJarRoot,
            isClassFile,
            JarLayers.APP_ROOT.resolve("explodedJar"));
    FileEntriesLayer resourcesLayer =
        JarLayers.getDirectoryContentsAsLayer(
            JarLayers.RESOURCES,
            localExplodedJarRoot,
            isResourceFile,
            JarLayers.APP_ROOT.resolve("explodedJar"));

    if (!resourcesLayer.getEntries().isEmpty()) {
      layers.add(resourcesLayer);
    }
    if (!classesLayer.getEntries().isEmpty()) {
      layers.add(classesLayer);
    }
    return layers;
  }

  @Override
  public ImmutableList<String> computeEntrypoint() throws IOException {
    if (jarPath == null) {
      return ImmutableList.of();
    }
    try (JarFile jarFile = new JarFile(jarPath.toFile())) {
      String mainClass =
          jarFile.getManifest().getMainAttributes().getValue(Attributes.Name.MAIN_CLASS);
      if (mainClass == null) {
        throw new IllegalArgumentException(
            "`Main-Class:` attribute for an application main class not defined in the input JAR's "
                + "manifest (`META-INF/MANIFEST.MF` in the JAR).");
      }
      String classpath =
          JarLayers.APP_ROOT + "/explodedJar:" + JarLayers.APP_ROOT + "/dependencies/*";
      return ImmutableList.of("java", "-cp", classpath, mainClass);
    }
  }

  @Nullable
  public Path getTempDirectoryPath() {
    return tempDirectoryPath;
  }

  @Nullable
  public Path getJarPath() {
    return jarPath;
  }

  public void setTempDirectoryPath(Path tempDirPath) {
    tempDirectoryPath = tempDirPath;
  }

  public void setJarPath(Path path) {
    jarPath = path;
  }
}
