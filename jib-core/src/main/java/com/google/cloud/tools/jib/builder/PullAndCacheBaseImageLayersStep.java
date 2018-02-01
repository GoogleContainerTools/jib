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
import com.google.cloud.tools.jib.cache.Cache;
import com.google.cloud.tools.jib.cache.CachedLayer;
import com.google.cloud.tools.jib.http.Authorization;
import com.google.cloud.tools.jib.image.Image;
import com.google.cloud.tools.jib.image.Layer;
import com.google.cloud.tools.jib.image.LayerPropertyNotFoundException;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

/** Pulls and caches the base image layers. */
class PullAndCacheBaseImageLayersStep implements Callable<List<ListenableFuture<CachedLayer>>> {

  private static final String DESCRIPTION = "Setting up base image caching";

  private final BuildConfiguration buildConfiguration;
  private final Cache cache;
  private final ListeningExecutorService listeningExecutorService;
  private final ListenableFuture<Authorization> pullAuthorizationFuture;
  private final ListenableFuture<Image> baseImageFuture;

  PullAndCacheBaseImageLayersStep(
      BuildConfiguration buildConfiguration,
      Cache cache,
      ListeningExecutorService listeningExecutorService,
      ListenableFuture<Authorization> pullAuthorizationFuture,
      ListenableFuture<Image> baseImageFuture) {
    this.buildConfiguration = buildConfiguration;
    this.cache = cache;
    this.listeningExecutorService = listeningExecutorService;
    this.pullAuthorizationFuture = pullAuthorizationFuture;
    this.baseImageFuture = baseImageFuture;
  }

  /** Depends on {@code baseImageFuture}. */
  @Override
  public List<ListenableFuture<CachedLayer>> call()
      throws ExecutionException, InterruptedException, LayerPropertyNotFoundException {
    try (Timer ignored = new Timer(buildConfiguration.getBuildLogger(), DESCRIPTION)) {
      List<ListenableFuture<CachedLayer>> pullAndCacheBaseImageLayerFutures = new ArrayList<>();
      for (Layer layer : NonBlockingFutures.get(baseImageFuture).getLayers()) {
        pullAndCacheBaseImageLayerFutures.add(
            Futures.whenAllSucceed(pullAuthorizationFuture)
                .call(
                    new PullAndCacheBaseImageLayerStep(
                        buildConfiguration,
                        cache,
                        layer.getBlobDescriptor().getDigest(),
                        pullAuthorizationFuture),
                    listeningExecutorService));
      }

      return pullAndCacheBaseImageLayerFutures;
    }
  }
}
