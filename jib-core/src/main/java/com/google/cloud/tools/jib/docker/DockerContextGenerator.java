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

import com.google.cloud.tools.jib.builder.EntrypointBuilder;
import com.google.cloud.tools.jib.builder.SourceFilesConfiguration;
import com.google.cloud.tools.jib.filesystem.FileOperations;
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
import java.util.regex.Matcher;
import java.util.stream.Collectors;
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

  /** The template to generate the Dockerfile from. */
  private static final String DOCKERFILE_TEMPLATE =
      "FROM @@BASE_IMAGE@@\n"
          + "\n"
          + "COPY libs @@DEPENDENCIES_PATH_ON_IMAGE@@\n"
          + "COPY resources @@RESOURCES_PATH_ON_IMAGE@@\n"
          + "COPY classes @@CLASSES_PATH_ON_IMAGE@@\n"
          + "\n"
          + "@@EXPOSE_INSTRUCTIONS@@\n"
          + "ENTRYPOINT @@ENTRYPOINT@@\n"
          + "CMD @@CMD@@\n";

  /**
   * Formats a list for the Dockerfile's ENTRYPOINT or CMD.
   *
   * @see <a
   *     href="https://docs.docker.com/engine/reference/builder/#exec-form-entrypoint-example">https://docs.docker.com/engine/reference/builder/#exec-form-entrypoint-example</a>
   * @param items the list of items to join into an array.
   * @return a string in the format: ["item1","item2",...]
   */
  @VisibleForTesting
  static String joinAsJsonArray(List<String> items) {
    StringBuilder resultString = new StringBuilder("[");
    boolean firstComponent = true;
    for (String item : items) {
      if (!firstComponent) {
        resultString.append(',');
      }

      // Escapes quotes.
      item = item.replaceAll("\"", Matcher.quoteReplacement("\\\""));

      resultString.append('"').append(item).append('"');
      firstComponent = false;
    }
    resultString.append(']');

    return resultString.toString();
  }

  /**
   * Builds a list of Dockerfile "EXPOSE" instructions.
   *
   * @param exposedPorts the list of ports numbers/ranges to expose
   * @return a string containing an EXPOSE instruction for each of the entries
   */
  @VisibleForTesting
  static String makeExposeItems(List<String> exposedPorts) {
    return String.join(
        "\n", exposedPorts.stream().map(port -> "EXPOSE " + port).collect(Collectors.toList()));
  }

  private final SourceFilesConfiguration sourceFilesConfiguration;

  @Nullable private String baseImage;
  private List<String> jvmFlags = Collections.emptyList();
  private String mainClass = "";
  private List<String> javaArguments = Collections.emptyList();
  private List<String> exposedPorts = Collections.emptyList();

  public DockerContextGenerator(SourceFilesConfiguration sourceFilesConfiguration) {
    this.sourceFilesConfiguration = sourceFilesConfiguration;
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

  /**
   * Creates the Docker context in {@code #targetDirectory}.
   *
   * @param targetDirectory the directory to generate the Docker context in.
   * @throws IOException if the export fails.
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
    Path resourcesDIr = targetDirectory.resolve("resources");
    Path classesDir = targetDirectory.resolve("classes");
    Files.createDirectory(dependenciesDir);
    Files.createDirectory(resourcesDIr);
    Files.createDirectory(classesDir);

    // Copies dependencies.
    FileOperations.copy(sourceFilesConfiguration.getDependenciesFiles(), dependenciesDir);
    FileOperations.copy(sourceFilesConfiguration.getResourcesFiles(), resourcesDIr);
    FileOperations.copy(sourceFilesConfiguration.getClassesFiles(), classesDir);

    // Creates the Dockerfile.
    Files.write(
        targetDirectory.resolve("Dockerfile"), makeDockerfile().getBytes(StandardCharsets.UTF_8));
  }

  /**
   * Makes a {@code Dockerfile} from the {@code DOCKERFILE_TEMPLATE}.
   *
   * @return the {@code Dockerfile} contents.
   * @throws IOException if reading the Dockerfile template fails.
   */
  @VisibleForTesting
  String makeDockerfile() throws IOException {
    Preconditions.checkNotNull(baseImage);

    return DOCKERFILE_TEMPLATE
        .replace("@@BASE_IMAGE@@", baseImage)
        .replace(
            "@@DEPENDENCIES_PATH_ON_IMAGE@@", sourceFilesConfiguration.getDependenciesPathOnImage())
        .replace("@@RESOURCES_PATH_ON_IMAGE@@", sourceFilesConfiguration.getResourcesPathOnImage())
        .replace("@@CLASSES_PATH_ON_IMAGE@@", sourceFilesConfiguration.getClassesPathOnImage())
        .replace("@@EXPOSE_INSTRUCTIONS@@", makeExposeItems(exposedPorts))
        .replace(
            "@@ENTRYPOINT@@",
            joinAsJsonArray(
                EntrypointBuilder.makeEntrypoint(sourceFilesConfiguration, jvmFlags, mainClass)))
        .replace("@@CMD@@", joinAsJsonArray(javaArguments));
  }
}
