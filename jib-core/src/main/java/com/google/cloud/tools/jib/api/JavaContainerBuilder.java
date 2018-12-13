/*
 * Copyright 2018 Google LLC.
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

package com.google.cloud.tools.jib.api;

import com.google.cloud.tools.jib.filesystem.AbsoluteUnixPath;
import com.google.cloud.tools.jib.frontend.JavaEntrypointConstructor;
import com.google.cloud.tools.jib.frontend.JavaLayerConfigurations;
import com.google.cloud.tools.jib.frontend.JavaLayerConfigurations.LayerType;
import com.google.cloud.tools.jib.image.ImageReference;
import com.google.cloud.tools.jib.image.InvalidImageReferenceException;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class JavaContainerBuilder {

  private static final AbsoluteUnixPath DEPENDENCIES_PATH =
      AbsoluteUnixPath
          .get("/app")
          .resolve(JavaEntrypointConstructor.DEFAULT_RELATIVE_DEPENDENCIES_PATH_ON_IMAGE);
  private static final AbsoluteUnixPath CLASSES_PATH =
      AbsoluteUnixPath
          .get("/app")
          .resolve(JavaEntrypointConstructor.DEFAULT_RELATIVE_CLASSES_PATH_ON_IMAGE);
  private static final AbsoluteUnixPath RESOURCES_PATH =
      AbsoluteUnixPath
          .get("/app")
          .resolve(JavaEntrypointConstructor.DEFAULT_RELATIVE_RESOURCES_PATH_ON_IMAGE);
  private static final AbsoluteUnixPath OTHERS_PATH =
      AbsoluteUnixPath.get("/app").resolve("/other");

  public static JavaContainerBuilder fromDistroless() throws InvalidImageReferenceException {
    return from(RegistryImage.named("https://gcr.io/distroless/java"));
  }

  public static JavaContainerBuilder from(String baseImageReference)
      throws InvalidImageReferenceException {
    return from(RegistryImage.named(baseImageReference));
  }

  public static JavaContainerBuilder from(ImageReference baseImageReference) {
    return from(RegistryImage.named(baseImageReference));
  }

  public static JavaContainerBuilder from(RegistryImage baseImageReference) {
    return new JavaContainerBuilder(Jib.from(baseImageReference));
  }

  private JibContainerBuilder jibContainerBuilder;
  private JavaLayerConfigurations.Builder layerConfigurationsBuilder;
  private List<String> classpath;
  private List<String> jvmFlags;
  private String mainClass;

  private JavaContainerBuilder(JibContainerBuilder jibContainerBuilder) {
    this.jibContainerBuilder = jibContainerBuilder;
    layerConfigurationsBuilder = JavaLayerConfigurations.builder();
    classpath = new ArrayList<>();
    jvmFlags = new ArrayList<>();
  }

  public JavaContainerBuilder addDependencies(List<Path> dependencyFiles) {
    for (Path dependencyFile : dependencyFiles) {
      if (Files.exists(dependencyFile)) {
        boolean isSnapshot = dependencyFile.getFileName().toString().contains("SNAPSHOT");
        LayerType layerType = isSnapshot ? LayerType.SNAPSHOT_DEPENDENCIES : LayerType.DEPENDENCIES;
        layerConfigurationsBuilder.addFile(layerType, dependencyFile, DEPENDENCIES_PATH);
      }
    }
    if (!classpath.contains(DEPENDENCIES_PATH.resolve("*").toString())) {
      classpath.add(DEPENDENCIES_PATH.resolve("*").toString());
    }
    return this;
  }

  public JavaContainerBuilder addResources(Path resourceFilesDirectory) throws IOException {
    if (Files.exists(resourceFilesDirectory)) {
      layerConfigurationsBuilder.addDirectoryContents(
          LayerType.RESOURCES, resourceFilesDirectory, path -> true, RESOURCES_PATH);
    }
    if (!classpath.contains(RESOURCES_PATH.toString())) {
      classpath.add(RESOURCES_PATH.toString());
    }
    return this;
  }

  public JavaContainerBuilder addClasses(Path classFilesDirectory) throws IOException {
    if (Files.exists(classFilesDirectory)) {
      layerConfigurationsBuilder.addDirectoryContents(
          LayerType.CLASSES, classFilesDirectory, path -> true, CLASSES_PATH);
    }
    if (!classpath.contains(CLASSES_PATH.toString())) {
      classpath.add(CLASSES_PATH.toString());
    }
    return this;
  }

  public JavaContainerBuilder addToClasspath(List<Path> otherFiles) {
    for (Path otherFile : otherFiles) {
      if (Files.exists(otherFile)) {
        layerConfigurationsBuilder.addFile(LayerType.EXTRA_FILES, otherFile, OTHERS_PATH);
      }
    }
    if (!classpath.contains(OTHERS_PATH.toString())) {
      classpath.add(OTHERS_PATH.toString());
    }
    return this;
  }

  public JavaContainerBuilder setJvmFlags(List<String> jvmFlags) {
    this.jvmFlags = ImmutableList.copyOf(jvmFlags);
    return this;
  }

  public JavaContainerBuilder setJvmFlags(String... jvmFlags) {
    this.jvmFlags = ImmutableList.copyOf(jvmFlags);
    return this;
  }

  public JavaContainerBuilder setMainClass(String mainClass) {
    this.mainClass = mainClass;
    return this;
  }

  public JibContainerBuilder toContainerBuilder() {
    jibContainerBuilder.setEntrypoint(
        JavaEntrypointConstructor.makeEntrypoint(classpath, jvmFlags, mainClass));
    jibContainerBuilder.setLayers(layerConfigurationsBuilder.build().getLayerConfigurations());
    return jibContainerBuilder;
  }
}
