/*
 * Copyright 2020 Google LLC.
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

package com.google.cloud.tools.jib.cli.jar;

import com.google.cloud.tools.jib.api.InvalidImageReferenceException;
import com.google.cloud.tools.jib.api.JibContainerBuilder;
import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer;
import com.google.cloud.tools.jib.cli.ArtifactProcessor;
import com.google.cloud.tools.jib.cli.CommonCliOptions;
import com.google.cloud.tools.jib.cli.CommonContainerConfigCliOptions;
import com.google.cloud.tools.jib.cli.ContainerBuilders;
import com.google.cloud.tools.jib.cli.Jar;
import com.google.cloud.tools.jib.plugins.common.logging.ConsoleLogger;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/** Class to build a container representation from the contents of a jar file. */
public class JarFiles {

  /**
   * Generates a {@link JibContainerBuilder} from contents of a jar file.
   *
   * @param processor jar processor
   * @param jarOptions jar cli options
   * @param commonCliOptions common cli options
   * @param commonContainerConfigCliOptions common command line options shared between jar and war
   *     command
   * @param logger console logger
   * @return JibContainerBuilder
   * @throws IOException if I/O error occurs when opening the jar file or if temporary directory
   *     provided doesn't exist
   * @throws InvalidImageReferenceException if the base image reference is invalid
   */
  public static JibContainerBuilder toJibContainerBuilder(
      ArtifactProcessor processor,
      Jar jarOptions,
      CommonCliOptions commonCliOptions,
      CommonContainerConfigCliOptions commonContainerConfigCliOptions,
      ConsoleLogger logger)
      throws IOException, InvalidImageReferenceException {

    // Use AdoptOpenJDK image as the default base image.
    JibContainerBuilder containerBuilder;
    Optional<String> baseImageRef = commonContainerConfigCliOptions.getFrom();
    if (baseImageRef.isPresent()) {
      containerBuilder =
          ContainerBuilders.create(
              baseImageRef.get(), Collections.emptySet(), commonCliOptions, logger);
    } else {
      containerBuilder =
          (processor.getJavaVersion() <= 8)
              ? ContainerBuilders.create(
                  "adoptopenjdk:8-jre", Collections.emptySet(), commonCliOptions, logger)
              : ContainerBuilders.create(
                  "adoptopenjdk:11-jre", Collections.emptySet(), commonCliOptions, logger);
    }

    List<FileEntriesLayer> layers = processor.createLayers();
    List<String> customEntrypoint = commonContainerConfigCliOptions.getEntrypoint();
    List<String> entrypoint =
        customEntrypoint.isEmpty()
            ? processor.computeEntrypoint(jarOptions.getJvmFlags())
            : customEntrypoint;

    containerBuilder
        .setEntrypoint(entrypoint)
        .setFileEntriesLayers(layers)
        .setExposedPorts(commonContainerConfigCliOptions.getExposedPorts())
        .setVolumes(commonContainerConfigCliOptions.getVolumes())
        .setEnvironment(commonContainerConfigCliOptions.getEnvironment())
        .setLabels(commonContainerConfigCliOptions.getLabels())
        .setProgramArguments(commonContainerConfigCliOptions.getProgramArguments());
    commonContainerConfigCliOptions.getUser().ifPresent(containerBuilder::setUser);
    commonContainerConfigCliOptions.getFormat().ifPresent(containerBuilder::setFormat);
    commonContainerConfigCliOptions.getCreationTime().ifPresent(containerBuilder::setCreationTime);

    return containerBuilder;
  }
}
