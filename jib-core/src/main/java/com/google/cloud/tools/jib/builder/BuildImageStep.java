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
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/** Builds a model {@link Image}. */
class BuildImageStep implements Callable<ListenableFuture<Image>> {

  private static final String DESCRIPTION = "Building container configuration";

  private final BuildConfiguration buildConfiguration;
  private final ListeningExecutorService listeningExecutorService;
  private final ListenableFuture<ImmutableList<ListenableFuture<CachedLayer>>>
      pullBaseImageLayerFuturesFuture;
  private final ImmutableList<ListenableFuture<CachedLayer>> buildApplicationLayerFutures;
  private final ImmutableList<String> entrypoint;

  BuildImageStep(
      BuildConfiguration buildConfiguration,
      ListeningExecutorService listeningExecutorService,
      ListenableFuture<ImmutableList<ListenableFuture<CachedLayer>>>
          pullBaseImageLayerFuturesFuture,
      ImmutableList<ListenableFuture<CachedLayer>> buildApplicationLayerFutures,
      ImmutableList<String> entrypoint) {
    this.buildConfiguration = buildConfiguration;
    this.listeningExecutorService = listeningExecutorService;
    this.pullBaseImageLayerFuturesFuture = pullBaseImageLayerFuturesFuture;
    this.buildApplicationLayerFutures = buildApplicationLayerFutures;
    this.entrypoint = entrypoint;
  }

  /** Depends on {@code pullBaseImageLayerFuturesFuture}. */
  @Override
  public ListenableFuture<Image> call() throws ExecutionException, InterruptedException {
    // TODO: This might need to belong in BuildImageSteps.
    ImmutableList.Builder<ListenableFuture<?>> afterImageLayerFuturesFutureDependenciesBuilder =
        ImmutableList.builder();
    afterImageLayerFuturesFutureDependenciesBuilder.addAll(
        NonBlockingFutures.get(pullBaseImageLayerFuturesFuture));
    afterImageLayerFuturesFutureDependenciesBuilder.addAll(buildApplicationLayerFutures);
    return Futures.whenAllSucceed(afterImageLayerFuturesFutureDependenciesBuilder.build())
        .call(this::afterImageLayerFuturesFuture, listeningExecutorService);
  }

  /**
   * Depends on {@code pushAuthorizationFuture}, {@code pullBaseImageLayerFuturesFuture.get()}, and
   * {@code buildApplicationLayerFutures}.
   */
  private Image afterImageLayerFuturesFuture()
      throws ExecutionException, InterruptedException, LayerPropertyNotFoundException {
    try (Timer ignored = new Timer(buildConfiguration.getBuildLogger(), DESCRIPTION)) {
      // Constructs the image.
      Image.Builder imageBuilder = Image.builder();
      for (Future<CachedLayer> cachedLayerFuture :
          NonBlockingFutures.get(pullBaseImageLayerFuturesFuture)) {
        imageBuilder.addLayer(NonBlockingFutures.get(cachedLayerFuture));
      }
      for (Future<CachedLayer> cachedLayerFuture : buildApplicationLayerFutures) {
        imageBuilder.addLayer(NonBlockingFutures.get(cachedLayerFuture));
      }
      imageBuilder.setEnvironment(buildConfiguration.getEnvironment());
      imageBuilder.setEntrypoint(entrypoint);

      // Gets the container configuration content descriptor.
      return imageBuilder.build();
    }
  }
}
