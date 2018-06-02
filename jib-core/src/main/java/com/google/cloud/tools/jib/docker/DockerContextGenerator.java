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
import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
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

  private final SourceFilesConfiguration sourceFilesConfiguration;

  @Nullable private String baseImage;
  private List<String> jvmFlags = Collections.emptyList();
  private String mainClass = "";

  public DockerContextGenerator(SourceFilesConfiguration sourceFilesConfiguration) {
    this.sourceFilesConfiguration = sourceFilesConfiguration;
  }

  /**
   * Sets the base image for the {@code FROM} directive. This must be called before {@link
   * #generate}.
   */
  public DockerContextGenerator setBaseImage(String baseImage) {
    this.baseImage = baseImage;
    return this;
  }

  /** Sets the JVM flags used in the {@code ENTRYPOINT}. */
  public DockerContextGenerator setJvmFlags(List<String> jvmFlags) {
    this.jvmFlags = jvmFlags;
    return this;
  }

  /** Sets the main class used in the {@code ENTRYPOINT}. */
  public DockerContextGenerator setMainClass(String mainClass) {
    this.mainClass = mainClass;
    return this;
  }

  /** Creates the Docker context in {@code #targetDirectory}. */
  public void generate(Path targetDirectory) throws IOException {
    Preconditions.checkNotNull(baseImage);

    // Deletes the targetDir if it exists.
    if (Files.exists(targetDirectory)) {
      System.out.println("WTFFFF");
      try {
        Files.delete(targetDirectory);

      } catch (DirectoryNotEmptyException ex) {
        MoreFiles.deleteDirectoryContents(targetDirectory);
        Files.delete(targetDirectory);
      }
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

  @VisibleForTesting
  /** Makes a {@code Dockerfile} from the {@code DockerfileTemplate}. */
  String makeDockerfile() throws IOException {
    Preconditions.checkNotNull(baseImage);

    String dockerfileTemplate =
        Resources.toString(Resources.getResource("DockerfileTemplate"), StandardCharsets.UTF_8);

    return dockerfileTemplate
        .replace("@@BASE_IMAGE@@", baseImage)
        .replace(
            "@@DEPENDENCIES_PATH_ON_IMAGE@@", sourceFilesConfiguration.getDependenciesPathOnImage())
        .replace("@@RESOURCES_PATH_ON_IMAGE@@", sourceFilesConfiguration.getResourcesPathOnImage())
        .replace("@@CLASSES_PATH_ON_IMAGE@@", sourceFilesConfiguration.getClassesPathOnImage())
        .replace("@@ENTRYPOINT@@", getEntrypoint());
  }

  /**
   * Gets the Dockerfile ENTRYPOINT in exec-form.
   *
   * @see <a
   *     href="https://docs.docker.com/engine/reference/builder/#exec-form-entrypoint-example">https://docs.docker.com/engine/reference/builder/#exec-form-entrypoint-example</a>
   */
  @VisibleForTesting
  String getEntrypoint() {
    List<String> entrypoint =
        EntrypointBuilder.makeEntrypoint(sourceFilesConfiguration, jvmFlags, mainClass);

    StringBuilder entrypointString = new StringBuilder("[");
    boolean firstComponent = true;
    for (String entrypointComponent : entrypoint) {
      if (!firstComponent) {
        entrypointString.append(',');
      }

      // Escapes quotes.
      entrypointComponent = entrypointComponent.replaceAll("\"", Matcher.quoteReplacement("\\\""));

      entrypointString.append('"').append(entrypointComponent).append('"');
      firstComponent = false;
    }
    entrypointString.append(']');

    return entrypointString.toString();
  }
}
