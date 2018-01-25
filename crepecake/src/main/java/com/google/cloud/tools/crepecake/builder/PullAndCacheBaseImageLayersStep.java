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

import com.google.cloud.tools.crepecake.cache.Cache;
import com.google.cloud.tools.crepecake.cache.CachedLayer;
import com.google.cloud.tools.crepecake.http.Authorization;
import com.google.cloud.tools.crepecake.image.DuplicateLayerException;
import com.google.cloud.tools.crepecake.image.Image;
import com.google.cloud.tools.crepecake.image.ImageLayers;
import com.google.cloud.tools.crepecake.image.Layer;
import com.google.cloud.tools.crepecake.image.LayerPropertyNotFoundException;
import com.google.cloud.tools.crepecake.registry.RegistryClient;
import com.google.cloud.tools.crepecake.registry.RegistryException;
import java.io.IOException;
import java.util.concurrent.Callable;

/** Pulls and caches the base image layers. */
class PullAndCacheBaseImageLayersStep implements Callable<ImageLayers<CachedLayer>> {

  private final BuildConfiguration buildConfiguration;
  private final Cache cache;
  private final Authorization pullAuthorization;
  private final Image baseImage;

  PullAndCacheBaseImageLayersStep(
      BuildConfiguration buildConfiguration,
      Cache cache,
      Authorization pullAuthorization,
      Image baseImage) {
    this.buildConfiguration = buildConfiguration;
    this.cache = cache;
    this.pullAuthorization = pullAuthorization;
    this.baseImage = baseImage;
  }

  @Override
  public ImageLayers<CachedLayer> call()
      throws LayerPropertyNotFoundException, IOException, RegistryException,
          DuplicateLayerException {
    RegistryClient registryClient =
        new RegistryClient(
            pullAuthorization,
            buildConfiguration.getBaseImageServerUrl(),
            buildConfiguration.getBaseImageName());

    ImageLayers<CachedLayer> baseImageLayers = new ImageLayers<>();
    for (Layer layer : baseImage.getLayers()) {
      PullAndCacheBaseImageLayerStep pullAndCacheBaseImageLayerStep =
          new PullAndCacheBaseImageLayerStep(registryClient, cache, layer);
      baseImageLayers.add(pullAndCacheBaseImageLayerStep.call());
    }

    return baseImageLayers;
  }
}
