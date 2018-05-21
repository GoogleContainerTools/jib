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
import com.google.cloud.tools.jib.image.DescriptorDigest;
import com.google.cloud.tools.jib.registry.RegistryClient;
import com.google.cloud.tools.jib.registry.RegistryException;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.io.IOException;
import java.util.concurrent.ExecutionException;

/** Pushes a BLOB to the target registry. */
class PushBlobStep implements AsyncStep<Void> {

  private static final String DESCRIPTION = "Pushing BLOB ";

  private final BuildConfiguration buildConfiguration;
  private final AuthenticatePushStep authenticatePushStep;
  private final AsyncStep<CachedLayer> cachedLayerStep;

  private final ListenableFuture<Void> listenableFuture;

  PushBlobStep(
      ListeningExecutorService listeningExecutorService,
      BuildConfiguration buildConfiguration,
      AuthenticatePushStep authenticatePushStep,
      AsyncStep<CachedLayer> cachedLayerStep) {
    this.buildConfiguration = buildConfiguration;
    this.authenticatePushStep = authenticatePushStep;
    this.cachedLayerStep = cachedLayerStep;

    listenableFuture =
        Futures.whenAllSucceed(authenticatePushStep.getFuture(), cachedLayerStep.getFuture())
            .call(this, listeningExecutorService);
  }

  @Override
  public ListenableFuture<Void> getFuture() {
    return listenableFuture;
  }

  @Override
  public Void call()
      throws IOException, RegistryException, ExecutionException, InterruptedException {
    CachedLayer layer = NonBlockingSteps.get(cachedLayerStep);
    DescriptorDigest layerDigest = layer.getBlobDescriptor().getDigest();

    try (Timer timer = new Timer(buildConfiguration.getBuildLogger(), DESCRIPTION + layerDigest)) {
      RegistryClient registryClient =
          new RegistryClient(
                  NonBlockingSteps.get(authenticatePushStep),
                  buildConfiguration.getTargetImageRegistry(),
                  buildConfiguration.getTargetImageRepository())
              .setTimer(timer);

      if (registryClient.checkBlob(layerDigest) != null) {
        buildConfiguration
            .getBuildLogger()
            .info("BLOB : " + layerDigest + " already exists on registry");
        return null;
      }

      registryClient.pushBlob(layerDigest, layer.getBlob());

      return null;
    }
  }
}
