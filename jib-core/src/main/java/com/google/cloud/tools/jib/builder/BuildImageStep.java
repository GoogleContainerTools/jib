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

package com.google.cloud.tools.jib.builder;

import com.google.cloud.tools.jib.Timer;
import com.google.cloud.tools.jib.image.Image;
import com.google.cloud.tools.jib.image.LayerPropertyNotFoundException;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nullable;

/** Builds a model {@link Image}. */
// TODO: Change ListenableFuture to AsyncStep
class BuildImageStep implements AsyncStep<ListenableFuture<Image>> {

  private static final String DESCRIPTION = "Building container configuration";

  private final BuildConfiguration buildConfiguration;
  private final PullAndCacheBaseImageLayersStep pullAndCacheBaseImageLayersStep;
  private final ImmutableList<BuildAndCacheApplicationLayerStep> buildAndCacheApplicationLayerSteps;
  private final ImmutableList<String> entrypoint;

  private final ListeningExecutorService listeningExecutorService;
  @Nullable private ListenableFuture<ListenableFuture<Image>> listenableFuture;

  BuildImageStep(
      ListeningExecutorService listeningExecutorService,
      BuildConfiguration buildConfiguration,
      PullAndCacheBaseImageLayersStep pullAndCacheBaseImageLayersStep,
      ImmutableList<BuildAndCacheApplicationLayerStep> buildAndCacheApplicationLayerSteps,
      ImmutableList<String> entrypoint) {
    this.listeningExecutorService = listeningExecutorService;
    this.buildConfiguration = buildConfiguration;
    this.pullAndCacheBaseImageLayersStep = pullAndCacheBaseImageLayersStep;
    this.buildAndCacheApplicationLayerSteps = buildAndCacheApplicationLayerSteps;
    this.entrypoint = entrypoint;
  }

  @Override
  public ListenableFuture<ListenableFuture<Image>> getFuture() {
    if (listenableFuture == null) {
      listenableFuture =
          Futures.whenAllSucceed(pullAndCacheBaseImageLayersStep.getFuture())
              .call(this, listeningExecutorService);
    }
    return listenableFuture;
  }

  @Override
  public ListenableFuture<Image> call() throws ExecutionException {
    List<ListenableFuture<?>> dependencies = new ArrayList<>();

    for (PullAndCacheBaseImageLayerStep pullAndCacheBaseImageLayerStep :
        NonBlockingSteps.get(pullAndCacheBaseImageLayersStep)) {
      dependencies.add(pullAndCacheBaseImageLayerStep.getFuture());
    }
    for (BuildAndCacheApplicationLayerStep buildAndCacheApplicationLayerStep :
        buildAndCacheApplicationLayerSteps) {
      dependencies.add(buildAndCacheApplicationLayerStep.getFuture());
    }
    return Futures.whenAllSucceed(dependencies)
        .call(this::afterCachedLayersSteps, listeningExecutorService);
  }

  private Image afterCachedLayersSteps() throws ExecutionException, LayerPropertyNotFoundException {
    try (Timer ignored = new Timer(buildConfiguration.getBuildLogger(), DESCRIPTION)) {
      // Constructs the image.
      Image.Builder imageBuilder = Image.builder();
      for (PullAndCacheBaseImageLayerStep pullAndCacheBaseImageLayerStep :
          NonBlockingSteps.get(pullAndCacheBaseImageLayersStep)) {
        imageBuilder.addLayer(NonBlockingSteps.get(pullAndCacheBaseImageLayerStep));
      }
      for (BuildAndCacheApplicationLayerStep buildAndCacheApplicationLayerStep :
          buildAndCacheApplicationLayerSteps) {
        imageBuilder.addLayer(NonBlockingSteps.get(buildAndCacheApplicationLayerStep));
      }
      imageBuilder.setEnvironment(buildConfiguration.getEnvironment());
      imageBuilder.setEntrypoint(entrypoint);

      // Gets the container configuration content descriptor.
      return imageBuilder.build();
    }
  }
}
