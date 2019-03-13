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

import com.google.cloud.tools.jib.ProjectInfo;
import com.google.cloud.tools.jib.filesystem.AbsoluteUnixPath;
import com.google.cloud.tools.jib.filesystem.RelativeUnixPath;
import com.google.cloud.tools.jib.frontend.JavaEntrypointConstructor;
import com.google.cloud.tools.jib.frontend.JavaLayerConfigurations;
import com.google.cloud.tools.jib.frontend.JavaLayerConfigurations.LayerType;
import com.google.cloud.tools.jib.frontend.MainClassFinder;
import com.google.cloud.tools.jib.image.ImageReference;
import com.google.cloud.tools.jib.image.InvalidImageReferenceException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/** Creates a {@link JibContainerBuilder} for containerizing Java applications. */
public class JavaContainerBuilder {

  /** Holds a directory and a filter. */
  private static class PathPredicatePair {

    private final Path path;
    private final Predicate<Path> predicate;

    private PathPredicatePair(Path path, Predicate<Path> predicate) {
      this.path = path;
      this.predicate = predicate;
    }
  }

  /**
   * Creates a new {@link JavaContainerBuilder} that uses distroless java as the base image. For
   * more information on {@code gcr.io/distroless/java}, see <a
   * href="https://github.com/GoogleContainerTools/distroless">the distroless repository</a>.
   *
   * @return a new {@link JavaContainerBuilder}
   * @see <a href="https://github.com/GoogleContainerTools/distroless">The distroless repository</a>
   */
  public static JavaContainerBuilder fromDistroless() {
    try {
      return from(RegistryImage.named("gcr.io/distroless/java"));
    } catch (InvalidImageReferenceException ignored) {
      throw new IllegalStateException("Unreachable");
    }
  }

  /**
   * Creates a new {@link JavaContainerBuilder} that uses distroless jetty as the base image. For
   * more information on {@code gcr.io/distroless/java}, see <a
   * href="https://github.com/GoogleContainerTools/distroless">the distroless repository</a>.
   *
   * @return a new {@link JavaContainerBuilder}
   * @see <a href="https://github.com/GoogleContainerTools/distroless">The distroless repository</a>
   */
  public static JavaContainerBuilder fromDistrolessJetty() {
    try {
      return from(RegistryImage.named("gcr.io/distroless/java/jetty"))
          .setAppRoot(AbsoluteUnixPath.get(JavaLayerConfigurations.DEFAULT_WEB_APP_ROOT));
    } catch (InvalidImageReferenceException ignored) {
      throw new IllegalStateException("Unreachable");
    }
  }

  /**
   * Creates a new {@link JavaContainerBuilder} with the specified base image reference.
   *
   * @param baseImageReference the base image reference
   * @return a new {@link JavaContainerBuilder}
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
   * @return a new {@link JavaContainerBuilder}
   */
  public static JavaContainerBuilder from(ImageReference baseImageReference) {
    return from(RegistryImage.named(baseImageReference));
  }

  /**
   * Creates a new {@link JavaContainerBuilder} with the specified base image.
   *
   * @param registryImage the {@link RegistryImage} that defines base container registry and
   *     credentials
   * @return a new {@link JavaContainerBuilder}
   */
  public static JavaContainerBuilder from(RegistryImage registryImage) {
    return new JavaContainerBuilder(Jib.from(registryImage));
  }

  private final JibContainerBuilder jibContainerBuilder;
  private final List<String> jvmFlags = new ArrayList<>();
  private final LinkedHashSet<LayerType> classpathOrder = new LinkedHashSet<>(4);

  // Keeps track of files to add to the image, by system path
  private final List<PathPredicatePair> addedResources = new ArrayList<>();
  private final List<PathPredicatePair> addedClasses = new ArrayList<>();
  private final List<Path> addedDependencies = new ArrayList<>();
  private final List<Path> addedOthers = new ArrayList<>();

