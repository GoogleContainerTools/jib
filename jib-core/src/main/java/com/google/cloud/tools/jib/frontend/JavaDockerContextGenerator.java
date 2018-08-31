/*
 * Copyright 2018 Google LLC. All rights reserved.
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
import com.google.cloud.tools.jib.filesystem.FileOperations;
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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
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
  private static final String EXPLODED_WAR_LAYER_DIRECTORY = "exploded-war";
  private static final String EXTRA_FILES_LAYER_DIRECTORY = "root";

  /** Represents a Dockerfile {@code COPY} directive. */
  private static class CopyDirective {

    /** The source files to put into the context. */
    private final ImmutableList<Path> sourceFiles;

    /** The directory in the context to put the source files for the layer */
    private final String directoryInContext;

    /** The extraction path in the image. */
    private final String extractionPath;

    private CopyDirective(
        ImmutableList<Path> sourceFiles, String directoryInContext, String extractionPath) {
      this.sourceFiles = sourceFiles;
      this.directoryInContext = directoryInContext;
      this.extractionPath = extractionPath;
    }
  }

  /**
   * Adds a copy directive for the {@code layerEntry} if it's not empty.
   *
   * @param listBuilder the {@link ImmutableList.Builder} to add to
   * @param layerEntry the layer entry
   * @param directoryInContext the directory in the context to put the source files for the layer
   */
  private static void addIfNotEmpty(
      ImmutableList.Builder<CopyDirective> listBuilder,
      LayerEntry layerEntry,
      String directoryInContext) {
    if (layerEntry.getSourceFiles().isEmpty()) {
      return;
    }

    listBuilder.add(
        new CopyDirective(
            layerEntry.getSourceFiles(), directoryInContext, layerEntry.getExtractionPath()));
  }

  private final ImmutableList<CopyDirective> copyDirectives;

  @Nullable private String baseImage;
  private List<String> entrypoint = Collections.emptyList();
  private List<String> javaArguments = Collections.emptyList();
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
        javaLayerConfigurations.getDependenciesLayerEntry(),
        DEPENDENCIES_LAYER_DIRECTORY);
    addIfNotEmpty(
        copyDirectivesBuilder,
        javaLayerConfigurations.getSnapshotDependenciesLayerEntry(),
        SNAPSHOT_DEPENDENCIES_LAYER_DIRECTORY);
    addIfNotEmpty(
        copyDirectivesBuilder,
        javaLayerConfigurations.getResourcesLayerEntry(),
        RESOURCES_LAYER_DIRECTORY);
    addIfNotEmpty(
        copyDirectivesBuilder,
        javaLayerConfigurations.getClassesLayerEntry(),
        CLASSES_LAYER_DIRECTORY);
    addIfNotEmpty(
        copyDirectivesBuilder,
        javaLayerConfigurations.getExplodedWarEntry(),
        EXPLODED_WAR_LAYER_DIRECTORY);
    addIfNotEmpty(
        copyDirectivesBuilder,
        javaLayerConfigurations.getExtraFilesLayerEntry(),
        EXTRA_FILES_LAYER_DIRECTORY);

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

      // Copies dependencies.
      FileOperations.copy(copyDirective.sourceFiles, directoryInContext);
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
   * LABEL [key1]="[value1]" \
   *       [key2]="[value2]" \
   *       [...]
   * ENTRYPOINT java [jvm flags] -cp [classpaths] [main class]
   * CMD [main class args]
   * }</pre>
   *
   * @return the {@code Dockerfile} contents
   */
  @VisibleForTesting
  String makeDockerfile() throws JsonProcessingException {
    ObjectMapper objectMapper = new ObjectMapper();
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

    boolean firstLabel = true;
    for (Entry<String, String> label : labels.entrySet()) {
      dockerfile
          .append(firstLabel ? "\nLABEL " : " \\\n      ")
          .append(label.getKey())
          .append("=")
          .append(objectMapper.writeValueAsString(label.getValue()));
      firstLabel = false;
    }

    dockerfile
        .append("\nENTRYPOINT ")
        .append(objectMapper.writeValueAsString(entrypoint))
        .append("\nCMD ")
        .append(objectMapper.writeValueAsString(javaArguments));
    return dockerfile.toString();
  }
}
