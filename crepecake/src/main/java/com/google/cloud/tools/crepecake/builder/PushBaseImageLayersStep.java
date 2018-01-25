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
import java.io.IOException;
import java.util.concurrent.Callable;

/** Pushes the base image layers to the target registry. */
class PushBaseImageLayersStep implements Callable<Void> {

  private final BuildConfiguration buildConfiguration;
  private final Authorization pushAuthorization;
  private final ImageLayers<CachedLayer> baseImageLayers;

  PushBaseImageLayersStep(
      BuildConfiguration buildConfiguration,
      Authorization pushAuthorization,
      ImageLayers<CachedLayer> baseImageLayers) {
    this.buildConfiguration = buildConfiguration;
    this.pushAuthorization = pushAuthorization;
    this.baseImageLayers = baseImageLayers;
  }

  @Override
  public Void call() throws IOException, RegistryException {
    RegistryClient registryClient =
        new RegistryClient(
            pushAuthorization,
            buildConfiguration.getTargetServerUrl(),
            buildConfiguration.getTargetImageName());

    // Pushes the base image layers.
    for (CachedLayer layer : baseImageLayers) {
      new PushBlobStep(registryClient, layer.getBlob(), layer.getBlobDescriptor().getDigest())
          .call();
    }

    return null;
  }
}
