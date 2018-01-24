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

import com.google.cloud.tools.crepecake.Timer;
import com.google.cloud.tools.crepecake.blob.Blob;
import com.google.cloud.tools.crepecake.blob.BlobDescriptor;
import com.google.cloud.tools.crepecake.cache.CachedLayer;
import com.google.cloud.tools.crepecake.hash.CountingDigestOutputStream;
import com.google.cloud.tools.crepecake.http.Authorization;
import com.google.cloud.tools.crepecake.image.DuplicateLayerException;
import com.google.cloud.tools.crepecake.image.Image;
import com.google.cloud.tools.crepecake.image.LayerPropertyNotFoundException;
import com.google.cloud.tools.crepecake.image.json.ImageToJsonTranslator;
import com.google.cloud.tools.crepecake.registry.RegistryClient;
import com.google.cloud.tools.crepecake.registry.RegistryException;
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

class BuildAndPushContainerConfigurationStep implements Callable<BlobDescriptor> {

  private static final String DESCRIPTION = "Pushing container configuration";

  private final BuildConfiguration buildConfiguration;
  private final Future<Authorization> pushAuthorizationFuture;
  private final List<Future<CachedLayer>> cachedLayerFutures;
  private final List<String> entrypoint;

  BuildAndPushContainerConfigurationStep(
      BuildConfiguration buildConfiguration,
      Future<Authorization> pushAuthorizationFuture,
      List<? extends Future<CachedLayer>> baseImageLayerFutures,
      List<? extends Future<CachedLayer>> applicationLayerFutures,
      List<String> entrypoint) {
    this.buildConfiguration = buildConfiguration;
    this.pushAuthorizationFuture = pushAuthorizationFuture;
    this.entrypoint = entrypoint;

    cachedLayerFutures = new ArrayList<>(baseImageLayerFutures);
    cachedLayerFutures.addAll(applicationLayerFutures);
  }

  @Override
  public BlobDescriptor call()
      throws ExecutionException, InterruptedException, LayerPropertyNotFoundException,
          DuplicateLayerException, IOException, RegistryException {
    try (Timer timer = new Timer(buildConfiguration.getBuildLogger(), DESCRIPTION)) {
      RegistryClient registryClient =
          new RegistryClient(
              pushAuthorizationFuture.get(),
              buildConfiguration.getTargetServerUrl(),
              buildConfiguration.getTargetImageName());

      // Constructs the image.
      Image image = new Image();
      for (Future<CachedLayer> cachedLayerFuture : cachedLayerFutures) {
        image.addLayer(cachedLayerFuture.get());
      }
      image.setEntrypoint(entrypoint);

      ImageToJsonTranslator imageToJsonTranslator = new ImageToJsonTranslator(image);

      // Gets the container configuration content descriptor.
      Blob containerConfigurationBlob = imageToJsonTranslator.getContainerConfigurationBlob();
      CountingDigestOutputStream digestOutputStream =
          new CountingDigestOutputStream(ByteStreams.nullOutputStream());
      containerConfigurationBlob.writeTo(digestOutputStream);
      BlobDescriptor containerConfigurationBlobDescriptor = digestOutputStream.toBlobDescriptor();

      timer.lap("push container configuration");

      // Pushes the container configuration.
      registryClient.pushBlob(
          containerConfigurationBlobDescriptor.getDigest(), containerConfigurationBlob);

      return containerConfigurationBlobDescriptor;
    }
  }
}
