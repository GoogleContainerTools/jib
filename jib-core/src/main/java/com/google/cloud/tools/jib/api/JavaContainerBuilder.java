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
import java.util.Arrays;
import java.util.List;
import javax.annotation.Nullable;

/** Creates a {@link JavaContainerBuilder} for containerizing Java applications. */
public class JavaContainerBuilder {

  /** Absolute path of dependencies on container. */
  private static final AbsoluteUnixPath DEPENDENCIES_PATH =
      AbsoluteUnixPath.get("/app")
          .resolve(JavaEntrypointConstructor.DEFAULT_RELATIVE_DEPENDENCIES_PATH_ON_IMAGE);

  /** Absolute path of classes on container. */
  private static final AbsoluteUnixPath CLASSES_PATH =
      AbsoluteUnixPath.get("/app")
          .resolve(JavaEntrypointConstructor.DEFAULT_RELATIVE_CLASSES_PATH_ON_IMAGE);

  /** Absolute path of resources on container. */
  private static final AbsoluteUnixPath RESOURCES_PATH =
      AbsoluteUnixPath.get("/app")
          .resolve(JavaEntrypointConstructor.DEFAULT_RELATIVE_RESOURCES_PATH_ON_IMAGE);

  /** Absolute path of additional classpath files on container. */
  private static final AbsoluteUnixPath OTHERS_PATH = AbsoluteUnixPath.get("/app/other");

  /**
   * Creates a new {@link JavaContainerBuilder} that uses distroless java as the base image.
   *
   * @return a new {@link JavaContainerBuilder} that uses distroless java as the base image
   * @throws InvalidImageReferenceException if creating the base image reference fails
   */
  public static JavaContainerBuilder fromDistroless() throws InvalidImageReferenceException {
    return from(RegistryImage.named("gcr.io/distroless/java"));
  }

  /**
   * Creates a new {@link JavaContainerBuilder} with the specified base image reference.
   *
   * @param baseImageReference the base image reference
   * @return a new {@link JavaContainerBuilder} with the specified base image reference
   * @throws InvalidImageReferenceException if {@code baseImageReference} is invalid
   */
  public static JavaContainerBuilder from(String baseImageReference)
      throws InvalidImageReferenceException {
    return from(RegistryImage.named(baseImageReference));
  }

  /**
   * Creates a new {@link JavaContainerBuilder} with the specified base image reference.
   *
   * @param baseImageReference the base image reference
   * @return a new {@link JavaContainerBuilder} with the specified base image reference
   */
  public static JavaContainerBuilder from(ImageReference baseImageReference) {
    return from(RegistryImage.named(baseImageReference));
  }

  /**
   * Creates a new {@link JavaContainerBuilder} with the specified base image reference.
   *
   * @param baseImageReference the base image reference
   * @return a new {@link JavaContainerBuilder} with the specified base image reference
   */
  public static JavaContainerBuilder from(RegistryImage baseImageReference) {
    return new JavaContainerBuilder(Jib.from(baseImageReference));
  }

  private JibContainerBuilder jibContainerBuilder;
  private JavaLayerConfigurations.Builder layerConfigurationsBuilder;
  private List<String> classpath;
  private List<String> jvmFlags;
  @Nullable private String mainClass;

  private JavaContainerBuilder(JibContainerBuilder jibContainerBuilder) {
    this.jibContainerBuilder = jibContainerBuilder;
    layerConfigurationsBuilder = JavaLayerConfigurations.builder();
    classpath = new ArrayList<>();
    jvmFlags = new ArrayList<>();
  }

  /**
   * Adds dependencies to /app/libs on the image.
   *
   * @param dependencyFiles the list of dependencies to add to the image
   * @return this
   * @throws IOException if adding the layer fails
   */
  public JavaContainerBuilder addDependencies(List<Path> dependencyFiles) throws IOException {
    for (Path file : dependencyFiles) {
      if (!Files.exists(file)) {
        continue;
      }
      if (Files.isDirectory(file)) {
        layerConfigurationsBuilder.addDirectoryContents(
            LayerType.DEPENDENCIES,
            file,
            path -> !path.getFileName().toString().contains("SNAPSHOT"),
            DEPENDENCIES_PATH);
        layerConfigurationsBuilder.addDirectoryContents(
            LayerType.SNAPSHOT_DEPENDENCIES,
            file,
            path -> path.getFileName().toString().contains("SNAPSHOT"),
            DEPENDENCIES_PATH);
      } else {
        layerConfigurationsBuilder.addFile(
            file.getFileName().toString().contains("SNAPSHOT")
                ? LayerType.SNAPSHOT_DEPENDENCIES
                : LayerType.DEPENDENCIES,
            file,
            DEPENDENCIES_PATH.resolve(file.getFileName()));
      }
    }
    if (!classpath.contains(DEPENDENCIES_PATH.resolve("*").toString())) {
      classpath.add(DEPENDENCIES_PATH.resolve("*").toString());
    }
    return this;
  }