  private AbsoluteUnixPath appRoot = AbsoluteUnixPath.get("/app");
  private RelativeUnixPath classesDestination =
      JavaEntrypointConstructor.DEFAULT_RELATIVE_CLASSES_PATH_ON_IMAGE;
  private RelativeUnixPath resourcesDestination =
      JavaEntrypointConstructor.DEFAULT_RELATIVE_RESOURCES_PATH_ON_IMAGE;
  private RelativeUnixPath dependenciesDestination =
      JavaEntrypointConstructor.DEFAULT_RELATIVE_DEPENDENCIES_PATH_ON_IMAGE;
  private RelativeUnixPath othersDestination = RelativeUnixPath.get("classpath");
  @Nullable private String mainClass;

  private JavaContainerBuilder(JibContainerBuilder jibContainerBuilder) {
    this.jibContainerBuilder = jibContainerBuilder;
  }

  /**
   * Sets the app root of the container image (useful for building WAR containers).
   *
   * @param appRoot the absolute path of the app on the container ({@code /app} by default)
   * @return this
   */
  public JavaContainerBuilder setAppRoot(String appRoot) {
    return setAppRoot(AbsoluteUnixPath.get(appRoot));
  }

  /**
   * Sets the app root of the container image (useful for building WAR containers).
   *
   * @param appRoot the absolute path of the app on the container ({@code /app} by default)
   * @return this
   */
  public JavaContainerBuilder setAppRoot(AbsoluteUnixPath appRoot) {
    this.appRoot = appRoot;
    return this;
  }

  /**
   * Sets the destination directory of the classes added to the container (relative to the app
   * root).
   *
   * @param classesDestination the path to the classes directory, relative to the app root
   * @return this
   */
  public JavaContainerBuilder setClassesDestination(RelativeUnixPath classesDestination) {
    this.classesDestination = classesDestination;
    return this;
  }

  /**
   * Sets the destination directory of the resources added to the container (relative to the app
   * root).
   *
   * @param resourcesDestination the path to the resources directory, relative to the app root
   * @return this
   */
  public JavaContainerBuilder setResourcesDestination(RelativeUnixPath resourcesDestination) {
    this.resourcesDestination = resourcesDestination;
    return this;
  }

  /**
   * Sets the destination directory of the dependencies added to the container (relative to the app
   * root).
   *
   * @param dependenciesDestination the path to the dependencies directory, relative to the app root
   * @return this
   */
  public JavaContainerBuilder setDependenciesDestination(RelativeUnixPath dependenciesDestination) {
    this.dependenciesDestination = dependenciesDestination;
    return this;
  }

  /**
   * Sets the destination directory of additional classpath files added to the container (relative
   * to the app root).
   *
   * @param othersDestination the additional classpath directory, relative to the app root
   * @return this
   */
  public JavaContainerBuilder setOthersDestination(RelativeUnixPath othersDestination) {
    this.othersDestination = othersDestination;
    return this;
  }

  /**
   * Adds dependency JARs to the image. Duplicate JAR filenames are renamed with the filesize in
   * order to avoid collisions.
   *
   * @param dependencyFiles the list of dependency JARs to add to the image
   * @return this
   * @throws IOException if adding the layer fails
   */
  public JavaContainerBuilder addDependencies(List<Path> dependencyFiles) throws IOException {
    // Make sure all files exist before adding any
    for (Path file : dependencyFiles) {
      if (!Files.exists(file)) {
        throw new NoSuchFileException(file.toString());
      }
    }
    addedDependencies.addAll(dependencyFiles);
    classpathOrder.add(LayerType.DEPENDENCIES);
    return this;
  }

  /**
   * Adds dependency JARs to the image. Duplicate JAR filenames are renamed with the filesize in
   * order to avoid collisions.
   *
   * @param dependencyFiles the list of dependency JARs to add to the image
   * @return this
   * @throws IOException if adding the layer fails
   */
  public JavaContainerBuilder addDependencies(Path... dependencyFiles) throws IOException {
    return addDependencies(Arrays.asList(dependencyFiles));
  }

  /**
   * Adds the contents of a resources directory to the image.
   *
   * @param resourceFilesDirectory the directory containing the project's resources
   * @return this
   * @throws IOException if adding the layer fails
   */
  public JavaContainerBuilder addResources(Path resourceFilesDirectory) throws IOException {
    return addResources(resourceFilesDirectory, path -> true);
  }

