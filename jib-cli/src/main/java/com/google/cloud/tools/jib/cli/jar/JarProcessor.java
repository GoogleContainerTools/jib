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

import com.google.cloud.tools.jib.api.JavaContainerBuilder;
import com.google.cloud.tools.jib.api.JavaContainerBuilder.LayerType;
import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer;
import com.google.cloud.tools.jib.api.buildplan.RelativeUnixPath;
import com.google.cloud.tools.jib.filesystem.TempDirectoryProvider;
import com.google.cloud.tools.jib.plugins.common.ZipUtil;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Process jar file contents and create layers. */
public class JarProcessor {

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
   * Explode jar into three layers (classes, resources, dependencies) on container. This method doesn't maintain the source directory
   * structure when adding file entries to the layers.
   *
   * @param jarPath path to jar file.
   * @return list of {@link FileEntriesLayer}.
   * @throws IOException if I/O error occurs when opening the file.
   */
  public static List<FileEntriesLayer> explodeStandardJar(Path jarPath) throws IOException {
    TempDirectoryProvider tempDirectoryProvider = new TempDirectoryProvider();
    Path tempDirectoryPath = tempDirectoryProvider.newDirectory();
    ZipUtil.unzip(jarPath, tempDirectoryPath);

    // Get class files and resource files.
    List<Path> classFiles = new ArrayList<>();
    List<Path> resourceFiles = new ArrayList<>();
    try (Stream<Path> fileStream = Files.walk(tempDirectoryPath).filter(Files::isRegularFile)) {
      List<Path> allFilePathsInJar = fileStream.collect(Collectors.toList());
      for (Path path : allFilePathsInJar) {
        if (path.toString().endsWith(".class")) {
          classFiles.add(path);
        } else {
          resourceFiles.add(path);
        }
      }
    }

    // Get dependencies from class-path.
    List<Path> dependencyFiles = new ArrayList<>();
    JarFile jarFile = new JarFile(jarPath.toFile());
    String classPath = jarFile.getManifest().getMainAttributes().getValue("Class-Path");
    if (classPath != null) {
      for (String dependency : Splitter.on(" ").split(classPath)) {
        Path depPath = Paths.get(dependency);
        dependencyFiles.add(depPath);
      }
    } else {
      throw new IllegalStateException("Class path is not specified.");
    }

    // Create Layer for each type or file and add them to list of FileEntriesLayer.
    List<FileEntriesLayer> layers = new ArrayList<>();
    ImmutableMap<LayerType, FileEntriesLayer.Builder> layerMap =
        ImmutableMap.of(
            LayerType.CLASSES, FileEntriesLayer.builder(),
            LayerType.RESOURCES, FileEntriesLayer.builder(),
            LayerType.DEPENDENCIES, FileEntriesLayer.builder());
    AbsoluteUnixPath appRoot = AbsoluteUnixPath.get("/app");
    classFiles.forEach(
        path ->
            JavaContainerBuilder.addFileToLayer(
                layerMap,
                LayerType.CLASSES,
                path,
                appRoot.resolve(RelativeUnixPath.get("classes")).resolve(path.getFileName())));
    resourceFiles.forEach(
        path ->
            JavaContainerBuilder.addFileToLayer(
                layerMap,
                LayerType.RESOURCES,
                path,
                appRoot.resolve(RelativeUnixPath.get("resources")).resolve(path.getFileName())));
    dependencyFiles.forEach(
        path ->
            JavaContainerBuilder.addFileToLayer(
                layerMap,
                LayerType.DEPENDENCIES,
                path,
                appRoot.resolve(RelativeUnixPath.get("dependencies")).resolve(path)));
    layerMap.forEach((type, builder) -> layers.add(builder.setName(type.getName()).build()));

    return layers;
  }
}
