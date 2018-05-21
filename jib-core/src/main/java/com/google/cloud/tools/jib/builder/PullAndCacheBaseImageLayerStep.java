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
import com.google.cloud.tools.jib.cache.CacheReader;
import com.google.cloud.tools.jib.cache.CacheWriter;
import com.google.cloud.tools.jib.cache.CachedLayer;
import com.google.cloud.tools.jib.image.DescriptorDigest;
import com.google.cloud.tools.jib.image.LayerPropertyNotFoundException;
import com.google.cloud.tools.jib.registry.RegistryClient;
import com.google.cloud.tools.jib.registry.RegistryException;
import com.google.common.io.CountingOutputStream;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nullable;

/** Pulls and caches a single base image layer. */
class PullAndCacheBaseImageLayerStep implements AsyncStep<CachedLayer> {

  private static final String DESCRIPTION = "Pulling base image layer %s";

  private final BuildConfiguration buildConfiguration;
  private final Cache cache;
  private final DescriptorDigest layerDigest;
  private final AuthenticatePullStep authenticatePullStep;

  private final ListeningExecutorService listeningExecutorService;
  @Nullable private ListenableFuture<CachedLayer> listenableFuture;

  PullAndCacheBaseImageLayerStep(
      ListeningExecutorService listeningExecutorService,
      BuildConfiguration buildConfiguration,
      Cache cache,
      DescriptorDigest layerDigest,
      AuthenticatePullStep authenticatePullStep) {
    this.listeningExecutorService = listeningExecutorService;
    this.buildConfiguration = buildConfiguration;
    this.cache = cache;
    this.layerDigest = layerDigest;
    this.authenticatePullStep = authenticatePullStep;
  }

  @Override
  public ListenableFuture<CachedLayer> getFuture() {
    if (listenableFuture == null) {
      listenableFuture =
          Futures.whenAllSucceed(authenticatePullStep.getFuture())
              .call(this, listeningExecutorService);
    }
    return listenableFuture;
  }

  /** Depends on {@code pullAuthorizationFuture}. */
  @Override
  public CachedLayer call()
      throws IOException, RegistryException, LayerPropertyNotFoundException, ExecutionException,
          InterruptedException {
    try (Timer ignored =
        new Timer(buildConfiguration.getBuildLogger(), String.format(DESCRIPTION, layerDigest))) {
      RegistryClient registryClient =
          new RegistryClient(
              NonBlockingSteps.get(authenticatePullStep),
              buildConfiguration.getBaseImageRegistry(),
              buildConfiguration.getBaseImageRepository());

      // Checks if the layer already exists in the cache.
      CachedLayer cachedLayer = new CacheReader(cache).getLayer(layerDigest);
      if (cachedLayer != null) {
        return cachedLayer;
      }

      CacheWriter cacheWriter = new CacheWriter(cache);
      CountingOutputStream layerOutputStream = cacheWriter.getLayerOutputStream(layerDigest);
      registryClient.pullBlob(layerDigest, layerOutputStream);
      return cacheWriter.getCachedLayer(layerDigest, layerOutputStream);
    }
  }
}
