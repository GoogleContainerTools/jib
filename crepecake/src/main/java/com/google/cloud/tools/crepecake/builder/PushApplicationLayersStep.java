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
import com.google.cloud.tools.crepecake.image.ImageLayers;
import com.google.cloud.tools.crepecake.registry.RegistryClient;
import com.google.cloud.tools.crepecake.registry.RegistryException;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

// TODO: First check for existence.
/** Pushes the application layers to the target registry. */
class PushApplicationLayersStep implements Callable<Void> {

  private final ListeningExecutorService listeningExecutorService;
  private final BuildConfiguration buildConfiguration;
  private final Authorization pushAuthorization;
  private final ImageLayers<CachedLayer> applicationLayers;

  PushApplicationLayersStep(
      ListeningExecutorService listeningExecutorService,
      BuildConfiguration buildConfiguration,
      Authorization pushAuthorization,
      ImageLayers<CachedLayer> applicationLayers) {
    this.listeningExecutorService = listeningExecutorService;
    this.buildConfiguration = buildConfiguration;
    this.pushAuthorization = pushAuthorization;
    this.applicationLayers = applicationLayers;
  }

  @Override
  public Void call()
      throws IOException, RegistryException, ExecutionException, InterruptedException {
    RegistryClient registryClient =
        new RegistryClient(
            pushAuthorization,
            buildConfiguration.getTargetServerUrl(),
            buildConfiguration.getTargetImageName());

    // Pushes the application layers.
    List<ListenableFuture<Void>> pushBlobFutures = new ArrayList<>();
    for (CachedLayer layer : applicationLayers) {
      pushBlobFutures.add(
          listeningExecutorService.submit(
              new PushBlobStep(
                  registryClient, layer.getBlob(), layer.getBlobDescriptor().getDigest())));
    }

    for (ListenableFuture<Void> pushBlobFuture : pushBlobFutures) {
      pushBlobFuture.get();
    }

    return null;
  }
}
