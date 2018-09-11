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

package com.google.cloud.tools.jib.frontend;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.tools.jib.image.LayerEntry;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.io.MoreFiles;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.StringJoiner;
import javax.annotation.Nullable;

/**
 * Generates a Docker context for a Java application.
 *
 * <p>The image consists of a base image layer and 5 application layers under the directories:
 *
 * <ul>
 *   <li>{@code libs/} (dependency jars)
 *   <li>{@code snapshot-libs/} (snapshot dependency jars)
 *   <li>{@code resources/} (resource files)
 *   <li>{@code classes/} ({@code .class} files)
 *   <li>{@code root/} (extra files)
 * </ul>
 *
 * Empty application layers are omitted.
 */
public class JavaDockerContextGenerator {

  private static final String DEPENDENCIES_LAYER_DIRECTORY = "libs";
  private static final String SNAPSHOT_DEPENDENCIES_LAYER_DIRECTORY = "snapshot-libs";
  private static final String RESOURCES_LAYER_DIRECTORY = "resources";
  private static final String CLASSES_LAYER_DIRECTORY = "classes";
  // TODO: remove this once we put files in WAR into the relevant layers (i.e., dependencies,
  // snapshot dependencies, resources, and classes layers). Should copy files in the right
  private static final String EXPLODED_WAR_LAYER_DIRECTORY = "exploded-war";
  private static final String EXTRA_FILES_LAYER_DIRECTORY = "root";

  private static final ObjectMapper objectMapper = new ObjectMapper();

  /** Represents a Dockerfile {@code COPY} directive. */
  private static class CopyDirective {

    /** The layer entries to put into the context. */
    private final ImmutableList<LayerEntry> layerEntries;

    /** The directory in the context to put the source files for the layer */
    private final String directoryInContext;

    /** The extraction path in the image. */
    private final String extractionPath;

    private CopyDirective(
        ImmutableList<LayerEntry> layerEntries, String directoryInContext, String extractionPath) {
      this.layerEntries = layerEntries;
      this.directoryInContext = directoryInContext;
      this.extractionPath = extractionPath;
    }
  }

  /**
   * Adds a copy directive for the {@code layerEntries} if it's not empty.
   *
   * @param listBuilder the {@link ImmutableList.Builder} to add to
   * @param layerEntries the layer entries
   * @param directoryInContext the directory in the context to put the source files for the layer
   * @param extractionPath the extraction path to extract the directory to
   */
  private static void addIfNotEmpty(
      ImmutableList.Builder<CopyDirective> listBuilder,
      ImmutableList<LayerEntry> layerEntries,
      String directoryInContext,
      String extractionPath) {
    if (layerEntries.isEmpty()) {
      return;
    }

    listBuilder.add(new CopyDirective(layerEntries, directoryInContext, extractionPath));
  }

  /**
   * Converts a map to a corresponding dockerfile string in the form of:
   *
   * <pre>{@code
   * command key1="value1" \
   *     key2="value2" \
   *     ...
   * }</pre>
   *
   * @param map the map to convert
   * @param command the dockerfile command to prefix the map values with
   * @return the new dockerfile command as a string
   * @throws JsonProcessingException if getting the json string of a map value fails
   */
  private static String mapToDockerfileString(Map<String, String> map, String command)
      throws JsonProcessingException {
    if (map.isEmpty()) {
      return "";
    }

    StringJoiner joiner = new StringJoiner(" \\\n    ", "\n" + command + " ", "");
    for (Entry<String, String> entry : map.entrySet()) {
      joiner.add(entry.getKey() + "=" + objectMapper.writeValueAsString(entry.getValue()));
    }
    return joiner.toString();
  }

  private final ImmutableList<CopyDirective> copyDirectives;

  @Nullable private String baseImage;
  private List<String> entrypoint = Collections.emptyList();
  private List<String> javaArguments = Collections.emptyList();
  private Map<String, String> environment = Collections.emptyMap();
  private List<String> exposedPorts = Collections.emptyList();
  private Map<String, String> labels = Collections.emptyMap();