  /**
   * Adds the contents of a resources directory to the image.
   *
   * @param resourceFilesDirectory the directory containing the project's resources
   * @param pathFilter filter that determines which files (not directories) should be added
   * @return this
   * @throws IOException if adding the layer fails
   */
  public JavaContainerBuilder addResources(Path resourceFilesDirectory, Predicate<Path> pathFilter)
      throws IOException {
    classpathOrder.add(LayerType.RESOURCES);
    return addDirectory(addedResources, resourceFilesDirectory, pathFilter);
  }

  /**
   * Adds the contents of a classes directory to the image.
   *
   * @param classFilesDirectory the directory containing the class files
   * @return this
   * @throws IOException if adding the layer fails
   */
  public JavaContainerBuilder addClasses(Path classFilesDirectory) throws IOException {
    return addClasses(classFilesDirectory, path -> true);
  }

  /**
   * Adds the contents of a classes directory to the image.
   *
   * @param classFilesDirectory the directory containing the class files
   * @param pathFilter filter that determines which files (not directories) should be added
   * @return this
   * @throws IOException if adding the layer fails
   */
  public JavaContainerBuilder addClasses(Path classFilesDirectory, Predicate<Path> pathFilter)
      throws IOException {
    classpathOrder.add(LayerType.CLASSES);
    return addDirectory(addedClasses, classFilesDirectory, pathFilter);
  }

  /**
   * Adds additional files to the classpath. If {@code otherFiles} contains a directory, the files
   * within are added recursively, maintaining the directory structure. For files in {@code
   * otherFiles}, files with duplicate filenames will be overwritten (e.g. if {@code otherFiles}
   * contains '/loser/messages.txt' and '/winner/messages.txt', only the second 'messages.txt' is
   * added.
   *
   * @param otherFiles the list of files to add
   * @return this
   * @throws IOException if adding the layer fails
   */
  public JavaContainerBuilder addToClasspath(List<Path> otherFiles) throws IOException {
    // Make sure all files exist before adding any
    for (Path file : otherFiles) {
      if (!Files.exists(file)) {
        throw new NoSuchFileException(file.toString());
      }
    }
    classpathOrder.add(LayerType.EXTRA_FILES);
    addedOthers.addAll(otherFiles);
    return this;
  }

  /**
   * Adds additional files to the classpath. If {@code otherFiles} contains a directory, the files
   * within are added recursively, maintaining the directory structure. For files in {@code
   * otherFiles}, files with duplicate filenames will be overwritten (e.g. if {@code otherFiles}
   * contains '/loser/messages.txt' and '/winner/messages.txt', only the second 'messages.txt' is
   * added.
   *
   * @param otherFiles the list of files to add
   * @return this
   * @throws IOException if adding the layer fails
   */
  public JavaContainerBuilder addToClasspath(Path... otherFiles) throws IOException {
    return addToClasspath(Arrays.asList(otherFiles));
  }

  /**
   * Adds a JVM flag to use when starting the application.
   *
   * @param jvmFlag the JVM flag to add
   * @return this
   */
  public JavaContainerBuilder addJvmFlag(String jvmFlag) {
    jvmFlags.add(jvmFlag);
    return this;
  }

  /**
   * Adds JVM flags to use when starting the application.
   *
   * @param jvmFlags the list of JVM flags to add
   * @return this
   */
  public JavaContainerBuilder addJvmFlags(List<String> jvmFlags) {
    this.jvmFlags.addAll(jvmFlags);
    return this;
  }

  /**
   * Adds JVM flags to use when starting the application.
   *
   * @param jvmFlags the list of JVM flags to add
   * @return this
   */
  public JavaContainerBuilder addJvmFlags(String... jvmFlags) {
    this.jvmFlags.addAll(Arrays.asList(jvmFlags));
    return this;
  }

