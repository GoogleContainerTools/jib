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
import com.google.common.io.MoreFiles;
import com.google.common.io.RecursiveDeleteOption;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;
import java.util.jar.Attributes;
import java.util.jar.JarFile;

class StandardExplodedProcessor implements JarProcessor {

  private final Path jarPath;
  private final Path targetExplodedJarRoot;
  private final Integer jarJavaVersion;

  /**
   * Constructor for {@link StandardExplodedProcessor}.
   *
   * @param jarPath path to jar file
   * @param targetExplodedJarRoot path to exploded-jar root
   * @param jarJavaVersion jar java version
   */
  StandardExplodedProcessor(Path jarPath, Path targetExplodedJarRoot, Integer jarJavaVersion) {
    this.jarPath = jarPath;
    this.targetExplodedJarRoot = targetExplodedJarRoot;
    this.jarJavaVersion = jarJavaVersion;
  }

  @Override
  public List<FileEntriesLayer> createLayers() throws IOException {
    // Clear the exploded-jar root first
    if (Files.exists(targetExplodedJarRoot)) {
      MoreFiles.deleteRecursively(targetExplodedJarRoot, RecursiveDeleteOption.ALLOW_INSECURE);
    }

    // Add dependencies layers.
    List<FileEntriesLayer> layers =
        JarLayers.getDependenciesLayers(jarPath, ProcessingMode.exploded);

    // Determine class and resource files in the directory containing jar contents and create
    // FileEntriesLayer for each type of layer (classes or resources).
    ZipUtil.unzip(jarPath, targetExplodedJarRoot, true);
    Predicate<Path> isClassFile = path -> path.getFileName().toString().endsWith(".class");
    Predicate<Path> isResourceFile = isClassFile.negate().and(Files::isRegularFile);
    FileEntriesLayer classesLayer =
        JarLayers.getDirectoryContentsAsLayer(
            JarLayers.CLASSES,
            targetExplodedJarRoot,
            isClassFile,
            JarLayers.APP_ROOT.resolve("explodedJar"));
    FileEntriesLayer resourcesLayer =
        JarLayers.getDirectoryContentsAsLayer(
            JarLayers.RESOURCES,
            targetExplodedJarRoot,
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
  public ImmutableList<String> computeEntrypoint(List<String> jvmFlags) throws IOException {
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
      ImmutableList.Builder<String> entrypoint = ImmutableList.builder();
      entrypoint.add("java");
      entrypoint.addAll(jvmFlags);
      entrypoint.add("-cp");
      entrypoint.add(classpath);
      entrypoint.add(mainClass);
      return entrypoint.build();
    }
  }

  @Override
  public Integer getJarJavaVersion() {
    return jarJavaVersion;
  }
}
