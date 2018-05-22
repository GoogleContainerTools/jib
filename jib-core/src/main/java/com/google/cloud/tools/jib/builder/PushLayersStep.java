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
import com.google.cloud.tools.jib.cache.CachedLayer;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.util.concurrent.ExecutionException;

class PushLayersStep implements AsyncStep<ImmutableList<PushBlobStep>> {

  private static final String DESCRIPTION = "Setting up to push layers";

  private final BuildConfiguration buildConfiguration;
  private final AuthenticatePushStep authenticatePushStep;
  private final AsyncStep<? extends ImmutableList<? extends AsyncStep<CachedLayer>>>
      cachedLayerStepsStep;

  private final ListeningExecutorService listeningExecutorService;
  private final ListenableFuture<ImmutableList<PushBlobStep>> listenableFuture;

  PushLayersStep(
      ListeningExecutorService listeningExecutorService,
      BuildConfiguration buildConfiguration,
      AuthenticatePushStep authenticatePushStep,
      AsyncStep<? extends ImmutableList<? extends AsyncStep<CachedLayer>>> cachedLayerStepsStep) {
    this.listeningExecutorService = listeningExecutorService;
    this.buildConfiguration = buildConfiguration;
    this.authenticatePushStep = authenticatePushStep;
    this.cachedLayerStepsStep = cachedLayerStepsStep;

    listenableFuture =
        Futures.whenAllSucceed(cachedLayerStepsStep.getFuture())
            .call(this, listeningExecutorService);
  }

  @Override
  public ListenableFuture<ImmutableList<PushBlobStep>> getFuture() {
    return listenableFuture;
  }

  @Override
  public ImmutableList<PushBlobStep> call() throws ExecutionException {
    try (Timer ignored = new Timer(buildConfiguration.getBuildLogger(), DESCRIPTION)) {
      ImmutableList<? extends AsyncStep<CachedLayer>> cachedLayerSteps =
          NonBlockingSteps.get(cachedLayerStepsStep);

      // Pushes the image layers.
      ImmutableList.Builder<PushBlobStep> pushBlobStepsBuilder = ImmutableList.builder();
      for (AsyncStep<CachedLayer> cachedLayerStep : cachedLayerSteps) {
        pushBlobStepsBuilder.add(
            new PushBlobStep(
                listeningExecutorService,
                buildConfiguration,
                authenticatePushStep,
                cachedLayerStep));
      }

      return pushBlobStepsBuilder.build();
    }
  }
}
