/*
 * Copyright 2018 Google LLC. All rights reserved.
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
import com.google.cloud.tools.jib.cache.CachedLayer;
import com.google.cloud.tools.jib.configuration.BuildConfiguration;
import com.google.cloud.tools.jib.docker.ImageToTarballTranslator;
import com.google.cloud.tools.jib.image.Image;
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

public class WriteTarFileStep implements AsyncStep<Void>, Callable<Void> {

  private final Path outputPath;
  private final BuildConfiguration buildConfiguration;
  private final PullAndCacheBaseImageLayersStep pullAndCacheBaseImageLayersStep;
  private final ImmutableList<BuildAndCacheApplicationLayerStep> buildAndCacheApplicationLayerSteps;
  private final BuildImageStep buildImageStep;

  private final ListeningExecutorService listeningExecutorService;
  private final ListenableFuture<Void> listenableFuture;

  WriteTarFileStep(
      ListeningExecutorService listeningExecutorService,
      Path outputPath,
      BuildConfiguration buildConfiguration,
      PullAndCacheBaseImageLayersStep pullAndCacheBaseImageLayersStep,
      ImmutableList<BuildAndCacheApplicationLayerStep> buildAndCacheApplicationLayerSteps,
      BuildImageStep buildImageStep) {
    this.listeningExecutorService = listeningExecutorService;
    this.buildConfiguration = buildConfiguration;
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
  public ListenableFuture<Void> getFuture() {
    return listenableFuture;
  }

  @Override
  public Void call() throws ExecutionException, InterruptedException {
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
        .call(this::afterPushBaseImageLayerFuturesFuture, listeningExecutorService)
        .get();
  }

  private Void afterPushBaseImageLayerFuturesFuture() throws ExecutionException, IOException {
    Image<CachedLayer> image = NonBlockingSteps.get(NonBlockingSteps.get(buildImageStep));

    // Build the image to a tarball
    buildConfiguration.getBuildLogger().lifecycle("Building image to tar file...");
    Files.createDirectories(outputPath.getParent());
    try (OutputStream outputStream = new BufferedOutputStream(Files.newOutputStream(outputPath))) {
      new ImageToTarballTranslator(image)
          .toTarballBlob(buildConfiguration.getTargetImageReference())
          .writeTo(outputStream);
    }

    return null;
  }
}
