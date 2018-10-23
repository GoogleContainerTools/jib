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

import com.google.cloud.tools.jib.async.AsyncStep;
import com.google.cloud.tools.jib.blob.Blob;
import com.google.cloud.tools.jib.builder.TimerEventDispatcher;
import com.google.cloud.tools.jib.cache.Cache;
import com.google.cloud.tools.jib.cache.CacheCorruptedException;
import com.google.cloud.tools.jib.cache.CachedLayer;
import com.google.cloud.tools.jib.configuration.BuildConfiguration;
import com.google.cloud.tools.jib.configuration.LayerConfiguration;
import com.google.cloud.tools.jib.event.events.LogEvent;
import com.google.cloud.tools.jib.image.ReproducibleLayerBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.Callable;

/** Builds and caches application layers. */
class BuildAndCacheApplicationLayerStep implements AsyncStep<CachedLayer>, Callable<CachedLayer> {

  private static final String DESCRIPTION = "Building application layers";

  /**
   * Makes a list of {@link BuildAndCacheApplicationLayerStep} for dependencies, resources, and
   * classes layers. Optionally adds an extra layer if configured to do so.
   */
  static ImmutableList<BuildAndCacheApplicationLayerStep> makeList(
      ListeningExecutorService listeningExecutorService, BuildConfiguration buildConfiguration) {
    try (TimerEventDispatcher ignored =
        new TimerEventDispatcher(buildConfiguration.getEventDispatcher(), DESCRIPTION)) {
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
                layerConfiguration));
      }
      return buildAndCacheApplicationLayerSteps.build();
    }
  }

  private final String layerType;
  private final BuildConfiguration buildConfiguration;
  private final LayerConfiguration layerConfiguration;

  private final ListenableFuture<CachedLayer> listenableFuture;

  private BuildAndCacheApplicationLayerStep(
      String layerType,
      ListeningExecutorService listeningExecutorService,
      BuildConfiguration buildConfiguration,
      LayerConfiguration layerConfiguration) {
    this.layerType = layerType;
    this.buildConfiguration = buildConfiguration;
    this.layerConfiguration = layerConfiguration;

    listenableFuture = listeningExecutorService.submit(this);
  }

  @Override
  public ListenableFuture<CachedLayer> getFuture() {
    return listenableFuture;
  }

  @Override
  public CachedLayer call() throws IOException, CacheCorruptedException {
    String description = "Building " + layerType + " layer";

    buildConfiguration.getEventDispatcher().dispatch(LogEvent.lifecycle(description + "..."));

    try (TimerEventDispatcher ignored =
        new TimerEventDispatcher(buildConfiguration.getEventDispatcher(), description)) {
      Cache cache = buildConfiguration.getApplicationLayersCache();

      // Don't build the layer if it exists already.
      Optional<CachedLayer> optionalCachedLayer =
          cache.retrieve(layerConfiguration.getLayerEntries());
      if (optionalCachedLayer.isPresent()) {
        return optionalCachedLayer.get();
      }

      Blob layerBlob = new ReproducibleLayerBuilder(layerConfiguration.getLayerEntries()).build();
      CachedLayer cachedLayer =
          cache.writeUncompressedLayer(layerBlob, layerConfiguration.getLayerEntries());

      buildConfiguration
          .getEventDispatcher()
          .dispatch(LogEvent.debug(description + " built " + cachedLayer.getDigest()));

      return cachedLayer;
    }
  }
}
