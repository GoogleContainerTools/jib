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
import com.google.cloud.tools.jib.cache.Cache;
import com.google.cloud.tools.jib.image.Layer;
import com.google.cloud.tools.jib.image.LayerPropertyNotFoundException;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nullable;

/** Pulls and caches the base image layers. */
class PullAndCacheBaseImageLayersStep
    implements AsyncStep<ImmutableList<PullAndCacheBaseImageLayerStep>> {

  private static final String DESCRIPTION = "Setting up base image caching";

  private final BuildConfiguration buildConfiguration;
  private final Cache cache;
  private final AuthenticatePullStep authenticatePullStep;
  private final PullBaseImageStep pullBaseImageStep;

  private final ListeningExecutorService listeningExecutorService;

  @Nullable
  private ListenableFuture<ImmutableList<PullAndCacheBaseImageLayerStep>> listenableFuture;

  PullAndCacheBaseImageLayersStep(
      ListeningExecutorService listeningExecutorService,
      BuildConfiguration buildConfiguration,
      Cache cache,
      AuthenticatePullStep authenticatePullStep,
      PullBaseImageStep pullBaseImageStep) {
    this.listeningExecutorService = listeningExecutorService;
    this.buildConfiguration = buildConfiguration;
    this.cache = cache;
    this.authenticatePullStep = authenticatePullStep;
    this.pullBaseImageStep = pullBaseImageStep;
  }

  @Override
  public ListenableFuture<ImmutableList<PullAndCacheBaseImageLayerStep>> getFuture() {
    if (listenableFuture == null) {
      listenableFuture =
          Futures.whenAllSucceed(pullBaseImageStep.getFuture())
              .call(this, listeningExecutorService);
    }
    return listenableFuture;
  }

  /** Depends on {@code baseImageFuture}. */
  @Override
  public ImmutableList<PullAndCacheBaseImageLayerStep> call()
      throws ExecutionException, InterruptedException, LayerPropertyNotFoundException {
    try (Timer ignored = new Timer(buildConfiguration.getBuildLogger(), DESCRIPTION)) {
      ImmutableList<Layer> baseImageLayers = NonBlockingSteps.get(pullBaseImageStep).getLayers();

      ImmutableList.Builder<PullAndCacheBaseImageLayerStep> pullAndCacheBaseImageLayerStepsBuilder =
          ImmutableList.builderWithExpectedSize(baseImageLayers.size());
      for (Layer layer : baseImageLayers) {
        pullAndCacheBaseImageLayerStepsBuilder.add(
            new PullAndCacheBaseImageLayerStep(
                listeningExecutorService,
                buildConfiguration,
                cache,
                layer.getBlobDescriptor().getDigest(),
                authenticatePullStep));
      }

      return pullAndCacheBaseImageLayerStepsBuilder.build();
    }
  }
}
