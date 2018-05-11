/*
 * Copyright 2018 Google LLC. All Rights Reserved.
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
import com.google.cloud.tools.jib.http.Authorization;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

/** Pushes the container configuration. */
class PushContainerConfigurationStep implements Callable<ListenableFuture<BlobDescriptor>> {

  private static final String DESCRIPTION = "Pushing container configuration";

  private final BuildConfiguration buildConfiguration;
  private final ListenableFuture<Authorization> pushAuthorizationFuture;
  private final ListenableFuture<ListenableFuture<Image>> buildConfigurationFutureFuture;
  private final ListeningExecutorService listeningExecutorService;

  PushContainerConfigurationStep(
      BuildConfiguration buildConfiguration,
      ListenableFuture<Authorization> pushAuthorizationFuture,
      ListenableFuture<ListenableFuture<Image>> buildConfigurationFutureFuture,
      ListeningExecutorService listeningExecutorService) {
    this.buildConfiguration = buildConfiguration;
    this.pushAuthorizationFuture = pushAuthorizationFuture;
    this.buildConfigurationFutureFuture = buildConfigurationFutureFuture;
    this.listeningExecutorService = listeningExecutorService;
  }

  /** Depends on {@code buildConfigurationFutureFuture} and {@code pushAuthorizationFuture}. */
  @Override
  public ListenableFuture<BlobDescriptor> call() throws ExecutionException, InterruptedException {
    List<ListenableFuture<?>> afterBuildConfigurationFutureFutureDependencies = new ArrayList<>();
    afterBuildConfigurationFutureFutureDependencies.add(pushAuthorizationFuture);
    afterBuildConfigurationFutureFutureDependencies.add(
        NonBlockingFutures.get(buildConfigurationFutureFuture));
    return Futures.whenAllSucceed(afterBuildConfigurationFutureFutureDependencies)
        .call(this::afterBuildConfigurationFutureFuture, listeningExecutorService);
  }

  /**
   * Depends on {@code buildConfigurationFutureFuture.get()} and {@code pushAuthorizationFuture}.
   */
  private BlobDescriptor afterBuildConfigurationFutureFuture()
      throws ExecutionException, InterruptedException, IOException, RegistryException,
          LayerPropertyNotFoundException {
    try (Timer timer = new Timer(buildConfiguration.getBuildLogger(), DESCRIPTION)) {
      // TODO: Use PushBlobStep.
      // Pushes the container configuration.
      RegistryClient registryClient =
          new RegistryClient(
                  NonBlockingFutures.get(pushAuthorizationFuture),
                  buildConfiguration.getTargetImageRegistry(),
                  buildConfiguration.getTargetImageRepository())
              .setTimer(timer);

      Image image = NonBlockingFutures.get(NonBlockingFutures.get(buildConfigurationFutureFuture));
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
