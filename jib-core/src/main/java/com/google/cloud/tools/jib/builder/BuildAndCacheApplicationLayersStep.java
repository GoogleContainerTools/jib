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
import com.google.cloud.tools.jib.cache.Cache;
import com.google.cloud.tools.jib.cache.CacheMetadataCorruptedException;
import com.google.cloud.tools.jib.cache.CacheReader;
import com.google.cloud.tools.jib.cache.CacheWriter;
import com.google.cloud.tools.jib.cache.CachedLayer;
import com.google.cloud.tools.jib.image.LayerPropertyNotFoundException;
import com.google.cloud.tools.jib.image.ReproducibleLayerBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import javax.annotation.Nullable;

/** Builds and caches application layers. */
class BuildAndCacheApplicationLayersStep
    implements AsyncStep<ImmutableList<AsyncStep<CachedLayer>>> {

  private static final String DESCRIPTION = "Building application layers";

  private final BuildConfiguration buildConfiguration;
  private final SourceFilesConfiguration sourceFilesConfiguration;
  private final Cache cache;

  private final ListeningExecutorService listeningExecutorService;
  @Nullable private ListenableFuture<ImmutableList<AsyncStep<CachedLayer>>> listenableFuture;

  BuildAndCacheApplicationLayersStep(
      ListeningExecutorService listeningExecutorService,
      BuildConfiguration buildConfiguration,
      SourceFilesConfiguration sourceFilesConfiguration,
      Cache cache) {
    this.listeningExecutorService = listeningExecutorService;
    this.buildConfiguration = buildConfiguration;
    this.sourceFilesConfiguration = sourceFilesConfiguration;
    this.cache = cache;
  }

  @Override
  public ListenableFuture<ImmutableList<AsyncStep<CachedLayer>>> getFuture() {
    if (listenableFuture == null) {
      listenableFuture = listeningExecutorService.submit(this);
    }
    return listenableFuture;
  }

  /** Depends on nothing. */
  @Override
  public ImmutableList<AsyncStep<CachedLayer>> call() {
    try (Timer ignored = new Timer(buildConfiguration.getBuildLogger(), DESCRIPTION)) {
      return ImmutableList.of(
          buildAndCacheLayerAsync(
              "dependencies",
              sourceFilesConfiguration.getDependenciesFiles(),
              sourceFilesConfiguration.getDependenciesPathOnImage()),
          buildAndCacheLayerAsync(
              "resources",
              sourceFilesConfiguration.getResourcesFiles(),
              sourceFilesConfiguration.getResourcesPathOnImage()),
          buildAndCacheLayerAsync(
              "classes",
              sourceFilesConfiguration.getClassesFiles(),
              sourceFilesConfiguration.getClassesPathOnImage()));
    }
  }

  private AsyncStep<CachedLayer> buildAndCacheLayerAsync(
      String layerType, List<Path> sourceFiles, String extractionPath) {
    String description = "Building " + layerType + " layer";

    return new AsyncStep<CachedLayer>() {

      @Nullable private ListenableFuture<CachedLayer> listenableFuture;

      @Override
      public ListenableFuture<CachedLayer> getFuture() {
        if (listenableFuture == null) {
          listenableFuture = listeningExecutorService.submit(this);
        }
        return listenableFuture;
      }

      @Override
      public CachedLayer call()
          throws IOException, CacheMetadataCorruptedException, LayerPropertyNotFoundException {
        buildConfiguration.getBuildLogger().lifecycle(description + "...");

        try (Timer ignored = new Timer(buildConfiguration.getBuildLogger(), description)) {
          // Don't build the layer if it exists already.
          CachedLayer cachedLayer =
              new CacheReader(cache).getUpToDateLayerBySourceFiles(sourceFiles);
          if (cachedLayer != null) {
            return cachedLayer;
          }

          ReproducibleLayerBuilder reproducibleLayerBuilder =
              new ReproducibleLayerBuilder(sourceFiles, extractionPath);

          cachedLayer = new CacheWriter(cache).writeLayer(reproducibleLayerBuilder);
          // TODO: Remove
          buildConfiguration
              .getBuildLogger()
              .debug(description + " built " + cachedLayer.getBlobDescriptor().getDigest());
          return cachedLayer;
        }
      }
    };
  }
}