  /**
   * Adds dependencies to /app/libs on the image.
   *
   * @param dependencyFiles the list of dependencies to add to the image
   * @return this
   * @throws IOException if adding the layer fails
   */
  public JavaContainerBuilder addDependencies(Path... dependencyFiles) throws IOException {
    return addDependencies(Arrays.asList(dependencyFiles));
  }

  /**
   * Adds resources to /app/resources on the image.
   *
   * @param resourceFilesDirectory the directory containing the project's resources
   * @return this
   * @throws IOException if adding the layer fails
   */
  public JavaContainerBuilder addResources(Path resourceFilesDirectory) throws IOException {
    if (!Files.exists(resourceFilesDirectory) || !Files.isDirectory(resourceFilesDirectory)) {
      throw new IOException(
          "Adding resources failed: '" + resourceFilesDirectory + "' is not a directory");
    }
    layerConfigurationsBuilder.addDirectoryContents(
        LayerType.RESOURCES, resourceFilesDirectory, path -> true, RESOURCES_PATH);
    if (!classpath.contains(RESOURCES_PATH.toString())) {
      classpath.add(RESOURCES_PATH.toString());
    }
    return this;
  }

  /**
   * Adds classes to /app/classes on the image.
   *
   * @param classFilesDirectory the directory containing the class files
   * @return this
   * @throws IOException if adding the layer fails
   */
  public JavaContainerBuilder addClasses(Path classFilesDirectory) throws IOException {
    if (!Files.exists(classFilesDirectory) || !Files.isDirectory(classFilesDirectory)) {
      throw new IOException(
          "Adding classes failed: '" + classFilesDirectory + "' is not a directory");
    }
    layerConfigurationsBuilder.addDirectoryContents(
        LayerType.CLASSES, classFilesDirectory, path -> true, CLASSES_PATH);
    if (!classpath.contains(CLASSES_PATH.toString())) {
      classpath.add(CLASSES_PATH.toString());
    }
    return this;
  }

  /**
   * Adds additional files to the image's classpath.
   *
   * @param otherFiles the list of files to add. Files are added to /app/other on the container
   * @return this
   * @throws IOException if adding the layer fails
   */
  public JavaContainerBuilder addToClasspath(List<Path> otherFiles) throws IOException {
    for (Path file : otherFiles) {
      if (!Files.exists(file)) {
        continue;
      }
      if (Files.isDirectory(file)) {
        layerConfigurationsBuilder.addDirectoryContents(
            LayerType.EXTRA_FILES, file, path -> true, OTHERS_PATH);
      } else {
        layerConfigurationsBuilder.addFile(
            LayerType.EXTRA_FILES, file, OTHERS_PATH.resolve(file.getFileName()));
      }
    }
    if (!classpath.contains(OTHERS_PATH.toString())) {
      classpath.add(OTHERS_PATH.toString());
    }
    return this;
  }

  /**
   * Adds additional files to the image's classpath.
   *
   * @param otherFiles the list of files to add. Files are added to /app/other on the container
   * @return this
   * @throws IOException if adding the layer fails
   */
  public JavaContainerBuilder addToClasspath(Path... otherFiles) throws IOException {
    return addToClasspath(Arrays.asList(otherFiles));
  }

  /**
   * Sets the JVM flags to use when starting the application.
   *
   * @param jvmFlags the list of JVM flags
   * @return this
   */
  public JavaContainerBuilder setJvmFlags(List<String> jvmFlags) {
    this.jvmFlags = ImmutableList.copyOf(jvmFlags);
    return this;
  }

  /**
   * Sets the JVM flags to use when starting the application.
   *
   * @param jvmFlags the list of JVM flags
   * @return this
   */
  public JavaContainerBuilder setJvmFlags(String... jvmFlags) {
    this.jvmFlags = ImmutableList.copyOf(jvmFlags);
    return this;
  }

  /**
   * Sets the main class used to start the application on the image.
   *
   * @param mainClass the main class used to start the application
   * @return this
   */
  public JavaContainerBuilder setMainClass(String mainClass) {
    this.mainClass = mainClass;
    return this;
  }

  /**
   * Returns a new {@link JibContainerBuilder} using the parameters specified on the {@link
   * JavaContainerBuilder}.
   *
   * @return a new {@link JibContainerBuilder} using the parameters specified on the {@link
   *     JavaContainerBuilder}
   */
  public JibContainerBuilder toContainerBuilder() {
    if (mainClass == null) {
      throw new IllegalArgumentException(
          "mainClass is null on JavaContainerBuilder; specify the "
              + "main class using JavaContainerBuilder#setMainClass(String), or consider using a "
              + "jib.frontend.MainClassFinder to infer the main class");
    }
    if (classpath.isEmpty()) {
      throw new IllegalArgumentException(
          "Failed to construct entrypoint because no files were added to the JavaContainerBuilder");
    }
    jibContainerBuilder.setEntrypoint(
        JavaEntrypointConstructor.makeEntrypoint(classpath, jvmFlags, mainClass));
    jibContainerBuilder.setLayers(layerConfigurationsBuilder.build().getLayerConfigurations());
    return jibContainerBuilder;
  }
}
