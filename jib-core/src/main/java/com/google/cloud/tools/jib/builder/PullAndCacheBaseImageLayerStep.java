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

import com.google.cloud.tools.jib.cache.Cache;
import com.google.cloud.tools.jib.cache.CacheChecker;
import com.google.cloud.tools.jib.cache.CacheWriter;
import com.google.cloud.tools.jib.cache.CachedLayer;
import com.google.cloud.tools.jib.image.DescriptorDigest;
import com.google.cloud.tools.jib.image.DuplicateLayerException;
import com.google.cloud.tools.jib.image.Layer;
import com.google.cloud.tools.jib.image.LayerPropertyNotFoundException;
import com.google.cloud.tools.jib.registry.RegistryClient;
import com.google.cloud.tools.jib.registry.RegistryException;
import com.google.common.io.CountingOutputStream;
import java.io.IOException;
import java.util.concurrent.Callable;

/** Pulls and caches a single base image layer. */
class PullAndCacheBaseImageLayerStep implements Callable<CachedLayer> {

  private final RegistryClient registryClient;
  private final Cache cache;
  private final Layer layer;

  PullAndCacheBaseImageLayerStep(RegistryClient registryClient, Cache cache, Layer layer) {
    this.registryClient = registryClient;
    this.cache = cache;
    this.layer = layer;
  }

  @Override
  public CachedLayer call()
      throws IOException, RegistryException, LayerPropertyNotFoundException,
          DuplicateLayerException {
    DescriptorDigest layerDigest = layer.getBlobDescriptor().getDigest();

    // Checks if the layer already exists in the cache.
    CachedLayer cachedLayer = new CacheChecker(cache).getLayer(layerDigest);
    if (cachedLayer != null) {
      return cachedLayer;
    }

    CacheWriter cacheWriter = new CacheWriter(cache);
    CountingOutputStream layerOutputStream = cacheWriter.getLayerOutputStream(layerDigest);
    registryClient.pullBlob(layerDigest, layerOutputStream);
    return cacheWriter.getCachedLayer(layerDigest, layerOutputStream);
  }
}
