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
import com.google.cloud.tools.jib.blob.BlobDescriptor;
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

/** Pushes the final image. */
class PushImageStep implements Callable<Void> {

  private static final String DESCRIPTION = "Pushing new image";

  private final BuildConfiguration buildConfiguration;
  private final ListeningExecutorService listeningExecutorService;
  private final ListenableFuture<Authorization> pushAuthorizationFuture;

  private final ListenableFuture<List<ListenableFuture<Void>>> pushBaseImageLayerFuturesFuture;
  private final List<ListenableFuture<Void>> pushApplicationLayerFutures;
  private final ListenableFuture<ListenableFuture<BlobDescriptor>>
      containerConfigurationBlobDescriptorFutureFuture;
  private final ListenableFuture<ListenableFuture<Image>> buildContainerConfigurationFutureFuture;

  PushImageStep(
      BuildConfiguration buildConfiguration,
      ListeningExecutorService listeningExecutorService,
      ListenableFuture<Authorization> pushAuthorizationFuture,
      ListenableFuture<List<ListenableFuture<Void>>> pushBaseImageLayerFuturesFuture,
      List<ListenableFuture<Void>> pushApplicationLayerFutures,
      ListenableFuture<ListenableFuture<BlobDescriptor>>
          containerConfigurationBlobDescriptorFutureFuture,
      ListenableFuture<ListenableFuture<Image>> buildContainerConfigurationFutureFuture) {
    this.buildConfiguration = buildConfiguration;
    this.listeningExecutorService = listeningExecutorService;
    this.pushAuthorizationFuture = pushAuthorizationFuture;

    this.pushBaseImageLayerFuturesFuture = pushBaseImageLayerFuturesFuture;
    this.pushApplicationLayerFutures = pushApplicationLayerFutures;
    this.containerConfigurationBlobDescriptorFutureFuture =
        containerConfigurationBlobDescriptorFutureFuture;
    this.buildContainerConfigurationFutureFuture = buildContainerConfigurationFutureFuture;
  }

  /**
   * Depends on {@code pushBaseImageLayerFuturesFuture}, {@code
   * containerConfigurationBlobDescriptorFutureFuture}, and {@code
   * buildContainerConfigurationFutureFuture}.
   */
  @Override
  public Void call() throws ExecutionException, InterruptedException {
    List<ListenableFuture<?>> dependencies = new ArrayList<>();
    dependencies.add(pushAuthorizationFuture);
    dependencies.addAll(NonBlockingFutures.get(pushBaseImageLayerFuturesFuture));
    dependencies.addAll(pushApplicationLayerFutures);
    dependencies.add(NonBlockingFutures.get(containerConfigurationBlobDescriptorFutureFuture));
    dependencies.add(NonBlockingFutures.get(buildContainerConfigurationFutureFuture));
    return Futures.whenAllComplete(dependencies)
        .call(this::afterPushBaseImageLayerFuturesFuture, listeningExecutorService)
        .get();
  }

  /**
   * Depends on {@code pushAuthorizationFuture}, {@code pushBaseImageLayerFuturesFuture.get()},
   * {@code pushApplicationLayerFutures}, {@code
   * containerConfigurationBlobDescriptorFutureFuture.get()}, and {@code
   * buildContainerConfigurationFutureFuture.get()}.
   */
  private Void afterPushBaseImageLayerFuturesFuture()
      throws IOException, RegistryException, ExecutionException, InterruptedException,
          LayerPropertyNotFoundException {
    try (Timer ignored = new Timer(buildConfiguration.getBuildLogger(), DESCRIPTION)) {
      RegistryClient registryClient =
          new RegistryClient(
              NonBlockingFutures.get(pushAuthorizationFuture),
              buildConfiguration.getTargetImageRegistry(),
              buildConfiguration.getTargetImageRepository());

      // Constructs the image.
      ImageToJsonTranslator imageToJsonTranslator =
          new ImageToJsonTranslator(
              NonBlockingFutures.get(
                  NonBlockingFutures.get(buildContainerConfigurationFutureFuture)));

      // Pushes the image manifest.
      BuildableManifestTemplate manifestTemplate =
          imageToJsonTranslator.getManifestTemplate(
              buildConfiguration.getTargetFormat(),
              NonBlockingFutures.get(
                  NonBlockingFutures.get(containerConfigurationBlobDescriptorFutureFuture)));
      registryClient.pushManifest(manifestTemplate, buildConfiguration.getTargetImageTag());
    }

    return null;
  }
}
