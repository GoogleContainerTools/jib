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

import com.google.cloud.tools.jib.Timer;
import com.google.cloud.tools.jib.async.AsyncStep;
import com.google.cloud.tools.jib.async.NonBlockingSteps;
import com.google.cloud.tools.jib.cache.CachedLayer;
import com.google.cloud.tools.jib.configuration.BuildConfiguration;
import com.google.cloud.tools.jib.image.Image;
import com.google.cloud.tools.jib.image.LayerPropertyNotFoundException;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

/** Builds a model {@link Image}. */
class BuildImageStep
    implements AsyncStep<AsyncStep<Image<CachedLayer>>>, Callable<AsyncStep<Image<CachedLayer>>> {

  private static final String DESCRIPTION = "Building container configuration";

  private final BuildConfiguration buildConfiguration;
  private final PullAndCacheBaseImageLayersStep pullAndCacheBaseImageLayersStep;
  private final ImmutableList<BuildAndCacheApplicationLayerStep> buildAndCacheApplicationLayerSteps;

  private final ListeningExecutorService listeningExecutorService;
  private final ListenableFuture<AsyncStep<Image<CachedLayer>>> listenableFuture;

  BuildImageStep(
      ListeningExecutorService listeningExecutorService,
      BuildConfiguration buildConfiguration,
      PullAndCacheBaseImageLayersStep pullAndCacheBaseImageLayersStep,
      ImmutableList<BuildAndCacheApplicationLayerStep> buildAndCacheApplicationLayerSteps) {
    this.listeningExecutorService = listeningExecutorService;
    this.buildConfiguration = buildConfiguration;
    this.pullAndCacheBaseImageLayersStep = pullAndCacheBaseImageLayersStep;
    this.buildAndCacheApplicationLayerSteps = buildAndCacheApplicationLayerSteps;

    listenableFuture =
        Futures.whenAllSucceed(pullAndCacheBaseImageLayersStep.getFuture())
            .call(this, listeningExecutorService);
  }

  @Override
  public ListenableFuture<AsyncStep<Image<CachedLayer>>> getFuture() {
    return listenableFuture;
  }

  @Override
  public AsyncStep<Image<CachedLayer>> call() throws ExecutionException {
    List<ListenableFuture<?>> dependencies = new ArrayList<>();

    for (PullAndCacheBaseImageLayerStep pullAndCacheBaseImageLayerStep :
        NonBlockingSteps.get(pullAndCacheBaseImageLayersStep)) {
      dependencies.add(pullAndCacheBaseImageLayerStep.getFuture());
    }
    for (BuildAndCacheApplicationLayerStep buildAndCacheApplicationLayerStep :
        buildAndCacheApplicationLayerSteps) {
      dependencies.add(buildAndCacheApplicationLayerStep.getFuture());
    }
    ListenableFuture<Image<CachedLayer>> future =
        Futures.whenAllSucceed(dependencies)
            .call(this::afterCachedLayersSteps, listeningExecutorService);
    return () -> future;
  }

  private Image<CachedLayer> afterCachedLayersSteps()
      throws ExecutionException, LayerPropertyNotFoundException {
    try (Timer ignored = new Timer(buildConfiguration.getBuildLogger(), DESCRIPTION)) {
      // Constructs the image.
      Image.Builder<CachedLayer> imageBuilder = Image.builder();
      for (PullAndCacheBaseImageLayerStep pullAndCacheBaseImageLayerStep :
          NonBlockingSteps.get(pullAndCacheBaseImageLayersStep)) {
        imageBuilder.addLayer(NonBlockingSteps.get(pullAndCacheBaseImageLayerStep));
      }
      for (BuildAndCacheApplicationLayerStep buildAndCacheApplicationLayerStep :
          buildAndCacheApplicationLayerSteps) {
        imageBuilder.addLayer(NonBlockingSteps.get(buildAndCacheApplicationLayerStep));
      }
      imageBuilder.setCreated(buildConfiguration.getCreationTime());
      imageBuilder.setEnvironment(buildConfiguration.getEnvironment());
      imageBuilder.setEntrypoint(buildConfiguration.getEntrypoint());
      imageBuilder.setJavaArguments(buildConfiguration.getJavaArguments());
      imageBuilder.setExposedPorts(buildConfiguration.getExposedPorts());

      // Gets the container configuration content descriptor.
      return imageBuilder.build();
    }
  }
}
