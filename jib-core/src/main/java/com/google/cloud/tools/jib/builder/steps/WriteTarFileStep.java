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

import com.google.cloud.tools.jib.async.AsyncStep;
import com.google.cloud.tools.jib.async.NonBlockingSteps;
import com.google.cloud.tools.jib.configuration.BuildConfiguration;
import com.google.cloud.tools.jib.docker.ImageToTarballTranslator;
import com.google.cloud.tools.jib.event.events.LogEvent;
import com.google.cloud.tools.jib.event.events.ProgressEvent;
import com.google.cloud.tools.jib.event.progress.Allocation;
import com.google.cloud.tools.jib.filesystem.FileOperations;
import com.google.cloud.tools.jib.image.Image;
import com.google.cloud.tools.jib.image.Layer;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

public class WriteTarFileStep implements AsyncStep<BuildResult>, Callable<BuildResult> {

  private final ListeningExecutorService listeningExecutorService;
  private final BuildConfiguration buildConfiguration;
  private final Allocation parentProgressAllocation;

  private final Path outputPath;
  private final PullAndCacheBaseImageLayersStep pullAndCacheBaseImageLayersStep;
  private final ImmutableList<BuildAndCacheApplicationLayerStep> buildAndCacheApplicationLayerSteps;
  private final BuildImageStep buildImageStep;

  private final ListenableFuture<BuildResult> listenableFuture;

  WriteTarFileStep(
      ListeningExecutorService listeningExecutorService,
      BuildConfiguration buildConfiguration,
      Allocation parentProgressAllocation,
      Path outputPath,
      PullAndCacheBaseImageLayersStep pullAndCacheBaseImageLayersStep,
      ImmutableList<BuildAndCacheApplicationLayerStep> buildAndCacheApplicationLayerSteps,
      BuildImageStep buildImageStep) {
    this.listeningExecutorService = listeningExecutorService;
    this.buildConfiguration = buildConfiguration;
    this.parentProgressAllocation = parentProgressAllocation;
    this.outputPath = outputPath;
    this.pullAndCacheBaseImageLayersStep = pullAndCacheBaseImageLayersStep;
    this.buildAndCacheApplicationLayerSteps = buildAndCacheApplicationLayerSteps;
    this.buildImageStep = buildImageStep;

    listenableFuture =
        Futures.whenAllSucceed(
                pullAndCacheBaseImageLayersStep.getFuture(), buildImageStep.getFuture())
            .call(this, listeningExecutorService);
  }

  @Override
  public ListenableFuture<BuildResult> getFuture() {
    return listenableFuture;
  }

  @Override
  public BuildResult call() throws ExecutionException, InterruptedException {
    ImmutableList.Builder<ListenableFuture<?>> dependenciesBuilder = ImmutableList.builder();
    for (PullAndCacheBaseImageLayerStep pullAndCacheBaseImageLayerStep :
        NonBlockingSteps.get(pullAndCacheBaseImageLayersStep)) {
      dependenciesBuilder.add(pullAndCacheBaseImageLayerStep.getFuture());
    }
    for (BuildAndCacheApplicationLayerStep buildAndCacheApplicationLayerStep :
        buildAndCacheApplicationLayerSteps) {
      dependenciesBuilder.add(buildAndCacheApplicationLayerStep.getFuture());
    }
    dependenciesBuilder.add(NonBlockingSteps.get(buildImageStep).getFuture());
    return Futures.whenAllSucceed(dependenciesBuilder.build())
        .call(this::writeTarFile, listeningExecutorService)
        .get();
  }

  private BuildResult writeTarFile() throws ExecutionException, IOException {
    buildConfiguration
        .getEventDispatcher()
        .dispatch(LogEvent.lifecycle("Building image to tar file..."));
    Allocation progressAllocation = parentProgressAllocation.newChild("Write to tar file", 1);
    buildConfiguration.getEventDispatcher().dispatch(new ProgressEvent(progressAllocation, 0));

    Image<Layer> image = NonBlockingSteps.get(NonBlockingSteps.get(buildImageStep));

    // Builds the image to a tarball.
    Files.createDirectories(outputPath.getParent());
    try (OutputStream outputStream =
        new BufferedOutputStream(FileOperations.newLockingOutputStream(outputPath))) {
      new ImageToTarballTranslator(image)
          .toTarballBlob(buildConfiguration.getTargetImageConfiguration().getImage())
          .writeTo(outputStream);
    }

    buildConfiguration.getEventDispatcher().dispatch(new ProgressEvent(progressAllocation, 1));

    return BuildResult.fromImage(image, buildConfiguration.getTargetFormat());
  }
}
