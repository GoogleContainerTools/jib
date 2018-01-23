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

package com.google.cloud.tools.crepecake.builder;

import com.google.cloud.tools.crepecake.cache.CachedLayer;
import com.google.cloud.tools.crepecake.http.Authorization;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

class PushBaseImageLayersStep implements Callable<List<ListenableFuture<Void>>> {

  private final BuildConfiguration buildConfiguration;
  private final ListeningExecutorService listeningExecutorService;
  private final ListenableFuture<Authorization> pushAuthorizationFuture;
  private final List<ListenableFuture<CachedLayer>> pullBaseImageLayerFutures;

  PushBaseImageLayersStep(
      BuildConfiguration buildConfiguration,
      ListeningExecutorService listeningExecutorService,
      ListenableFuture<Authorization> pushAuthorizationFuture,
      List<ListenableFuture<CachedLayer>> pullBaseImageLayerFutures) {
    this.buildConfiguration = buildConfiguration;
    this.listeningExecutorService = listeningExecutorService;
    this.pushAuthorizationFuture = pushAuthorizationFuture;
    this.pullBaseImageLayerFutures = pullBaseImageLayerFutures;
  }

  @Override
  public List<ListenableFuture<Void>> call() {
    // Pushes the base image layers.
    List<ListenableFuture<Void>> pushBaseImageLayerFutures = new ArrayList<>();
    for (ListenableFuture<CachedLayer> pullBaseImageLayerFuture : pullBaseImageLayerFutures) {
      pushBaseImageLayerFutures.add(
          Futures.whenAllComplete(pullBaseImageLayerFuture)
              .call(
                  new PushBlobStep(
                      buildConfiguration, pushAuthorizationFuture, pullBaseImageLayerFuture),
                  listeningExecutorService));
    }

    return pushBaseImageLayerFutures;
  }
}
