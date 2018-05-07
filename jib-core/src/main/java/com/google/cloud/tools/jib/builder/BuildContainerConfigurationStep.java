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
import com.google.cloud.tools.jib.image.Image;
import com.google.cloud.tools.jib.image.LayerPropertyNotFoundException;
import com.google.cloud.tools.jib.image.json.ImageToJsonTranslator;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/** Builds the container configuration. */
class BuildContainerConfigurationStep implements Callable<ListenableFuture<ImageToJsonTranslator>> {

  private static final String DESCRIPTION = "Building container configuration";

  private final BuildConfiguration buildConfiguration;
  private final ListeningExecutorService listeningExecutorService;
  private final ListenableFuture<List<ListenableFuture<CachedLayer>>>
      pullBaseImageLayerFuturesFuture;
  private final List<ListenableFuture<CachedLayer>> buildApplicationLayerFutures;
  private final List<String> entrypoint;

  BuildContainerConfigurationStep(
      BuildConfiguration buildConfiguration,
      ListeningExecutorService listeningExecutorService,
      ListenableFuture<List<ListenableFuture<CachedLayer>>> pullBaseImageLayerFuturesFuture,
      List<ListenableFuture<CachedLayer>> buildApplicationLayerFutures,
      List<String> entrypoint) {
    this.buildConfiguration = buildConfiguration;
    this.listeningExecutorService = listeningExecutorService;
    this.pullBaseImageLayerFuturesFuture = pullBaseImageLayerFuturesFuture;
    this.buildApplicationLayerFutures = buildApplicationLayerFutures;
    this.entrypoint = entrypoint;
  }

  /** Depends on {@code pullBaseImageLayerFuturesFuture}. */
  @Override
  public ListenableFuture<ImageToJsonTranslator> call()
      throws ExecutionException, InterruptedException {
    // TODO: This might need to belong in BuildImageSteps.
    List<ListenableFuture<?>> afterBaseImageLayerFuturesFutureDependencies = new ArrayList<>();
    afterBaseImageLayerFuturesFutureDependencies.addAll(
        NonBlockingFutures.get(pullBaseImageLayerFuturesFuture));
    afterBaseImageLayerFuturesFutureDependencies.addAll(buildApplicationLayerFutures);
    return Futures.whenAllSucceed(afterBaseImageLayerFuturesFutureDependencies)
        .call(this::afterBaseImageLayerFuturesFuture, listeningExecutorService);
  }

  /**
   * Depends on {@code pushAuthorizationFuture}, {@code pullBaseImageLayerFuturesFuture.get()}, and
   * {@code buildApplicationLayerFutures}.
   */
  private ImageToJsonTranslator afterBaseImageLayerFuturesFuture()
      throws ExecutionException, InterruptedException, LayerPropertyNotFoundException {
    try (Timer ignored = new Timer(buildConfiguration.getBuildLogger(), DESCRIPTION)) {
      // Constructs the image.
      Image image = new Image();
      for (Future<CachedLayer> cachedLayerFuture :
          NonBlockingFutures.get(pullBaseImageLayerFuturesFuture)) {
        image.addLayer(NonBlockingFutures.get(cachedLayerFuture));
      }
      for (Future<CachedLayer> cachedLayerFuture : buildApplicationLayerFutures) {
        image.addLayer(NonBlockingFutures.get(cachedLayerFuture));
      }
      image.setEnvironment(buildConfiguration.getEnvironment());
      image.setEntrypoint(entrypoint);

      // Gets the container configuration content descriptor.
      return new ImageToJsonTranslator(image);
    }
  }
}
