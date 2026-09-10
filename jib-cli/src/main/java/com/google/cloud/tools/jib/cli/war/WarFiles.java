/*
 * Copyright 2021 Google LLC.
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

package com.google.cloud.tools.jib.cli.war;

import com.google.cloud.tools.jib.api.InvalidImageReferenceException;
import com.google.cloud.tools.jib.api.JibContainerBuilder;
import com.google.cloud.tools.jib.cli.ArtifactProcessor;
import com.google.cloud.tools.jib.cli.CommonCliOptions;
import com.google.cloud.tools.jib.cli.CommonContainerConfigCliOptions;
import com.google.cloud.tools.jib.cli.ContainerBuilders;
import com.google.cloud.tools.jib.plugins.common.logging.ConsoleLogger;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;

public class WarFiles {

  private WarFiles() {}

  /**
   * Generates a {@link JibContainerBuilder} from contents of a WAR file.
   *
   * @param processor artifact processor
   * @param commonCliOptions common cli options
   * @param commonContainerConfigCliOptions common cli options shared between jar and war command
   * @param logger console logger
   * @return JibContainerBuilder
   * @throws IOException if I/O error occurs when opening the war file or if temporary directory
   *     provided doesn't exist
   * @throws InvalidImageReferenceException if the base image reference is invalid
   */
  public static JibContainerBuilder toJibContainerBuilder(
      ArtifactProcessor processor,
      CommonCliOptions commonCliOptions,
      CommonContainerConfigCliOptions commonContainerConfigCliOptions,
      ConsoleLogger logger)
      throws IOException, InvalidImageReferenceException {
    String baseImage = commonContainerConfigCliOptions.getFrom().orElse("jetty");
    JibContainerBuilder containerBuilder =
        ContainerBuilders.create(baseImage, Collections.emptySet(), commonCliOptions, logger);
    List<String> programArguments = commonContainerConfigCliOptions.getProgramArguments();
    if (!commonContainerConfigCliOptions.getProgramArguments().isEmpty()) {
      containerBuilder.setProgramArguments(programArguments);
    }
    containerBuilder
        .setEntrypoint(computeEntrypoint(commonContainerConfigCliOptions))
        .setFileEntriesLayers(processor.createLayers())
        .setExposedPorts(commonContainerConfigCliOptions.getExposedPorts())
        .setVolumes(commonContainerConfigCliOptions.getVolumes())
        .setEnvironment(commonContainerConfigCliOptions.getEnvironment())
        .setLabels(commonContainerConfigCliOptions.getLabels());
    commonContainerConfigCliOptions.getUser().ifPresent(containerBuilder::setUser);
    commonContainerConfigCliOptions.getFormat().ifPresent(containerBuilder::setFormat);
    commonContainerConfigCliOptions.getCreationTime().ifPresent(containerBuilder::setCreationTime);

    return containerBuilder;
  }

  @Nullable
  private static List<String> computeEntrypoint(
      CommonContainerConfigCliOptions commonContainerConfigCliOptions)
      throws InvalidImageReferenceException {
    List<String> entrypoint = commonContainerConfigCliOptions.getEntrypoint();
    if (!entrypoint.isEmpty()) {
      return entrypoint;
    }
    if (commonContainerConfigCliOptions.isJettyBaseimage()) {
      // Since we are using Jetty 12 or later as the default, the deploy module needs to be
      // specified. See
      // https://eclipse.dev/jetty/documentation/jetty-12/operations-guide/index.html
      return ImmutableList.of("java", "-jar", "/usr/local/jetty/start.jar", "--module=ee10-deploy");
    }
    return null;
  }
}
