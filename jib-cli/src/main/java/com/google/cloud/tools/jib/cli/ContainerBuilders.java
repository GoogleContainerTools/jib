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

package com.google.cloud.tools.jib.cli;

import static com.google.cloud.tools.jib.api.Jib.DOCKER_DAEMON_IMAGE_PREFIX;
import static com.google.cloud.tools.jib.api.Jib.REGISTRY_IMAGE_PREFIX;
import static com.google.cloud.tools.jib.api.Jib.TAR_IMAGE_PREFIX;

import com.google.cloud.tools.jib.api.DockerDaemonImage;
import com.google.cloud.tools.jib.api.ImageReference;
import com.google.cloud.tools.jib.api.InvalidImageReferenceException;
import com.google.cloud.tools.jib.api.Jib;
import com.google.cloud.tools.jib.api.JibContainerBuilder;
import com.google.cloud.tools.jib.api.RegistryImage;
import com.google.cloud.tools.jib.api.TarImage;
import com.google.cloud.tools.jib.api.buildplan.Platform;
import com.google.cloud.tools.jib.frontend.CredentialRetrieverFactory;
import com.google.cloud.tools.jib.plugins.common.DefaultCredentialRetrievers;
import com.google.cloud.tools.jib.plugins.common.logging.ConsoleLogger;
import java.io.FileNotFoundException;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

/** Creates jib container builder. */
public class ContainerBuilders {

  /**
   * Generates a {@link JibContainerBuilder} depending on the base image specified.
   *
   * @param baseImageReference base image
   * @param platforms platforms for multi-platform support in build command.
   * @param commonCliOptions common cli options
   * @param logger console logger
   * @return a {@link JibContainerBuilder}
   * @throws InvalidImageReferenceException if the baseImage reference cannot be parsed
   * @throws FileNotFoundException if credential helper file cannot be found
   */
  public static JibContainerBuilder create(
      String baseImageReference,
      List<Platform> platforms,
      CommonCliOptions commonCliOptions,
      ConsoleLogger logger)
      throws InvalidImageReferenceException, FileNotFoundException {
    if (baseImageReference.startsWith(DOCKER_DAEMON_IMAGE_PREFIX)) {
      return Jib.from(
          DockerDaemonImage.named(baseImageReference.replaceFirst(DOCKER_DAEMON_IMAGE_PREFIX, "")));
    }
    if (baseImageReference.startsWith(TAR_IMAGE_PREFIX)) {
      return Jib.from(
          TarImage.at(Paths.get(baseImageReference.replaceFirst(TAR_IMAGE_PREFIX, ""))));
    }
    ImageReference imageReference =
        ImageReference.parse(baseImageReference.replaceFirst(REGISTRY_IMAGE_PREFIX, ""));
    RegistryImage registryImage = RegistryImage.named(imageReference);
    DefaultCredentialRetrievers defaultCredentialRetrievers =
        DefaultCredentialRetrievers.init(
            CredentialRetrieverFactory.forImage(
                imageReference,
                logEvent -> logger.log(logEvent.getLevel(), logEvent.getMessage())));
    Credentials.getFromCredentialRetrievers(commonCliOptions, defaultCredentialRetrievers)
        .forEach(registryImage::addCredentialRetriever);
    JibContainerBuilder containerBuilder = Jib.from(registryImage);
    if (!platforms.isEmpty()) {
      containerBuilder.setPlatforms(platforms.stream().collect(Collectors.toSet()));
    }
    return containerBuilder;
  }
}
