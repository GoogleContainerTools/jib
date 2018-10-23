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
import com.google.cloud.tools.jib.blob.BlobDescriptor;
import com.google.cloud.tools.jib.builder.TimerEventDispatcher;
import com.google.cloud.tools.jib.cache.CachedLayer;
import com.google.cloud.tools.jib.configuration.BuildConfiguration;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

class PushLayersStep
    implements AsyncStep<ImmutableList<AsyncStep<PushBlobStep>>>,
        Callable<ImmutableList<AsyncStep<PushBlobStep>>> {

  private static final String DESCRIPTION = "Setting up to push layers";

  private final BuildConfiguration buildConfiguration;
  private final AuthenticatePushStep authenticatePushStep;
  private final AsyncStep<? extends ImmutableList<? extends AsyncStep<? extends CachedLayer>>>
      cachedLayerStep;

  private final ListeningExecutorService listeningExecutorService;
  private final ListenableFuture<ImmutableList<AsyncStep<PushBlobStep>>> listenableFuture;

  PushLayersStep(
      ListeningExecutorService listeningExecutorService,
      BuildConfiguration buildConfiguration,
      AuthenticatePushStep authenticatePushStep,
      AsyncStep<? extends ImmutableList<? extends AsyncStep<? extends CachedLayer>>>
          cachedLayerStep) {
    this.listeningExecutorService = listeningExecutorService;
    this.buildConfiguration = buildConfiguration;
    this.authenticatePushStep = authenticatePushStep;
    this.cachedLayerStep = cachedLayerStep;

    listenableFuture =
        Futures.whenAllSucceed(cachedLayerStep.getFuture()).call(this, listeningExecutorService);
  }

  @Override
  public ListenableFuture<ImmutableList<AsyncStep<PushBlobStep>>> getFuture() {
    return listenableFuture;
  }

  @Override
  public ImmutableList<AsyncStep<PushBlobStep>> call() throws ExecutionException {
    try (TimerEventDispatcher ignored =
        new TimerEventDispatcher(buildConfiguration.getEventDispatcher(), DESCRIPTION)) {
      ImmutableList<? extends AsyncStep<? extends CachedLayer>> cachedLayer =
          NonBlockingSteps.get(cachedLayerStep);

      // Constructs a PushBlobStep for each layer.
      ImmutableList.Builder<AsyncStep<PushBlobStep>> pushBlobStepsBuilder = ImmutableList.builder();
      for (AsyncStep<? extends CachedLayer> cachedLayerStep : cachedLayer) {
        ListenableFuture<PushBlobStep> pushBlobStepFuture =
            Futures.whenAllSucceed(cachedLayerStep.getFuture())
                .call(() -> makePushBlobStep(cachedLayerStep), listeningExecutorService);
        pushBlobStepsBuilder.add(() -> pushBlobStepFuture);
      }

      return pushBlobStepsBuilder.build();
    }
  }

  private PushBlobStep makePushBlobStep(AsyncStep<? extends CachedLayer> cachedLayerStep)
      throws ExecutionException {
    CachedLayer cachedLayer = NonBlockingSteps.get(cachedLayerStep);
    return new PushBlobStep(
        listeningExecutorService,
        buildConfiguration,
        authenticatePushStep,
        new BlobDescriptor(cachedLayer.getSize(), cachedLayer.getDigest()),
        cachedLayer.getBlob());
  }
}
