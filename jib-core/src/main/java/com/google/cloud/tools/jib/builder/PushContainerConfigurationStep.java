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
import com.google.cloud.tools.jib.blob.Blob;
import com.google.cloud.tools.jib.blob.BlobDescriptor;
import com.google.cloud.tools.jib.hash.CountingDigestOutputStream;
import com.google.cloud.tools.jib.image.Image;
import com.google.cloud.tools.jib.image.LayerPropertyNotFoundException;
import com.google.cloud.tools.jib.image.json.ImageToJsonTranslator;
import com.google.cloud.tools.jib.registry.RegistryClient;
import com.google.cloud.tools.jib.registry.RegistryException;
import com.google.common.io.ByteStreams;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nullable;

/** Pushes the container configuration. */
class PushContainerConfigurationStep implements AsyncStep<ListenableFuture<BlobDescriptor>> {

  private static final String DESCRIPTION = "Pushing container configuration";

  private final BuildConfiguration buildConfiguration;
  private final AuthenticatePushStep authenticatePushStep;
  private final BuildImageStep buildImageStep;

  private final ListeningExecutorService listeningExecutorService;
  @Nullable private ListenableFuture<ListenableFuture<BlobDescriptor>> listenableFuture;

  PushContainerConfigurationStep(
      ListeningExecutorService listeningExecutorService,
      BuildConfiguration buildConfiguration,
      AuthenticatePushStep authenticatePushStep,
      BuildImageStep buildImageStep) {
    this.listeningExecutorService = listeningExecutorService;
    this.buildConfiguration = buildConfiguration;
    this.authenticatePushStep = authenticatePushStep;
    this.buildImageStep = buildImageStep;
  }

  @Override
  public ListenableFuture<ListenableFuture<BlobDescriptor>> getFuture() {
    if (listenableFuture == null) {
      listenableFuture =
          Futures.whenAllSucceed(buildImageStep.getFuture()).call(this, listeningExecutorService);
    }
    return listenableFuture;
  }

  @Override
  public ListenableFuture<BlobDescriptor> call() throws ExecutionException, InterruptedException {
    return Futures.whenAllSucceed(
            authenticatePushStep.getFuture(), NonBlockingSteps.get(buildImageStep))
        .call(this::afterBuildConfigurationFutureFuture, listeningExecutorService);
  }

  private BlobDescriptor afterBuildConfigurationFutureFuture()
      throws ExecutionException, InterruptedException, IOException, RegistryException,
          LayerPropertyNotFoundException {
    try (Timer timer = new Timer(buildConfiguration.getBuildLogger(), DESCRIPTION)) {
      // TODO: Use PushBlobStep.
      // Pushes the container configuration.
      RegistryClient registryClient =
          new RegistryClient(
                  NonBlockingSteps.get(authenticatePushStep),
                  buildConfiguration.getTargetImageRegistry(),
                  buildConfiguration.getTargetImageRepository())
              .setTimer(timer);

      Image image = NonBlockingFutures.get(NonBlockingSteps.get(buildImageStep));
      Blob containerConfigurationBlob =
          new ImageToJsonTranslator(image).getContainerConfigurationBlob();
      CountingDigestOutputStream digestOutputStream =
          new CountingDigestOutputStream(ByteStreams.nullOutputStream());
      containerConfigurationBlob.writeTo(digestOutputStream);

      BlobDescriptor descriptor = digestOutputStream.toBlobDescriptor();
      registryClient.pushBlob(descriptor.getDigest(), containerConfigurationBlob);
      return descriptor;
    }
  }
}
