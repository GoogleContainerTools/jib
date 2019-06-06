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
import com.google.cloud.tools.jib.async.AsyncDependencies;
import com.google.cloud.tools.jib.async.AsyncStep;
import com.google.cloud.tools.jib.async.NonBlockingSteps;
import com.google.cloud.tools.jib.builder.ProgressEventDispatcher;
import com.google.cloud.tools.jib.configuration.BuildConfiguration;
import com.google.cloud.tools.jib.docker.DockerClient;
import com.google.cloud.tools.jib.docker.ImageTarball;
import com.google.cloud.tools.jib.image.Image;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

/** Adds image layers to a tarball and loads into Docker daemon. */
class LoadDockerStep implements AsyncStep<BuildResult>, Callable<BuildResult> {

  private final ListeningExecutorService listeningExecutorService;
  private final BuildConfiguration buildConfiguration;
  private final ProgressEventDispatcher.Factory progressEventDispatcherFactory;

  private final DockerClient dockerClient;

  private final PullAndCacheBaseImageLayersStep pullAndCacheBaseImageLayersStep;
  private final ImmutableList<BuildAndCacheApplicationLayerStep> buildAndCacheApplicationLayerSteps;
  private final BuildImageStep buildImageStep;

  private final ListenableFuture<BuildResult> listenableFuture;

  LoadDockerStep(
      ListeningExecutorService listeningExecutorService,
      BuildConfiguration buildConfiguration,
      ProgressEventDispatcher.Factory progressEventDispatcherFactory,
      DockerClient dockerClient,
      PullAndCacheBaseImageLayersStep pullAndCacheBaseImageLayersStep,
      ImmutableList<BuildAndCacheApplicationLayerStep> buildAndCacheApplicationLayerSteps,
      BuildImageStep buildImageStep) {
    this.listeningExecutorService = listeningExecutorService;
    this.buildConfiguration = buildConfiguration;
    this.progressEventDispatcherFactory = progressEventDispatcherFactory;
    this.dockerClient = dockerClient;
    this.pullAndCacheBaseImageLayersStep = pullAndCacheBaseImageLayersStep;
    this.buildAndCacheApplicationLayerSteps = buildAndCacheApplicationLayerSteps;
    this.buildImageStep = buildImageStep;

    listenableFuture =
        AsyncDependencies.using(listeningExecutorService)
            .addStep(pullAndCacheBaseImageLayersStep)
            .addStep(buildImageStep)
            .whenAllSucceed(this);
  }

  @Override
  public ListenableFuture<BuildResult> getFuture() {
    return listenableFuture;
  }

  @Override
  public BuildResult call() throws ExecutionException, InterruptedException {
    return AsyncDependencies.using(listeningExecutorService)
        .addSteps(NonBlockingSteps.get(pullAndCacheBaseImageLayersStep))
        .addSteps(buildAndCacheApplicationLayerSteps)
        .addStep(NonBlockingSteps.get(buildImageStep))
        .whenAllSucceed(this::afterPushBaseImageLayerFuturesFuture)
        .get();
  }

  private BuildResult afterPushBaseImageLayerFuturesFuture()
      throws ExecutionException, InterruptedException, IOException {
    buildConfiguration
        .getEventHandlers()
        .dispatch(LogEvent.progress("Loading to Docker daemon..."));

    try (ProgressEventDispatcher ignored =
        progressEventDispatcherFactory.create("loading to Docker daemon", 1)) {
      Image image = NonBlockingSteps.get(NonBlockingSteps.get(buildImageStep));
      ImageReference targetImageReference =
          buildConfiguration.getTargetImageConfiguration().getImage();

      // Load the image to docker daemon.
      buildConfiguration
          .getEventHandlers()
          .dispatch(
              LogEvent.debug(dockerClient.load(new ImageTarball(image, targetImageReference))));

      // Tags the image with all the additional tags, skipping the one 'docker load' already loaded.
      for (String tag : buildConfiguration.getAllTargetImageTags()) {
        if (tag.equals(targetImageReference.getTag())) {
          continue;
        }

        dockerClient.tag(targetImageReference, targetImageReference.withTag(tag));
      }

      return BuildResult.fromImage(image, buildConfiguration.getTargetFormat());
    }
  }
}
