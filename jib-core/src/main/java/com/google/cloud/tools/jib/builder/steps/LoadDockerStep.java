/*
 * Copyright 2018 Google LLC.
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
import com.google.cloud.tools.jib.api.LogEvent;
import com.google.cloud.tools.jib.builder.ProgressEventDispatcher;
import com.google.cloud.tools.jib.builder.TimerEventDispatcher;
import com.google.cloud.tools.jib.configuration.BuildConfiguration;
import com.google.cloud.tools.jib.docker.DockerClient;
import com.google.cloud.tools.jib.docker.ImageTarball;
import com.google.cloud.tools.jib.event.EventHandlers;
import com.google.cloud.tools.jib.event.progress.ThrottledAccumulatingConsumer;
import com.google.cloud.tools.jib.image.Image;
import java.io.IOException;
import java.util.concurrent.Callable;

/** Adds image layers to a tarball and loads into Docker daemon. */
class LoadDockerStep implements Callable<BuildResult> {

  private final BuildConfiguration buildConfiguration;
  private final ProgressEventDispatcher.Factory progressEventDispatcherFactory;

  private final DockerClient dockerClient;
  private final Image builtImage;

  LoadDockerStep(
      BuildConfiguration buildConfiguration,
      ProgressEventDispatcher.Factory progressEventDispatcherFactory,
      DockerClient dockerClient,
      Image builtImage) {
    this.buildConfiguration = buildConfiguration;
    this.progressEventDispatcherFactory = progressEventDispatcherFactory;
    this.dockerClient = dockerClient;
    this.builtImage = builtImage;
  }

  @Override
  public BuildResult call() throws InterruptedException, IOException {
    EventHandlers eventHandlers = buildConfiguration.getEventHandlers();
    try (TimerEventDispatcher ignored =
        new TimerEventDispatcher(eventHandlers, "Loading to Docker daemon")) {
      eventHandlers.dispatch(LogEvent.progress("Loading to Docker daemon..."));

      ImageReference targetImageReference =
          buildConfiguration.getTargetImageConfiguration().getImage();
      ImageTarball imageTarball = new ImageTarball(builtImage, targetImageReference);

      // Note: The progress reported here is not entirely accurate. The total allocation units is
      // the size of the layers, but the progress being reported includes the config and manifest
      // as well, so we will always go over the total progress allocation here.
      // See https://github.com/GoogleContainerTools/jib/pull/1960#discussion_r321898390
      try (ProgressEventDispatcher progressEventDispatcher =
              progressEventDispatcherFactory.create(
                  "loading to Docker daemon", imageTarball.getTotalLayerSize());
          ThrottledAccumulatingConsumer throttledProgressReporter =
              new ThrottledAccumulatingConsumer(progressEventDispatcher::dispatchProgress)) {
        // Load the image to docker daemon.
        eventHandlers.dispatch(
            LogEvent.debug(dockerClient.load(imageTarball, throttledProgressReporter)));

        // Tags the image with all the additional tags, skipping the one 'docker load' already
        // loaded.
        for (String tag : buildConfiguration.getAllTargetImageTags()) {
          if (tag.equals(targetImageReference.getTag())) {
            continue;
          }

          dockerClient.tag(targetImageReference, targetImageReference.withTag(tag));
        }

        return BuildResult.fromImage(builtImage, buildConfiguration.getTargetFormat());
      }
    }
  }
}
