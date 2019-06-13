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

import com.google.cloud.tools.jib.api.LogEvent;
import com.google.cloud.tools.jib.async.AsyncDependencies;
import com.google.cloud.tools.jib.async.AsyncStep;
import com.google.cloud.tools.jib.async.NonBlockingSteps;
import com.google.cloud.tools.jib.builder.ProgressEventDispatcher;
import com.google.cloud.tools.jib.configuration.BuildConfiguration;
import com.google.cloud.tools.jib.docker.ImageTarball;
import com.google.cloud.tools.jib.filesystem.FileOperations;
import com.google.cloud.tools.jib.image.Image;
import com.google.common.collect.ImmutableList;
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
  private final ProgressEventDispatcher.Factory progressEventDispatcherFactory;

  private final Path outputPath;
  private final PullAndCacheBaseImageLayersStep pullAndCacheBaseImageLayersStep;
  private final ImmutableList<BuildAndCacheApplicationLayerStep> buildAndCacheApplicationLayerSteps;
  private final BuildImageStep buildImageStep;

  private final ListenableFuture<BuildResult> listenableFuture;

  WriteTarFileStep(
      ListeningExecutorService listeningExecutorService,
      BuildConfiguration buildConfiguration,
      ProgressEventDispatcher.Factory progressEventDispatcherFactory,
      Path outputPath,
      PullAndCacheBaseImageLayersStep pullAndCacheBaseImageLayersStep,
      ImmutableList<BuildAndCacheApplicationLayerStep> buildAndCacheApplicationLayerSteps,
      BuildImageStep buildImageStep) {
    this.listeningExecutorService = listeningExecutorService;
    this.buildConfiguration = buildConfiguration;
    this.progressEventDispatcherFactory = progressEventDispatcherFactory;
    this.outputPath = outputPath;
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
        .whenAllSucceed(this::writeTarFile)
        .get();
  }

  private BuildResult writeTarFile() throws ExecutionException, IOException {
    buildConfiguration
        .getEventHandlers()
        .dispatch(LogEvent.progress("Building image to tar file..."));

    try (ProgressEventDispatcher ignored =
        progressEventDispatcherFactory.create("writing to tar file", 1)) {
      Image image = NonBlockingSteps.get(NonBlockingSteps.get(buildImageStep));

      // Builds the image to a tarball.
      Files.createDirectories(outputPath.getParent());
      try (OutputStream outputStream =
          new BufferedOutputStream(FileOperations.newLockingOutputStream(outputPath))) {
        new ImageTarball(image, buildConfiguration.getTargetImageConfiguration().getImage())
            .writeTo(outputStream);
      }

      return BuildResult.fromImage(image, buildConfiguration.getTargetFormat());
    }
  }
}
