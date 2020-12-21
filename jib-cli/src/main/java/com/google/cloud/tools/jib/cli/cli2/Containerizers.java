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

package com.google.cloud.tools.jib.cli.cli2;

import static com.google.cloud.tools.jib.api.Jib.DOCKER_DAEMON_IMAGE_PREFIX;
import static com.google.cloud.tools.jib.api.Jib.REGISTRY_IMAGE_PREFIX;
import static com.google.cloud.tools.jib.api.Jib.TAR_IMAGE_PREFIX;

import com.google.cloud.tools.jib.api.Containerizer;
import com.google.cloud.tools.jib.api.DockerDaemonImage;
import com.google.cloud.tools.jib.api.ImageReference;
import com.google.cloud.tools.jib.api.InvalidImageReferenceException;
import com.google.cloud.tools.jib.api.LogEvent;
import com.google.cloud.tools.jib.api.RegistryImage;
import com.google.cloud.tools.jib.api.TarImage;
import com.google.cloud.tools.jib.event.events.ProgressEvent;
import com.google.cloud.tools.jib.event.progress.ProgressEventHandler;
import com.google.cloud.tools.jib.frontend.CredentialRetrieverFactory;
import com.google.cloud.tools.jib.global.JibSystemProperties;
import com.google.cloud.tools.jib.plugins.common.DefaultCredentialRetrievers;
import com.google.cloud.tools.jib.plugins.common.logging.ConsoleLogger;
import com.google.cloud.tools.jib.plugins.common.logging.ProgressDisplayGenerator;
import java.io.FileNotFoundException;
import java.nio.file.Paths;
import java.util.List;

/** Helper class for creating Containerizers from JibCli specifications. */
public class Containerizers {

  /**
   * Create a Containerizer from a command line specification.
   *
   * @param commonCliOptions common cli options
   * @param logger a logger to inject into the build
   * @param cacheDirectories the location of the relevant caches for this build
   * @return a populated Containerizer
   * @throws InvalidImageReferenceException if the image reference could not be parsed
   * @throws FileNotFoundException if a credential helper file is not found
   */
  public static Containerizer from(
      CommonCliOptions commonCliOptions, ConsoleLogger logger, CacheDirectories cacheDirectories)
      throws InvalidImageReferenceException, FileNotFoundException {
    Containerizer containerizer = create(commonCliOptions, logger);

    applyHandlers(containerizer, logger);
    applyConfiguration(containerizer, commonCliOptions, cacheDirectories);

    return containerizer;
  }

  private static Containerizer create(CommonCliOptions commonCliOptions, ConsoleLogger logger)
      throws InvalidImageReferenceException, FileNotFoundException {
    String imageSpec = commonCliOptions.getTargetImage();
    if (imageSpec.startsWith(DOCKER_DAEMON_IMAGE_PREFIX)) {
      // TODO: allow setting docker env and docker executable (along with path/env)
      return Containerizer.to(
          DockerDaemonImage.named(imageSpec.replaceFirst(DOCKER_DAEMON_IMAGE_PREFIX, "")));
    }
    if (imageSpec.startsWith(TAR_IMAGE_PREFIX)) {
      return Containerizer.to(
          TarImage.at(Paths.get(imageSpec.replaceFirst(TAR_IMAGE_PREFIX, "")))
              .named(commonCliOptions.getName()));
    }
    ImageReference imageReference =
        ImageReference.parse(imageSpec.replaceFirst(REGISTRY_IMAGE_PREFIX, ""));
    RegistryImage registryImage = RegistryImage.named(imageReference);
    DefaultCredentialRetrievers defaultCredentialRetrievers =
        DefaultCredentialRetrievers.init(
            CredentialRetrieverFactory.forImage(
                imageReference,
                logEvent -> logger.log(logEvent.getLevel(), logEvent.getMessage())));
    Credentials.getToCredentialRetrievers(commonCliOptions, defaultCredentialRetrievers)
        .forEach(registryImage::addCredentialRetriever);
    return Containerizer.to(registryImage);
  }

  private static void applyConfiguration(
      Containerizer containerizer,
      CommonCliOptions commonCliOptions,
      CacheDirectories cacheDirectories) {
    containerizer.setToolName(VersionInfo.TOOL_NAME);
    containerizer.setToolVersion(VersionInfo.getVersionSimple());

    // TODO: it's strange that we use system properties to set these
    // TODO: perhaps we should expose these as configuration options on the containerizer
    if (commonCliOptions.isSendCredentialsOverHttp()) {
      System.setProperty(JibSystemProperties.SEND_CREDENTIALS_OVER_HTTP, Boolean.TRUE.toString());
    }
    if (commonCliOptions.isSerialize()) {
      System.setProperty(JibSystemProperties.SERIALIZE, Boolean.TRUE.toString());
    }

    containerizer.setAllowInsecureRegistries(commonCliOptions.isAllowInsecureRegistries());
    cacheDirectories.getBaseImageCache().ifPresent(containerizer::setBaseImageLayersCache);
    containerizer.setApplicationLayersCache(cacheDirectories.getApplicationLayersCache());

    commonCliOptions.getAdditionalTags().forEach(containerizer::withAdditionalTag);
  }

  private static void applyHandlers(Containerizer containerizer, ConsoleLogger consoleLogger) {
    containerizer
        .addEventHandler(
            LogEvent.class,
            logEvent -> consoleLogger.log(logEvent.getLevel(), logEvent.getMessage()))
        .addEventHandler(
            ProgressEvent.class,
            new ProgressEventHandler(
                update -> {
                  List<String> footer =
                      ProgressDisplayGenerator.generateProgressDisplay(
                          update.getProgress(), update.getUnfinishedLeafTasks());
                  footer.add("");
                  consoleLogger.setFooter(footer);
                }));
  }
}
