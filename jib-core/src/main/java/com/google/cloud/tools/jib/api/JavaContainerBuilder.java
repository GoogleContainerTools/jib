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
import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer;
import com.google.cloud.tools.jib.api.buildplan.ModificationTimeProvider;
import com.google.cloud.tools.jib.api.buildplan.RelativeUnixPath;
import com.google.cloud.tools.jib.filesystem.DirectoryWalker;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Streams;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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

  /** Represents the different types of layers for a Java application. */
  public enum LayerType {
    DEPENDENCIES("dependencies"),
    SNAPSHOT_DEPENDENCIES("snapshot dependencies"),
    PROJECT_DEPENDENCIES("project dependencies"),
    RESOURCES("resources"),
    CLASSES("classes"),
    EXTRA_FILES("extra files"),
    JVM_ARG_FILES("jvm arg files");

    private final String name;

    /**
     * Initializes with a name for the layer.
     *
     * @param name name to set for the layer; does not affect the contents of the layer
     */
    LayerType(String name) {
      this.name = name;
    }

    public String getName() {
      return name;
    }
  }

  /**
   * Creates a new {@link JavaContainerBuilder} that uses distroless java as the base image. For
   * more information on {@code gcr.io/distroless/java}, see <a
   * href="https://github.com/GoogleContainerTools/distroless">the distroless repository</a>.
   *
   * @return a new {@link JavaContainerBuilder}
   * @see <a href="https://github.com/GoogleContainerTools/distroless">The distroless repository</a>
   * @deprecated Use {@code from()} with the image reference {@code gcr.io/distroless/java}.
   */
  @Deprecated
  public static JavaContainerBuilder fromDistroless() {
    try {
      return from(RegistryImage.named("gcr.io/distroless/java"));
    } catch (InvalidImageReferenceException ignored) {
      throw new IllegalStateException("Unreachable");
    }
  }

  /**
   * The default app root in the image. For example, if this is set to {@code "/app"}, dependency
   * JARs will be in {@code "/app/libs"}.
   */
  public static final String DEFAULT_APP_ROOT = "/app";

  /**
   * The default webapp root in the image. For example, if this is set to {@code
   * "/jetty/webapps/ROOT"}, dependency JARs will be in {@code "/jetty/webapps/ROOT/WEB-INF/lib"}.
   *
   * @deprecated Use the string {@code "/jetty/webapps/ROOT"}.
   */
  @Deprecated public static final String DEFAULT_WEB_APP_ROOT = "/jetty/webapps/ROOT";

  /**
   * Creates a new {@link JavaContainerBuilder} that uses distroless jetty as the base image. For
   * more information on {@code gcr.io/distroless/java}, see <a
   * href="https://github.com/GoogleContainerTools/distroless">the distroless repository</a>.
   *
   * @return a new {@link JavaContainerBuilder}
   * @see <a href="https://github.com/GoogleContainerTools/distroless">The distroless repository</a>
   * @deprecated Use {@code from()} with the image reference {@code gcr.io/distroless/java/jetty}
   *     and change the app root by calling {@code
   *     JavaContainerBuilder.setAppRoot("/jetty/webapps/ROOT")}.
   */
  @Deprecated
  public static JavaContainerBuilder fromDistrolessJetty() {
    try {
      return from(RegistryImage.named("gcr.io/distroless/java/jetty"))
          .setAppRoot(AbsoluteUnixPath.get(DEFAULT_WEB_APP_ROOT));
    } catch (InvalidImageReferenceException ignored) {
      throw new IllegalStateException("Unreachable");
    }
  }

  /**
   * Creates a new {@link JavaContainerBuilder} with the specified base image reference. The type of
   * base image can be specified using a prefix; see {@link Jib#from(String)} for the accepted
   * prefixes.
   *
   * @param baseImageReference the base image reference
   * @return a new {@link JavaContainerBuilder}
   * @throws InvalidImageReferenceException if {@code baseImageReference} is invalid
   */
  public static JavaContainerBuilder from(String baseImageReference)
      throws InvalidImageReferenceException {
    return new JavaContainerBuilder(Jib.from(baseImageReference));
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

  /**
   * Starts building the container from a base image stored in the Docker cache. Requires a running
   * Docker daemon.
   *
   * @param dockerDaemonImage the {@link DockerDaemonImage} that defines the base image and Docker
   *     client
   * @return a new {@link JavaContainerBuilder}
   */
  public static JavaContainerBuilder from(DockerDaemonImage dockerDaemonImage) {
    return new JavaContainerBuilder(Jib.from(dockerDaemonImage));
  }

  /**
   * Starts building the container from a tarball.
   *
   * @param tarImage the {@link TarImage} that defines the path to the base image
   * @return a new {@link JavaContainerBuilder}
   */
  public static JavaContainerBuilder from(TarImage tarImage) {
    return new JavaContainerBuilder(Jib.from(tarImage));
  }

  private final JibContainerBuilder jibContainerBuilder;
  private final List<String> jvmFlags = new ArrayList<>();
  private final LinkedHashSet<LayerType> classpathOrder = new LinkedHashSet<>(4);

  // Keeps track of files to add to the image, by system path
  private final List<PathPredicatePair> addedResources = new ArrayList<>();
  private final List<PathPredicatePair> addedClasses = new ArrayList<>();
  private final List<Path> addedDependencies = new ArrayList<>();
  private final List<Path> addedSnapshotDependencies = new ArrayList<>();
  private final List<Path> addedProjectDependencies = new ArrayList<>();
  private final List<Path> addedOthers = new ArrayList<>();

  private AbsoluteUnixPath appRoot = AbsoluteUnixPath.get(DEFAULT_APP_ROOT);
  private RelativeUnixPath classesDestination = RelativeUnixPath.get("classes");
  private RelativeUnixPath resourcesDestination = RelativeUnixPath.get("resources");
  private RelativeUnixPath dependenciesDestination = RelativeUnixPath.get("libs");
  private RelativeUnixPath othersDestination = RelativeUnixPath.get("classpath");
  @Nullable private String mainClass;
  private ModificationTimeProvider modificationTimeProvider =
      FileEntriesLayer.DEFAULT_MODIFICATION_TIME_PROVIDER;

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
   * Adds dependency JARs to the image. Duplicate JAR filenames across all dependencies are renamed
   * with the filesize in order to avoid collisions.
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
   * Adds dependency JARs to the image. Duplicate JAR filenames across all dependencies are renamed
   * with the filesize in order to avoid collisions.
   *
   * @param dependencyFiles the list of dependency JARs to add to the image
   * @return this
   * @throws IOException if adding the layer fails
   */
  public JavaContainerBuilder addDependencies(Path... dependencyFiles) throws IOException {
    return addDependencies(Arrays.asList(dependencyFiles));
  }

  /**
   * Adds snapshot dependency JARs to the image. Duplicate JAR filenames across all dependencies are
   * renamed with the filesize in order to avoid collisions.
   *
   * @param dependencyFiles the list of dependency JARs to add to the image
   * @return this
   * @throws IOException if adding the layer fails
   */
  public JavaContainerBuilder addSnapshotDependencies(List<Path> dependencyFiles)
      throws IOException {
    // Make sure all files exist before adding any
    for (Path file : dependencyFiles) {
      if (!Files.exists(file)) {
        throw new NoSuchFileException(file.toString());
      }
    }
    addedSnapshotDependencies.addAll(dependencyFiles);
    classpathOrder.add(LayerType.DEPENDENCIES); // this is a single classpath entry with all deps
    return this;
  }

  /**
   * Adds snapshot dependency JARs to the image. Duplicate JAR filenames across all dependencies are
   * renamed with the filesize in order to avoid collisions.
   *
   * @param dependencyFiles the list of dependency JARs to add to the image
   * @return this
   * @throws IOException if adding the layer fails
   */
  public JavaContainerBuilder addSnapshotDependencies(Path... dependencyFiles) throws IOException {
    return addSnapshotDependencies(Arrays.asList(dependencyFiles));
  }

  /**
   * Adds project dependency JARs to the image. Generally, project dependency are jars produced from
   * source in this project as part of other modules/sub-projects. Duplicate JAR filenames across
   * all dependencies are renamed with the filesize in order to avoid collisions.
   *
   * @param dependencyFiles the list of dependency JARs to add to the image
   * @return this
   * @throws IOException if adding the layer fails
   */
  public JavaContainerBuilder addProjectDependencies(List<Path> dependencyFiles)
      throws IOException {
    // Make sure all files exist before adding any
    for (Path file : dependencyFiles) {
      if (!Files.exists(file)) {
        throw new NoSuchFileException(file.toString());
      }
    }
    addedProjectDependencies.addAll(dependencyFiles);
    classpathOrder.add(LayerType.DEPENDENCIES); // this is a single classpath entry with all deps
    return this;
  }

  /**
   * Adds project dependency JARs to the image. Generally, project dependency are jars produced from
   * source in this project as part of other modules/sub-projects. Duplicate JAR filenames across
   * all dependencies are renamed with the filesize in order to avoid collisions.
   *
   * @param dependencyFiles the list of dependency JARs to add to the image
   * @return this
   * @throws IOException if adding the layer fails
   */
  public JavaContainerBuilder addProjectDependencies(Path... dependencyFiles) throws IOException {
    return addProjectDependencies(Arrays.asList(dependencyFiles));
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
   * Sets the container entrypoint with the specified main class. The entrypoint will be left
   * unconfigured if this method is not called. To find the main class from {@code .class} files,
   * use {@link MainClassFinder}.
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
   * Sets the modification time provider for container files.
   *
   * @param modificationTimeProvider a provider that takes a source path and destination path on the
   *     container and returns the file modification time that should be set for that path
   * @return this
   */
  public JavaContainerBuilder setModificationTimeProvider(
      ModificationTimeProvider modificationTimeProvider) {
    this.modificationTimeProvider = modificationTimeProvider;
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
    if (mainClass == null && !jvmFlags.isEmpty()) {
      throw new IllegalStateException(
          "Failed to construct entrypoint on JavaContainerBuilder; "
              + "jvmFlags were set, but mainClass is null. Specify the main class using "
              + "JavaContainerBuilder#setMainClass(String), or consider using MainClassFinder to "
              + "infer the main class.");
    }
    if (classpathOrder.isEmpty()) {
      throw new IllegalStateException(
          "Failed to construct entrypoint because no files were added to the JavaContainerBuilder");
    }

    Map<LayerType, FileEntriesLayer.Builder> layerBuilders = new EnumMap<>(LayerType.class);

    // Add classes to layer configuration
    for (PathPredicatePair directory : addedClasses) {
      addDirectoryContentsToLayer(
          layerBuilders,
          LayerType.CLASSES,
          directory.path,
          directory.predicate,
          appRoot.resolve(classesDestination));
    }

    // Add resources to layer configuration
    for (PathPredicatePair directory : addedResources) {
      addDirectoryContentsToLayer(
          layerBuilders,
          LayerType.RESOURCES,
          directory.path,
          directory.predicate,
          appRoot.resolve(resourcesDestination));
    }

    // Detect duplicate filenames across all dependency layer types
    Map<String, Long> occurrences =
        Streams.concat(
                addedDependencies.stream(),
                addedSnapshotDependencies.stream(),
                addedProjectDependencies.stream())
            .map(path -> path.getFileName().toString())
            .collect(Collectors.groupingBy(filename -> filename, Collectors.counting()));
    List<String> duplicates =
        occurrences
            .entrySet()
            .stream()
            .filter(entry -> entry.getValue() > 1)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());

    ImmutableMap<LayerType, List<Path>> layerMap =
        ImmutableMap.of(
            LayerType.DEPENDENCIES, addedDependencies,
            LayerType.SNAPSHOT_DEPENDENCIES, addedSnapshotDependencies,
            LayerType.PROJECT_DEPENDENCIES, addedProjectDependencies);
    for (Map.Entry<LayerType, List<Path>> entry : layerMap.entrySet()) {
      for (Path file : Preconditions.checkNotNull(entry.getValue())) {
        // Handle duplicates by appending filesize to the end of the file. This renaming logic
        // must be in sync with the code that does the same in the other place. See
        // https://github.com/GoogleContainerTools/jib/issues/3331
        String jarName = file.getFileName().toString();
        if (duplicates.contains(jarName)) {
          jarName = jarName.replaceFirst("\\.jar$", "-" + Files.size(file)) + ".jar";
        }
        // Add dependencies to layer configuration
        addFileToLayer(
            layerBuilders,
            entry.getKey(),
            file,
            appRoot.resolve(dependenciesDestination).resolve(jarName));
      }
    }

    // Add others to layer configuration
    for (Path path : addedOthers) {
      if (Files.isDirectory(path)) {
        addDirectoryContentsToLayer(
            layerBuilders,
            LayerType.EXTRA_FILES,
            path,
            ignored -> true,
            appRoot.resolve(othersDestination));
      } else {
        addFileToLayer(
            layerBuilders,
            LayerType.EXTRA_FILES,
            path,
            appRoot.resolve(othersDestination).resolve(path.getFileName()));
      }
    }

    // Add layer configurations to container builder
    List<FileEntriesLayer> layers = new ArrayList<>();
    layerBuilders.forEach((type, builder) -> layers.add(builder.setName(type.getName()).build()));
    jibContainerBuilder.setFileEntriesLayers(layers);

    if (mainClass != null) {
      // Construct entrypoint. Ensure classpath elements are in the same order as the files were
      // added to the JavaContainerBuilder.
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
      String classpathString = String.join(":", classpathElements);
      List<String> entrypoint = new ArrayList<>(4 + jvmFlags.size());
      entrypoint.add("java");
      entrypoint.addAll(jvmFlags);
      entrypoint.add("-cp");
      entrypoint.add(classpathString);
      entrypoint.add(mainClass);
      jibContainerBuilder.setEntrypoint(entrypoint);
    }

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

  private void addFileToLayer(
      Map<LayerType, FileEntriesLayer.Builder> layerBuilders,
      LayerType layerType,
      Path sourceFile,
      AbsoluteUnixPath pathInContainer) {
    if (!layerBuilders.containsKey(layerType)) {
      layerBuilders.put(layerType, FileEntriesLayer.builder());
    }
    //    layerBuilders.computeIfAbsent(layerType, x -> FileEntriesLayer.builder());
    Instant modificationTime = modificationTimeProvider.get(sourceFile, pathInContainer);
    layerBuilders.get(layerType).addEntry(sourceFile, pathInContainer, modificationTime);
  }

  private void addDirectoryContentsToLayer(
      Map<LayerType, FileEntriesLayer.Builder> layerBuilders,
      LayerType layerType,
      Path sourceRoot,
      Predicate<Path> pathFilter,
      AbsoluteUnixPath basePathInContainer)
      throws IOException {
    if (!layerBuilders.containsKey(layerType)) {
      layerBuilders.put(layerType, FileEntriesLayer.builder());
    }
    //    layerBuilders.computeIfAbsent(layerType, x -> FileEntriesLayer.builder());
    FileEntriesLayer.Builder builder = layerBuilders.get(layerType);

    new DirectoryWalker(sourceRoot)
        .filterRoot()
        .filter(path -> Files.isDirectory(path) || pathFilter.test(path))
        .walk(
            path -> {
              AbsoluteUnixPath pathOnContainer =
                  basePathInContainer.resolve(sourceRoot.relativize(path));
              Instant modificationTime = modificationTimeProvider.get(path, pathOnContainer);
              builder.addEntry(path, pathOnContainer, modificationTime);
            });
  }
}