  /**
   * Constructs a Docker context generator for a Java application.
   *
   * @param javaLayerConfigurations the {@link JavaLayerConfigurations}
   */
  public JavaDockerContextGenerator(JavaLayerConfigurations javaLayerConfigurations) {
    ImmutableList.Builder<CopyDirective> copyDirectivesBuilder = ImmutableList.builder();
    addIfNotEmpty(
        copyDirectivesBuilder,
        javaLayerConfigurations.getDependencyLayerEntries(),
        DEPENDENCIES_LAYER_DIRECTORY,
        JavaEntrypointConstructor.DEFAULT_DEPENDENCIES_PATH_ON_IMAGE);
    addIfNotEmpty(
        copyDirectivesBuilder,
        javaLayerConfigurations.getSnapshotDependencyLayerEntries(),
        SNAPSHOT_DEPENDENCIES_LAYER_DIRECTORY,
        JavaEntrypointConstructor.DEFAULT_DEPENDENCIES_PATH_ON_IMAGE);
    addIfNotEmpty(
        copyDirectivesBuilder,
        javaLayerConfigurations.getResourceLayerEntries(),
        RESOURCES_LAYER_DIRECTORY,
        JavaEntrypointConstructor.DEFAULT_RESOURCES_PATH_ON_IMAGE);
    addIfNotEmpty(
        copyDirectivesBuilder,
        javaLayerConfigurations.getClassLayerEntries(),
        CLASSES_LAYER_DIRECTORY,
        JavaEntrypointConstructor.DEFAULT_CLASSES_PATH_ON_IMAGE);
    // TODO: remove this once we put files in WAR into the relevant layers (i.e., dependencies,
    // snapshot dependencies, resources, and classes layers). Should copy files in the right
    // directories. (For example, "resources" will go into the webapp root.)
    addIfNotEmpty(
        copyDirectivesBuilder,
        javaLayerConfigurations.getExplodedWarEntries(),
        EXPLODED_WAR_LAYER_DIRECTORY,
        JavaEntrypointConstructor.DEFAULT_JETTY_BASE_ON_IMAGE);
    addIfNotEmpty(
        copyDirectivesBuilder,
        javaLayerConfigurations.getExtraFilesLayerEntries(),
        EXTRA_FILES_LAYER_DIRECTORY,
        "/");
    copyDirectives = copyDirectivesBuilder.build();
  }

  /**
   * Sets the base image for the {@code FROM} directive. This must be called before {@link
   * #generate}.
   *
   * @param baseImage the base image.
   * @return this
   */
  public JavaDockerContextGenerator setBaseImage(String baseImage) {
    this.baseImage = baseImage;
    return this;
  }

  /**
   * Sets the entrypoint to be used as the {@code ENTRYPOINT}.
   *
   * @param entrypoint the entrypoint.
   * @return this
   */
  public JavaDockerContextGenerator setEntrypoint(List<String> entrypoint) {
    this.entrypoint = entrypoint;
    return this;
  }

  /**
   * Sets the arguments used in the {@code CMD}.
   *
   * @param javaArguments the list of arguments to pass into main.
   * @return this
   */
  public JavaDockerContextGenerator setJavaArguments(List<String> javaArguments) {
    this.javaArguments = javaArguments;
    return this;
  }

  /**
   * Sets the environment variables
   *
   * @param environment map from the environment variable name to value
   * @return this
   */
  public JavaDockerContextGenerator setEnvironment(Map<String, String> environment) {
    this.environment = environment;
    return this;
  }

  /**
   * Sets the exposed ports.
   *
   * @param exposedPorts the list of port numbers/port ranges to expose
   * @return this
   */
  public JavaDockerContextGenerator setExposedPorts(List<String> exposedPorts) {
    this.exposedPorts = exposedPorts;
    return this;
  }

