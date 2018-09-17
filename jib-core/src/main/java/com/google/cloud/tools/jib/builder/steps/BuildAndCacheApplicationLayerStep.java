/*
 * Copyright 2018 Google LLC.
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

package com.google.cloud.tools.jib.builder.steps;

import com.google.cloud.tools.jib.Timer;
import com.google.cloud.tools.jib.async.AsyncStep;
import com.google.cloud.tools.jib.cache.Cache;
import com.google.cloud.tools.jib.cache.CacheMetadataCorruptedException;
import com.google.cloud.tools.jib.cache.CacheReader;
import com.google.cloud.tools.jib.cache.CacheWriter;
import com.google.cloud.tools.jib.cache.CachedLayerWithMetadata;
import com.google.cloud.tools.jib.configuration.BuildConfiguration;
import com.google.cloud.tools.jib.configuration.LayerConfiguration;
import com.google.cloud.tools.jib.image.ReproducibleLayerBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.Callable;

/** Builds and caches application layers. */
class BuildAndCacheApplicationLayerStep
    implements AsyncStep<CachedLayerWithMetadata>, Callable<CachedLayerWithMetadata> {

  private static final String DESCRIPTION = "Building application layers";

  /**
   * Makes a list of {@link BuildAndCacheApplicationLayerStep} for dependencies, resources, and
   * classes layers. Optionally adds an extra layer if configured to do so.
   */
  static ImmutableList<BuildAndCacheApplicationLayerStep> makeList(
      ListeningExecutorService listeningExecutorService,
      BuildConfiguration buildConfiguration,
      Cache cache) {
    try (Timer ignored = new Timer(buildConfiguration.getBuildLogger(), DESCRIPTION)) {
      ImmutableList.Builder<BuildAndCacheApplicationLayerStep> buildAndCacheApplicationLayerSteps =
          ImmutableList.builderWithExpectedSize(buildConfiguration.getLayerConfigurations().size());
      for (LayerConfiguration layerConfiguration : buildConfiguration.getLayerConfigurations()) {
        // Skips the layer if empty.
        if (layerConfiguration.getLayerEntries().isEmpty()) {
          continue;
        }

        buildAndCacheApplicationLayerSteps.add(
            new BuildAndCacheApplicationLayerStep(
                layerConfiguration.getName(),
                listeningExecutorService,
                buildConfiguration,
                layerConfiguration,
                cache));
      }
      return buildAndCacheApplicationLayerSteps.build();
    }
  }

  private final String layerType;
  private final BuildConfiguration buildConfiguration;
  private final LayerConfiguration layerConfiguration;
  private final Cache cache;

  private final ListenableFuture<CachedLayerWithMetadata> listenableFuture;

  private BuildAndCacheApplicationLayerStep(
      String layerType,
      ListeningExecutorService listeningExecutorService,
      BuildConfiguration buildConfiguration,
      LayerConfiguration layerConfiguration,
      Cache cache) {
    this.layerType = layerType;
    this.buildConfiguration = buildConfiguration;
    this.layerConfiguration = layerConfiguration;
    this.cache = cache;

    listenableFuture = listeningExecutorService.submit(this);
  }

  @Override
  public ListenableFuture<CachedLayerWithMetadata> getFuture() {
    return listenableFuture;
  }

  @Override
  public CachedLayerWithMetadata call() throws IOException, CacheMetadataCorruptedException {
    String description = "Building " + layerType + " layer";

    buildConfiguration.getBuildLogger().lifecycle(description + "...");

    try (Timer ignored = new Timer(buildConfiguration.getBuildLogger(), description)) {
      // Don't build the layer if it exists already.
      Optional<CachedLayerWithMetadata> optionalCachedLayer =
          new CacheReader(cache)
              .getUpToDateLayerByLayerEntries(layerConfiguration.getLayerEntries());
      if (optionalCachedLayer.isPresent()) {
        return optionalCachedLayer.get();
      }

      CachedLayerWithMetadata cachedLayer =
          new CacheWriter(cache)
              .writeLayer(new ReproducibleLayerBuilder(layerConfiguration.getLayerEntries()));

      buildConfiguration
          .getBuildLogger()
          .debug(description + " built " + cachedLayer.getBlobDescriptor().getDigest());

      return cachedLayer;
    }
  }
}
