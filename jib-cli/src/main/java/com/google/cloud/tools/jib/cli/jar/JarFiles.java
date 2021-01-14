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
import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer;
import com.google.cloud.tools.jib.cli.CommonCliOptions;
import com.google.cloud.tools.jib.cli.Credentials;
import com.google.cloud.tools.jib.cli.Jar;
import com.google.cloud.tools.jib.frontend.CredentialRetrieverFactory;
import com.google.cloud.tools.jib.plugins.common.DefaultCredentialRetrievers;
import com.google.cloud.tools.jib.plugins.common.logging.ConsoleLogger;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;

/** Class to build a container representation from the contents of a jar file. */
public class JarFiles {

  /**
   * Generates a {@link JibContainerBuilder} from contents of a jar file.
   *
   * @param processor jar processor
   * @param jarOptions jar cli options
   * @param commonCliOptions common cli options
   * @param logger console logger
   * @return JibContainerBuilder
   * @throws IOException if I/O error occurs when opening the jar file or if temporary directory
   *     provided doesn't exist
   * @throws InvalidImageReferenceException if the base image reference is invalid
   */
  public static JibContainerBuilder toJibContainerBuilder(
      JarProcessor processor,
      Jar jarOptions,
      CommonCliOptions commonCliOptions,
      ConsoleLogger logger)
      throws IOException, InvalidImageReferenceException {

    // Use distroless as the default base image.
    JibContainerBuilder containerBuilder =
        jarOptions.getFrom().isPresent()
            ? createJibContainerBuilder(jarOptions.getFrom().get(), commonCliOptions, logger)
            : Jib.from("gcr.io/distroless/java");

    List<FileEntriesLayer> layers = processor.createLayers();
    List<String> entrypoint = processor.computeEntrypoint(jarOptions.getJvmFlags());

    containerBuilder.setEntrypoint(entrypoint).setFileEntriesLayers(layers);
    containerBuilder.setExposedPorts(jarOptions.getExposedPorts());
    containerBuilder.setVolumes(jarOptions.getVolumes());
    containerBuilder.setEnvironment(jarOptions.getEnvironment());
    containerBuilder.setLabels(jarOptions.getLabels());
    jarOptions.getUser().ifPresent(containerBuilder::setUser);

    return containerBuilder;
  }

  private static JibContainerBuilder createJibContainerBuilder(
      String from, CommonCliOptions commonCliOptions, ConsoleLogger logger)
      throws InvalidImageReferenceException, FileNotFoundException {
    String baseImageReference = from;
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
    return Jib.from(registryImage);
  }
}