  /**
   * Sets the main class used to start the application on the image. To find the main class from
   * {@code .class} files, use {@link MainClassFinder}.
   *
   * @param mainClass the main class used to start the application
   * @return this
   * @see MainClassFinder
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
   * @throws IOException if building the {@link JibContainerBuilder} fails.
   */
  public JibContainerBuilder toContainerBuilder() throws IOException {
    if (mainClass == null) {
      throw new IllegalStateException(
          "mainClass is null on JavaContainerBuilder; specify the main class using "
              + "JavaContainerBuilder#setMainClass(String), or consider using a "
              + "jib.frontend.MainClassFinder to infer the main class");
    }
    if (classpathOrder.isEmpty()) {
      throw new IllegalStateException(
          "Failed to construct entrypoint because no files were added to the JavaContainerBuilder");
    }

    JavaLayerConfigurations.Builder layerConfigurationsBuilder = JavaLayerConfigurations.builder();

    // Add classes to layer configuration
    for (PathPredicatePair directory : addedClasses) {
      layerConfigurationsBuilder.addDirectoryContents(
          LayerType.CLASSES,
          directory.path,
          directory.predicate,
          appRoot.resolve(classesDestination));
    }

    // Add resources to layer configuration
    for (PathPredicatePair directory : addedResources) {
      layerConfigurationsBuilder.addDirectoryContents(
          LayerType.RESOURCES,
          directory.path,
          directory.predicate,
          appRoot.resolve(resourcesDestination));
    }

    // Detect duplicate filenames and rename with filesize to avoid collisions
    List<String> duplicates =
        addedDependencies
            .stream()
            .map(Path::getFileName)
            .map(Path::toString)
            .collect(Collectors.groupingBy(filename -> filename, Collectors.counting()))
            .entrySet()
            .stream()
            .filter(entry -> entry.getValue() > 1)
            .map(Entry::getKey)
            .collect(Collectors.toList());
    for (Path file : addedDependencies) {
      // Add dependencies to layer configuration
      layerConfigurationsBuilder.addFile(
          file.getFileName().toString().contains("SNAPSHOT")
              ? LayerType.SNAPSHOT_DEPENDENCIES
              : LayerType.DEPENDENCIES,
          file,
          appRoot
              .resolve(dependenciesDestination)
              .resolve(
                  duplicates.contains(file.getFileName().toString())
                      ? file.getFileName()
                              .toString()
                              .replaceFirst("\\.jar$", "-" + Files.size(file))
                          + ".jar"
                      : file.getFileName().toString()));
    }

    // Add others to layer configuration
    for (Path path : addedOthers) {
      if (Files.isDirectory(path)) {
        layerConfigurationsBuilder.addDirectoryContents(
            LayerType.EXTRA_FILES, path, path1 -> true, appRoot.resolve(othersDestination));
      } else {
        layerConfigurationsBuilder.addFile(
            LayerType.EXTRA_FILES,
            path,
            appRoot.resolve(othersDestination).resolve(path.getFileName()));
      }
    }

    // Construct entrypoint. Ensure classpath elements are in the same order as the files were added
    // to the JavaContainerBuilder.
    List<String> classpathElements = new ArrayList<>();
    for (LayerType path : classpathOrder) {
      switch (path) {
        case CLASSES:
          classpathElements.add(appRoot.resolve(classesDestination).toString());
          break;
        case RESOURCES:
          classpathElements.add(appRoot.resolve(resourcesDestination).toString());
          break;
        case DEPENDENCIES:
          classpathElements.add(appRoot.resolve(dependenciesDestination).resolve("*").toString());
          break;
        case EXTRA_FILES:
          classpathElements.add(appRoot.resolve(othersDestination).toString());
          break;
        default:
          throw new RuntimeException(
              "Bug in jib-core; please report the bug at " + ProjectInfo.GITHUB_NEW_ISSUE_URL);
      }
    }
    jibContainerBuilder.setEntrypoint(
        JavaEntrypointConstructor.makeEntrypoint(classpathElements, jvmFlags, mainClass));
    jibContainerBuilder.setLayers(layerConfigurationsBuilder.build().getLayerConfigurations());
    return jibContainerBuilder;
  }

  private JavaContainerBuilder addDirectory(
      List<PathPredicatePair> addedPaths, Path directory, Predicate<Path> filter)
      throws NoSuchFileException, NotDirectoryException {
    if (!Files.exists(directory)) {
      throw new NoSuchFileException(directory.toString());
    }
    if (!Files.isDirectory(directory)) {
      throw new NotDirectoryException(directory.toString());
    }
    addedPaths.add(new PathPredicatePair(directory, filter));
    return this;
  }
}
