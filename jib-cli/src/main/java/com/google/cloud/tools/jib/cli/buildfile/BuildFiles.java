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

package com.google.cloud.tools.jib.cli.buildfile;

import static com.google.cloud.tools.jib.api.Jib.DOCKER_DAEMON_IMAGE_PREFIX;
import static com.google.cloud.tools.jib.api.Jib.REGISTRY_IMAGE_PREFIX;
import static com.google.cloud.tools.jib.api.Jib.TAR_IMAGE_PREFIX;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.cloud.tools.jib.api.DockerDaemonImage;
import com.google.cloud.tools.jib.api.ImageReference;
import com.google.cloud.tools.jib.api.InvalidImageReferenceException;
import com.google.cloud.tools.jib.api.Jib;
import com.google.cloud.tools.jib.api.JibContainerBuilder;
import com.google.cloud.tools.jib.api.RegistryImage;
import com.google.cloud.tools.jib.api.TarImage;
import com.google.cloud.tools.jib.api.buildplan.Platform;
import com.google.cloud.tools.jib.cli.cli2.Build;
import com.google.cloud.tools.jib.cli.cli2.CommonCliOptions;
import com.google.cloud.tools.jib.cli.cli2.Credentials;
import com.google.cloud.tools.jib.frontend.CredentialRetrieverFactory;
import com.google.cloud.tools.jib.plugins.common.DefaultCredentialRetrievers;
import com.google.cloud.tools.jib.plugins.common.logging.ConsoleLogger;
import com.google.common.base.Charsets;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.text.StringSubstitutor;
import org.apache.commons.text.io.StringSubstitutorReader;

/** Class to convert BuildFiles to build container representations. */
public class BuildFiles {

  /** Read a build file from disk and apply templating parameters. */
  private static BuildFileSpec toBuildFileSpec(
      Path buildFilePath, Map<String, String> templateParameters) throws IOException {
    ObjectMapper yamlObjectMapper = new ObjectMapper(new YAMLFactory());
    StringSubstitutor templater =
        new StringSubstitutor(templateParameters).setEnableUndefinedVariableException(true);
    try (StringSubstitutorReader reader =
        new StringSubstitutorReader(
            Files.newBufferedReader(buildFilePath, Charsets.UTF_8), templater)) {
      return yamlObjectMapper.readValue(reader, BuildFileSpec.class);
    }
  }

  /**
   * Read a buildfile from disk and generate a JibContainerBuilder instance. All parsing of files
   * considers the directory the buildfile is located in as the working directory.
   *
   * @param projectRoot the root context directory of this build
   * @param buildFilePath a file containing the build definition
   * @param buildCommandOptions the build configuration from the command line
   * @param commonCliOptions common cli options
   * @param logger a logger to inject into various objects that do logging
   * @return a {@link JibContainerBuilder} generated from the contents of {@code buildFilePath}
   * @throws IOException if an I/O error occurs opening the file, or an error occurs while
   *     traversing files on the filesystem
   * @throws InvalidImageReferenceException if the baseImage reference can not be parsed
   */
  public static JibContainerBuilder toJibContainerBuilder(
      Path projectRoot,
      Path buildFilePath,
      Build buildCommandOptions,
      CommonCliOptions commonCliOptions,
      ConsoleLogger logger)
      throws InvalidImageReferenceException, IOException {
    BuildFileSpec buildFile =
        toBuildFileSpec(buildFilePath, buildCommandOptions.getTemplateParameters());

    JibContainerBuilder containerBuilder =
        buildFile.getFrom().isPresent()
            ? createJibContainerBuilder(
                buildFile.getFrom().get(), buildCommandOptions, commonCliOptions, logger)
            : Jib.fromScratch();

    buildFile.getCreationTime().ifPresent(containerBuilder::setCreationTime);
    buildFile.getFormat().ifPresent(containerBuilder::setFormat);
    containerBuilder.setEnvironment(buildFile.getEnvironment());
    containerBuilder.setLabels(buildFile.getLabels());
    containerBuilder.setVolumes(buildFile.getVolumes());
    containerBuilder.setExposedPorts(buildFile.getExposedPorts());
    buildFile.getUser().ifPresent(containerBuilder::setUser);
    buildFile.getWorkingDirectory().ifPresent(containerBuilder::setWorkingDirectory);
    buildFile.getEntrypoint().ifPresent(containerBuilder::setEntrypoint);
    buildFile.getCmd().ifPresent(containerBuilder::setProgramArguments);

    if (buildFile.getLayers().isPresent()) {
      containerBuilder.setFileEntriesLayers(
          Layers.toLayers(projectRoot, buildFile.getLayers().get()));
    }
    return containerBuilder;
  }

  // TODO: add testing, need to do via intergration tests as there's no good way to extract out that
  //   the base image was populated as the user intended currently.
  static JibContainerBuilder createJibContainerBuilder(
      BaseImageSpec from,
      Build buildCommandOptions,
      CommonCliOptions commonCliOptions,
      ConsoleLogger logger)
      throws InvalidImageReferenceException, FileNotFoundException {
    String baseImageReference = from.getImage();
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
    if (!from.getPlatforms().isEmpty()) {
      containerBuilder.setPlatforms(
          from.getPlatforms()
              .stream()
              .map(
                  platformSpec ->
                      new Platform(platformSpec.getArchitecture(), platformSpec.getOs()))
              .collect(Collectors.toCollection(LinkedHashSet::new)));
    }
    return containerBuilder;
  }
}
