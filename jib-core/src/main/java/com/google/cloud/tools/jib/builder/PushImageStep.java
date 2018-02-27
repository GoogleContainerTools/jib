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
import com.google.cloud.tools.jib.blob.BlobDescriptor;
import com.google.cloud.tools.jib.cache.CachedLayer;
import com.google.cloud.tools.jib.http.Authorization;
import com.google.cloud.tools.jib.image.Image;
import com.google.cloud.tools.jib.image.LayerPropertyNotFoundException;
import com.google.cloud.tools.jib.image.json.BuildableManifestTemplate;
import com.google.cloud.tools.jib.image.json.ImageToJsonTranslator;
import com.google.cloud.tools.jib.registry.RegistryClient;
import com.google.cloud.tools.jib.registry.RegistryException;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/** Pushes the final image. */
class PushImageStep implements Callable<Void> {

  private static final String DESCRIPTION = "Pushing new image";

  private final BuildConfiguration buildConfiguration;
  private final ListeningExecutorService listeningExecutorService;
  private final ListenableFuture<Authorization> pushAuthorizationFuture;
  private final ListenableFuture<List<ListenableFuture<CachedLayer>>>
      pullBaseImageLayerFuturesFuture;
  private final List<ListenableFuture<CachedLayer>> buildApplicationLayerFutures;

  private final ListenableFuture<List<ListenableFuture<Void>>> pushBaseImageLayerFuturesFuture;
  private final List<ListenableFuture<Void>> pushApplicationLayerFutures;
  private final ListenableFuture<ListenableFuture<BlobDescriptor>>
      containerConfigurationBlobDescriptorFutureFuture;

  PushImageStep(
      BuildConfiguration buildConfiguration,
      ListeningExecutorService listeningExecutorService,
      ListenableFuture<Authorization> pushAuthorizationFuture,
      ListenableFuture<List<ListenableFuture<CachedLayer>>> pullBaseImageLayerFuturesFuture,
      List<ListenableFuture<CachedLayer>> buildApplicationLayerFutures,
      ListenableFuture<List<ListenableFuture<Void>>> pushBaseImageLayerFuturesFuture,
      List<ListenableFuture<Void>> pushApplicationLayerFutures,
      ListenableFuture<ListenableFuture<BlobDescriptor>>
          containerConfigurationBlobDescriptorFutureFuture) {
    this.buildConfiguration = buildConfiguration;
    this.listeningExecutorService = listeningExecutorService;
    this.pushAuthorizationFuture = pushAuthorizationFuture;
    this.pullBaseImageLayerFuturesFuture = pullBaseImageLayerFuturesFuture;
    this.buildApplicationLayerFutures = buildApplicationLayerFutures;

    this.pushBaseImageLayerFuturesFuture = pushBaseImageLayerFuturesFuture;
    this.pushApplicationLayerFutures = pushApplicationLayerFutures;
    this.containerConfigurationBlobDescriptorFutureFuture =
        containerConfigurationBlobDescriptorFutureFuture;
  }

  /**
   * Depends on {@code pushBaseImageLayerFuturesFuture} and {@code
   * containerConfigurationBlobDescriptorFutureFuture}.
   */
  @Override
  public Void call() throws ExecutionException, InterruptedException {
    List<ListenableFuture<?>> dependencies = new ArrayList<>();
    dependencies.add(pushAuthorizationFuture);
    dependencies.addAll(NonBlockingFutures.get(pushBaseImageLayerFuturesFuture));
    dependencies.addAll(pushApplicationLayerFutures);
    dependencies.add(NonBlockingFutures.get(containerConfigurationBlobDescriptorFutureFuture));
    return Futures.whenAllComplete(dependencies)
        .call(this::afterPushBaseImageLayerFuturesFuture, listeningExecutorService)
        .get();
  }

  /**
   * Depends on {@code pushAuthorizationFuture}, {@code pushBaseImageLayerFuturesFuture.get()},
   * {@code pushApplicationLayerFutures}, and (@code
   * containerConfigurationBlobDescriptorFutureFuture.get()}.
   */
  private Void afterPushBaseImageLayerFuturesFuture()
      throws IOException, RegistryException, ExecutionException, InterruptedException,
          LayerPropertyNotFoundException {
    try (Timer ignored = new Timer(buildConfiguration.getBuildLogger(), DESCRIPTION)) {
      RegistryClient registryClient =
          new RegistryClient(
              NonBlockingFutures.get(pushAuthorizationFuture),
              buildConfiguration.getTargetRegistry(),
              buildConfiguration.getTargetRepository());

      // TODO: Consolidate with BuildAndPushContainerConfigurationStep.
      // Constructs the image.
      Image image = new Image();
      for (Future<CachedLayer> cachedLayerFuture :
          NonBlockingFutures.get(pullBaseImageLayerFuturesFuture)) {
        image.addLayer(NonBlockingFutures.get(cachedLayerFuture));
      }
      for (Future<CachedLayer> cachedLayerFuture : buildApplicationLayerFutures) {
        image.addLayer(NonBlockingFutures.get(cachedLayerFuture));
      }
      ImageToJsonTranslator imageToJsonTranslator = new ImageToJsonTranslator(image);

      // Pushes the image manifest.
      BuildableManifestTemplate manifestTemplate =
          imageToJsonTranslator.getManifestTemplate(
              buildConfiguration.getTargetFormat(),
              NonBlockingFutures.get(
                  NonBlockingFutures.get(containerConfigurationBlobDescriptorFutureFuture)));
      registryClient.pushManifest(manifestTemplate, buildConfiguration.getTargetTag());
    }

    return null;
  }
}
