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
import com.google.cloud.tools.jib.builder.steps.PullBaseImageStep.BaseImageWithAuthorization;
import com.google.cloud.tools.jib.cache.Cache;
import com.google.cloud.tools.jib.configuration.BuildConfiguration;
import com.google.cloud.tools.jib.image.Layer;
import com.google.cloud.tools.jib.image.LayerPropertyNotFoundException;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

/** Pulls and caches the base image layers. */
class PullAndCacheBaseImageLayersStep
    implements AsyncStep<ImmutableList<PullAndCacheBaseImageLayerStep>>,
        Callable<ImmutableList<PullAndCacheBaseImageLayerStep>> {

  private static final String DESCRIPTION = "Setting up base image caching";

  private final BuildConfiguration buildConfiguration;
  private final Cache cache;
  private final PullBaseImageStep pullBaseImageStep;

  private final ListeningExecutorService listeningExecutorService;
  private final ListenableFuture<ImmutableList<PullAndCacheBaseImageLayerStep>> listenableFuture;

  PullAndCacheBaseImageLayersStep(
      ListeningExecutorService listeningExecutorService,
      BuildConfiguration buildConfiguration,
      Cache cache,
      PullBaseImageStep pullBaseImageStep) {
    this.listeningExecutorService = listeningExecutorService;
    this.buildConfiguration = buildConfiguration;
    this.cache = cache;
    this.pullBaseImageStep = pullBaseImageStep;

    listenableFuture =
        Futures.whenAllSucceed(pullBaseImageStep.getFuture()).call(this, listeningExecutorService);
  }

  @Override
  public ListenableFuture<ImmutableList<PullAndCacheBaseImageLayerStep>> getFuture() {
    return listenableFuture;
  }

  @Override
  public ImmutableList<PullAndCacheBaseImageLayerStep> call()
      throws ExecutionException, LayerPropertyNotFoundException {
    try (Timer ignored = new Timer(buildConfiguration.getBuildLogger(), DESCRIPTION)) {
      BaseImageWithAuthorization pullBaseImageStepResult = NonBlockingSteps.get(pullBaseImageStep);
      ImmutableList<Layer> baseImageLayers = pullBaseImageStepResult.getBaseImage().getLayers();

      ImmutableList.Builder<PullAndCacheBaseImageLayerStep> pullAndCacheBaseImageLayerStepsBuilder =
          ImmutableList.builderWithExpectedSize(baseImageLayers.size());
      for (Layer layer : baseImageLayers) {
        pullAndCacheBaseImageLayerStepsBuilder.add(
            new PullAndCacheBaseImageLayerStep(
                listeningExecutorService,
                buildConfiguration,
                cache,
                layer.getBlobDescriptor().getDigest(),
                pullBaseImageStepResult.getBaseImageAuthorization()));
      }

      return pullAndCacheBaseImageLayerStepsBuilder.build();
    }
  }
}
