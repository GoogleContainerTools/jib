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
import com.google.cloud.tools.jib.cache.CachedLayer;
import com.google.cloud.tools.jib.http.Authorization;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

class PushLayersStep implements Callable<List<ListenableFuture<Void>>> {

  private static final String DESCRIPTION = "Setting up to push layers";

  private final BuildConfiguration buildConfiguration;
  private final ListeningExecutorService listeningExecutorService;
  private final ListenableFuture<Authorization> pushAuthorizationFuture;
  private final ListenableFuture<List<ListenableFuture<CachedLayer>>> cachedLayerFuturesFuture;

  PushLayersStep(
      BuildConfiguration buildConfiguration,
      ListeningExecutorService listeningExecutorService,
      ListenableFuture<Authorization> pushAuthorizationFuture,
      ListenableFuture<List<ListenableFuture<CachedLayer>>> cachedLayerFuturesFuture) {
    this.buildConfiguration = buildConfiguration;
    this.listeningExecutorService = listeningExecutorService;
    this.pushAuthorizationFuture = pushAuthorizationFuture;
    this.cachedLayerFuturesFuture = cachedLayerFuturesFuture;
  }

  /** Depends on {@code cachedLayerFuturesFuture}. */
  @Override
  public List<ListenableFuture<Void>> call() throws ExecutionException, InterruptedException {
    try (Timer ignored = new Timer(buildConfiguration.getBuildLogger(), DESCRIPTION)) {
      // Pushes the image layers.
      List<ListenableFuture<Void>> pushLayerFutures = new ArrayList<>();
      for (ListenableFuture<CachedLayer> cachedLayerFuture :
          NonBlockingFutures.get(cachedLayerFuturesFuture)) {
        pushLayerFutures.add(
            Futures.whenAllComplete(pushAuthorizationFuture, cachedLayerFuture)
                .call(
                    new PushBlobStep(
                        buildConfiguration, pushAuthorizationFuture, cachedLayerFuture),
                    listeningExecutorService));
      }

      return pushLayerFutures;
    }
  }
}
