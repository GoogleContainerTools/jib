/*
 * Copyright 2018 Google Inc.
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
import com.google.cloud.tools.jib.cache.CachedLayer;
import com.google.cloud.tools.jib.hash.CountingDigestOutputStream;
import com.google.cloud.tools.jib.http.Authorization;
import com.google.cloud.tools.jib.image.DuplicateLayerException;
import com.google.cloud.tools.jib.image.Image;
import com.google.cloud.tools.jib.image.LayerPropertyNotFoundException;
import com.google.cloud.tools.jib.image.json.ImageToJsonTranslator;
import com.google.cloud.tools.jib.registry.RegistryClient;
import com.google.cloud.tools.jib.registry.RegistryException;
import com.google.common.io.ByteStreams;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

class BuildAndPushContainerConfigurationStep
    implements Callable<NonBlockingListenableFuture<BlobDescriptor>> {

  private static final String DESCRIPTION = "Building container configuration";

  private final BuildConfiguration buildConfiguration;
  private final ListeningExecutorService listeningExecutorService;
  private final NonBlockingListenableFuture<Authorization> pushAuthorizationFuture;
  private final NonBlockingListenableFuture<List<NonBlockingListenableFuture<CachedLayer>>>
      pullBaseImageLayerFuturesFuture;
  private final List<NonBlockingListenableFuture<CachedLayer>> buildApplicationLayerFutures;
  private final List<String> entrypoint;

  BuildAndPushContainerConfigurationStep(
      BuildConfiguration buildConfiguration,
      ListeningExecutorService listeningExecutorService,
      NonBlockingListenableFuture<Authorization> pushAuthorizationFuture,
      NonBlockingListenableFuture<List<NonBlockingListenableFuture<CachedLayer>>>
          pullBaseImageLayerFuturesFuture,
      List<NonBlockingListenableFuture<CachedLayer>> buildApplicationLayerFutures,
      List<String> entrypoint) {
    this.buildConfiguration = buildConfiguration;
    this.listeningExecutorService = listeningExecutorService;
    this.pushAuthorizationFuture = pushAuthorizationFuture;
    this.pullBaseImageLayerFuturesFuture = pullBaseImageLayerFuturesFuture;
    this.buildApplicationLayerFutures = buildApplicationLayerFutures;
    this.entrypoint = entrypoint;
  }

  /** Depends on {@code pullBaseImageLayerFuturesFuture}. */
  @Override
  public NonBlockingListenableFuture<BlobDescriptor> call()
      throws ExecutionException, InterruptedException {
    // TODO: This might need to belong in BuildImageSteps.
    List<NonBlockingListenableFuture<?>> afterBaseImageLayerFuturesFutureDependencies =
        new ArrayList<>();
    afterBaseImageLayerFuturesFutureDependencies.add(pushAuthorizationFuture);
    afterBaseImageLayerFuturesFutureDependencies.addAll(pullBaseImageLayerFuturesFuture.get());
    afterBaseImageLayerFuturesFutureDependencies.addAll(buildApplicationLayerFutures);
    return new NonBlockingListenableFuture<>(
        Futures.whenAllSucceed(afterBaseImageLayerFuturesFutureDependencies)
            .call(this::afterBaseImageLayerFuturesFuture, listeningExecutorService));
  }

  /**
   * Depends on {@code pushAuthorizationFuture}, {@code pullBaseImageLayerFuturesFuture.get()}, and
   * {@code buildApplicationLayerFutures}.
   */
  private BlobDescriptor afterBaseImageLayerFuturesFuture()
      throws ExecutionException, InterruptedException, LayerPropertyNotFoundException,
          DuplicateLayerException, IOException, RegistryException {
    try (Timer timer = new Timer(buildConfiguration.getBuildLogger(), DESCRIPTION)) {
      RegistryClient registryClient =
          new RegistryClient(
                  pushAuthorizationFuture.get(),
                  buildConfiguration.getTargetServerUrl(),
                  buildConfiguration.getTargetImageName())
              .setTimer(timer);

      // Constructs the image.
      Image image = new Image();
      for (Future<CachedLayer> cachedLayerFuture : pullBaseImageLayerFuturesFuture.get()) {
        image.addLayer(cachedLayerFuture.get());
      }
      for (Future<CachedLayer> cachedLayerFuture : buildApplicationLayerFutures) {
        image.addLayer(cachedLayerFuture.get());
      }
      image.setEnvironment(buildConfiguration.getEnvironment());
      image.setEntrypoint(entrypoint);

      ImageToJsonTranslator imageToJsonTranslator = new ImageToJsonTranslator(image);

      // Gets the container configuration content descriptor.
      Blob containerConfigurationBlob = imageToJsonTranslator.getContainerConfigurationBlob();
      CountingDigestOutputStream digestOutputStream =
          new CountingDigestOutputStream(ByteStreams.nullOutputStream());
      containerConfigurationBlob.writeTo(digestOutputStream);
      BlobDescriptor containerConfigurationBlobDescriptor = digestOutputStream.toBlobDescriptor();

      timer.lap("Pushing container configuration");

      // TODO: Use PushBlobStep.
      // Pushes the container configuration.
      registryClient.pushBlob(
          containerConfigurationBlobDescriptor.getDigest(), containerConfigurationBlob);

      return containerConfigurationBlobDescriptor;
    }
  }
}
