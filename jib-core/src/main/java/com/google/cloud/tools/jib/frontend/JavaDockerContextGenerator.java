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
import com.google.cloud.tools.jib.filesystem.AbsoluteUnixPath;
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
  private static final String EXTRA_FILES_LAYER_DIRECTORY = "root";

  private static final ObjectMapper objectMapper = new ObjectMapper();

  /** Represents a Dockerfile {@code COPY} directive. */
  private static class CopyDirective {

    /** The layer entries to put into the context. */
    private final ImmutableList<LayerEntry> layerEntries;

    /** The directory in the context to put the source files for the layer */
    private final String directoryInContext;

    /** The extraction path in the image. */
    private final AbsoluteUnixPath extractionPath;

    private CopyDirective(
        ImmutableList<LayerEntry> layerEntries,
        String directoryInContext,
        AbsoluteUnixPath extractionPath) {
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
   */
  private static void addIfNotEmpty(
      ImmutableList.Builder<CopyDirective> listBuilder,
      ImmutableList<LayerEntry> layerEntries,
      String directoryInContext) {
    if (layerEntries.isEmpty()) {
      return;
    }

    listBuilder.add(new CopyDirective(layerEntries, directoryInContext, AbsoluteUnixPath.get("/")));
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
  @Nullable private List<String> entrypoint = Collections.emptyList();
  @Nullable private List<String> programArguments = Collections.emptyList();
  @Nullable private String user;
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
        DEPENDENCIES_LAYER_DIRECTORY);
    addIfNotEmpty(
        copyDirectivesBuilder,
        javaLayerConfigurations.getSnapshotDependencyLayerEntries(),
        SNAPSHOT_DEPENDENCIES_LAYER_DIRECTORY);
    addIfNotEmpty(
        copyDirectivesBuilder,
        javaLayerConfigurations.getResourceLayerEntries(),
        RESOURCES_LAYER_DIRECTORY);
    addIfNotEmpty(
        copyDirectivesBuilder,
        javaLayerConfigurations.getClassLayerEntries(),
        CLASSES_LAYER_DIRECTORY);
    addIfNotEmpty(
        copyDirectivesBuilder,
        javaLayerConfigurations.getExtraFilesLayerEntries(),
        EXTRA_FILES_LAYER_DIRECTORY);
    copyDirectives = copyDirectivesBuilder.build();
  }

  /**
   * Sets the base image for the {@code FROM} directive. This must be called before {@link
   * #generate}.
   *
   * @param baseImage the base image
   * @return this
   */
  public JavaDockerContextGenerator setBaseImage(String baseImage) {
    this.baseImage = baseImage;
    return this;
  }

  /**
   * Sets the entrypoint to be used as the {@code ENTRYPOINT}.
   *
   * @param entrypoint the entrypoint
   * @return this
   */
  public JavaDockerContextGenerator setEntrypoint(@Nullable List<String> entrypoint) {
    this.entrypoint = entrypoint;
    return this;
  }

  /**
   * Sets the user for the {@code USER} directive.
   *
   * @param user the username/UID and optionally the groupname/GID
   * @return this
   */
  public JavaDockerContextGenerator setUser(@Nullable String user) {
    this.user = user;
    return this;
  }

  /**
   * Sets the arguments used in the {@code CMD}.
   *
   * @param programArguments the list of arguments to append to {@code ENTRYPOINT}
   * @return this
   */
  public JavaDockerContextGenerator setProgramArguments(@Nullable List<String> programArguments) {
    this.programArguments = programArguments;
    return this;
  }

  /**
   * Sets the environment variables.
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
   * Sets the labels.
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
        String noLeadingSlash = layerEntry.getAbsoluteExtractionPathString().substring(1);
        Path destination = directoryInContext.resolve(noLeadingSlash);

        if (Files.isDirectory(layerEntry.getSourceFile())) {
          Files.createDirectories(destination);
        } else {
          Files.createDirectories(destination.getParent());
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
   * COPY libs /
   * COPY snapshot-libs /
   * COPY resources /
   * COPY classes /
   * COPY root /
   *
   * EXPOSE [port]
   * [More EXPOSE instructions, if necessary]
   * ENV [key1]="[value1]" \
   *     [key2]="[value2]" \
   *     [...]
   * LABEL [key1]="[value1]" \
   *     [key2]="[value2]" \
   *     [...]
   * USER [user name (or UID) and optionally user group (or GID)]
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
      boolean hasTrailingSlash = copyDirective.extractionPath.toString().endsWith("/");
      dockerfile
          .append("\nCOPY ")
          .append(copyDirective.directoryInContext)
          .append(" ")
          .append(copyDirective.extractionPath)
          .append(hasTrailingSlash ? "" : "/");
    }

    dockerfile.append("\n");
    for (String port : exposedPorts) {
      dockerfile.append("\nEXPOSE ").append(port);
    }

    dockerfile.append(mapToDockerfileString(environment, "ENV"));
    dockerfile.append(mapToDockerfileString(labels, "LABEL"));
    if (entrypoint != null) {
      dockerfile.append("\nENTRYPOINT ").append(objectMapper.writeValueAsString(entrypoint));
    }
    if (programArguments != null) {
      dockerfile.append("\nCMD ").append(objectMapper.writeValueAsString(programArguments));
    }
    if (user != null) {
      dockerfile.append("\nUSER ").append(user);
    }
    return dockerfile.toString();
  }
}
