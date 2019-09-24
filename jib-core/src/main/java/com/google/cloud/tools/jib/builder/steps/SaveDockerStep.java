/*
 * Copyright 2019 Google LLC.
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

package com.google.cloud.tools.jib.builder.steps;

import com.google.cloud.tools.jib.api.ImageReference;
import com.google.cloud.tools.jib.builder.ProgressEventDispatcher;
import com.google.cloud.tools.jib.builder.TimerEventDispatcher;
import com.google.cloud.tools.jib.configuration.BuildConfiguration;
import com.google.cloud.tools.jib.docker.DockerClient;
import com.google.cloud.tools.jib.event.progress.ThrottledAccumulatingConsumer;
import com.google.cloud.tools.jib.filesystem.TempDirectoryProvider;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.Callable;

/** Saves an image from the docker daemon. */
public class SaveDockerStep implements Callable<Path> {

  private final BuildConfiguration buildConfiguration;
  private final DockerClient dockerClient;
  private final ProgressEventDispatcher.Factory progressEventDispatcherFactory;
  private final TempDirectoryProvider tempDirectoryProvider;

  SaveDockerStep(
      BuildConfiguration buildConfiguration,
      DockerClient dockerClient,
      ProgressEventDispatcher.Factory progressEventDispatcherFactory,
      TempDirectoryProvider tempDirectoryProvider) {
    this.buildConfiguration = buildConfiguration;
    this.dockerClient = dockerClient;
    this.progressEventDispatcherFactory = progressEventDispatcherFactory;
    this.tempDirectoryProvider = tempDirectoryProvider;
  }

  @Override
  public Path call() throws IOException, InterruptedException {
    Path outputDir = tempDirectoryProvider.newDirectory();
    Path outputPath = outputDir.resolve("out.tar");
    ImageReference imageReference = buildConfiguration.getBaseImageConfiguration().getImage();
    try (TimerEventDispatcher ignored =
        new TimerEventDispatcher(
            buildConfiguration.getEventHandlers(),
            "Saving " + imageReference + " from Docker daemon")) {
      long size = dockerClient.sizeOf(imageReference);
      try (ProgressEventDispatcher progressEventDispatcher =
              progressEventDispatcherFactory.create("saving base image " + imageReference, size);
          ThrottledAccumulatingConsumer throttledProgressReporter =
              new ThrottledAccumulatingConsumer(progressEventDispatcher::dispatchProgress)) {
        dockerClient.save(imageReference, outputPath, throttledProgressReporter);
      }
      return outputPath;
    }
  }
}