  /**
   * Sets the labels
   *
   * @param labels the map of labels
   * @return this
   */
  public JavaDockerContextGenerator setLabels(Map<String, String> labels) {
    this.labels = labels;
    return this;
  }

  /**
   * Creates the Docker context in {@code #targetDirectory}.
   *
   * @param targetDirectory the directory to generate the Docker context in
   * @throws IOException if the export fails
   */
  public void generate(Path targetDirectory) throws IOException {
    Preconditions.checkNotNull(baseImage);

    // Deletes the targetDir if it exists.
    try {
      Files.deleteIfExists(targetDirectory);

    } catch (DirectoryNotEmptyException ex) {
      MoreFiles.deleteDirectoryContents(targetDirectory);
      Files.delete(targetDirectory);
    }

    Files.createDirectory(targetDirectory);

    for (CopyDirective copyDirective : copyDirectives) {
      // Creates the directories.
      Path directoryInContext = targetDirectory.resolve(copyDirective.directoryInContext);
      Files.createDirectory(directoryInContext);

      // Copies the source files to the directoryInContext.
      for (LayerEntry layerEntry : copyDirective.layerEntries) {
        // This resolves the path to copy the source file to in the {@code directory}.
        // For example, for a 'baseDirectory' of 'target/jib-docker-context/classes', a
        // 'baseExtractionPath' of '/app/classes', and an 'actualExtractionPath' of
        // '/app/classes/com/test/HelloWorld.class', the resolved destination would be
        // 'target/jib-docker-context/classes/com/test/HelloWorld.class'.
        Path destination =
            directoryInContext.resolve(
                Paths.get(copyDirective.extractionPath).relativize(layerEntry.getExtractionPath()));

        if (Files.isDirectory(layerEntry.getSourceFile())) {
          Files.createDirectories(destination);
        } else {
          Files.copy(layerEntry.getSourceFile(), destination);
        }
      }
    }

    // Creates the Dockerfile.
    Files.write(
        targetDirectory.resolve("Dockerfile"), makeDockerfile().getBytes(StandardCharsets.UTF_8));
  }

  /**
   * Makes the contents of a {@code Dockerfile} using configuration data, in the following format:
   *
   * <pre>{@code
   * FROM [base image]
   *
   * COPY libs [path/to/dependencies]
   * COPY snapshot-libs [path/to/dependencies]
   * COPY resources [path/to/resources]
   * COPY classes [path/to/classes]
   * COPY root [path/to/classes]
   *
   * EXPOSE [port]
   * [More EXPOSE instructions, if necessary]
   * ENV [key1]="[value1]" \
   *     [key2]="[value2]" \
   *     [...]
   * LABEL [key1]="[value1]" \
   *     [key2]="[value2]" \
   *     [...]
   * ENTRYPOINT java [jvm flags] -cp [classpaths] [main class]
   * CMD [main class args]
   * }</pre>
   *
   * @return the {@code Dockerfile} contents
   */
  @VisibleForTesting
  String makeDockerfile() throws JsonProcessingException {
    StringBuilder dockerfile = new StringBuilder();
    dockerfile.append("FROM ").append(Preconditions.checkNotNull(baseImage)).append("\n");
    for (CopyDirective copyDirective : copyDirectives) {
      dockerfile
          .append("\nCOPY ")
          .append(copyDirective.directoryInContext)
          .append(" ")
          .append(copyDirective.extractionPath);
    }

    dockerfile.append("\n");
    for (String port : exposedPorts) {
      dockerfile.append("\nEXPOSE ").append(port);
    }

    dockerfile.append(mapToDockerfileString(environment, "ENV"));
    dockerfile.append(mapToDockerfileString(labels, "LABEL"));
    dockerfile
        .append("\nENTRYPOINT ")
        .append(objectMapper.writeValueAsString(entrypoint))
        .append("\nCMD ")
        .append(objectMapper.writeValueAsString(javaArguments));
    return dockerfile.toString();
  }
}
