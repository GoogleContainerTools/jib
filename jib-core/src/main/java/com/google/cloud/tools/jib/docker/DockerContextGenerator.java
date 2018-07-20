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

package com.google.cloud.tools.jib.docker;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.tools.jib.filesystem.FileOperations;
import com.google.cloud.tools.jib.frontend.JavaEntrypointBuilder;
import com.google.cloud.tools.jib.image.LayerEntry;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.io.MoreFiles;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Generates a Docker context that mimics how Jib builds the image.
 *
 * <p>The image consists of a base image layer and three application layers under the directories:
 *
 * <ul>
 *   <li>libs/ (dependency jars)
 *   <li>resources/
 *   <li>classes
 * </ul>
 */
public class DockerContextGenerator {

  private final LayerEntry dependenciesLayerEntry;
  private final LayerEntry snapshotDependenciesLayerEntry;
  private final LayerEntry resourcesLayerEntry;
  private final LayerEntry classesLayerEntry;
  private final LayerEntry extraFilesLayerEntry;

  @Nullable private String baseImage;
  private List<String> jvmFlags = Collections.emptyList();
  private String mainClass = "";
  private List<String> javaArguments = Collections.emptyList();
  private List<String> exposedPorts = Collections.emptyList();

  // TODO: Just take the LayerConfigurations.
  public DockerContextGenerator(
      LayerEntry dependenciesLayerEntry,
      LayerEntry snapshotDependenciesLayerEntry,
      LayerEntry resourcesLayerEntry,
      LayerEntry classesLayerEntry,
      LayerEntry extraFilesLayerEntry) {
    this.dependenciesLayerEntry = dependenciesLayerEntry;
    this.snapshotDependenciesLayerEntry = snapshotDependenciesLayerEntry;
    this.resourcesLayerEntry = resourcesLayerEntry;
    this.classesLayerEntry = classesLayerEntry;
    this.extraFilesLayerEntry = extraFilesLayerEntry;
  }

  /**
   * Sets the base image for the {@code FROM} directive. This must be called before {@link
   * #generate}.
   *
   * @param baseImage the base image.
   * @return this
   */
  public DockerContextGenerator setBaseImage(String baseImage) {
    this.baseImage = baseImage;
    return this;
  }

  /**
   * Sets the JVM flags used in the {@code ENTRYPOINT}.
   *
   * @param jvmFlags the jvm flags.
   * @return this
   */
  public DockerContextGenerator setJvmFlags(List<String> jvmFlags) {
    this.jvmFlags = jvmFlags;
    return this;
  }

  /**
   * Sets the main class used in the {@code ENTRYPOINT}.
   *
   * @param mainClass the name of the main class.
   * @return this
   */
  public DockerContextGenerator setMainClass(String mainClass) {
    this.mainClass = mainClass;
    return this;
  }

  /**
   * Sets the arguments used in the {@code CMD}.
   *
   * @param javaArguments the list of arguments to pass into main.
   * @return this
   */
  public DockerContextGenerator setJavaArguments(List<String> javaArguments) {
    this.javaArguments = javaArguments;
    return this;
  }

  /**
   * Sets the exposed ports.
   *
   * @param exposedPorts the list of port numbers/port ranges to expose
   * @return this
   */
  public DockerContextGenerator setExposedPorts(List<String> exposedPorts) {
    this.exposedPorts = exposedPorts;
    return this;
  }

  // TODO: Don't generate empty layers.
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

    // Creates the directories.
    Path dependenciesDir = targetDirectory.resolve("libs");
    Path snapshotDependenciesDir = targetDirectory.resolve("snapshot-libs");
    Path resourcesDIr = targetDirectory.resolve("resources");
    Path classesDir = targetDirectory.resolve("classes");
    Path extraFilesDir = targetDirectory.resolve("root");
    Files.createDirectory(dependenciesDir);
    Files.createDirectory(snapshotDependenciesDir);
    Files.createDirectory(resourcesDIr);
    Files.createDirectory(classesDir);
    Files.createDirectory(extraFilesDir);

    // Copies dependencies.
    FileOperations.copy(dependenciesLayerEntry.getSourceFiles(), dependenciesDir);
    FileOperations.copy(snapshotDependenciesLayerEntry.getSourceFiles(), snapshotDependenciesDir);
    FileOperations.copy(resourcesLayerEntry.getSourceFiles(), resourcesDIr);
    FileOperations.copy(classesLayerEntry.getSourceFiles(), classesDir);
    FileOperations.copy(extraFilesLayerEntry.getSourceFiles(), extraFilesDir);

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
   * COPY resources [path/to/resources]
   * COPY classes [path/to/classes]
   *
   * EXPOSE [port]
   * [More EXPOSE instructions, if necessary]
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
    dockerfile
        .append("FROM ")
        .append(Preconditions.checkNotNull(baseImage))
        .append("\n\nCOPY libs ")
        .append(dependenciesLayerEntry.getExtractionPath());
    if (!snapshotDependenciesLayerEntry.getSourceFiles().isEmpty()) {
      dockerfile.append("\nCOPY snapshot-libs ").append(dependenciesLayerEntry.getExtractionPath());
    }
    dockerfile
        .append("\nCOPY resources ")
        .append(resourcesLayerEntry.getExtractionPath())
        .append("\nCOPY classes ")
        .append(classesLayerEntry.getExtractionPath());
    if (!extraFilesLayerEntry.getSourceFiles().isEmpty()) {
      dockerfile.append("\nCOPY root ").append(extraFilesLayerEntry.getExtractionPath());
    }
    dockerfile.append("\n");
    for (String port : exposedPorts) {
      dockerfile.append("\nEXPOSE ").append(port);
    }
    dockerfile
        .append("\nENTRYPOINT ")
        .append(
            objectMapper.writeValueAsString(
                JavaEntrypointBuilder.makeDefaultEntrypoint(jvmFlags, mainClass)))
        .append("\nCMD ")
        .append(objectMapper.writeValueAsString(javaArguments));
    return dockerfile.toString();
  }
}
