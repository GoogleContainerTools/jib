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
import com.google.cloud.tools.jib.cache.Cache;
import com.google.cloud.tools.jib.cache.CacheReader;
import com.google.cloud.tools.jib.cache.CacheWriter;
import com.google.cloud.tools.jib.cache.CachedLayer;
import com.google.cloud.tools.jib.image.LayerBuilder;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/** Builds and caches application layers. */
class BuildAndCacheApplicationLayersStep implements Callable<List<ListenableFuture<CachedLayer>>> {

  private static final String DESCRIPTION = "Building application layers";

  private final BuildConfiguration buildConfiguration;
  private final SourceFilesConfiguration sourceFilesConfiguration;
  private final Cache cache;
  private final ListeningExecutorService listeningExecutorService;

  BuildAndCacheApplicationLayersStep(
      BuildConfiguration buildConfiguration,
      SourceFilesConfiguration sourceFilesConfiguration,
      Cache cache,
      ListeningExecutorService listeningExecutorService) {
    this.buildConfiguration = buildConfiguration;
    this.sourceFilesConfiguration = sourceFilesConfiguration;
    this.cache = cache;
    this.listeningExecutorService = listeningExecutorService;
  }

  /** Depends on nothing. */
  @Override
  public List<ListenableFuture<CachedLayer>> call() {
    try (Timer ignored = new Timer(buildConfiguration.getBuildLogger(), DESCRIPTION)) {
      List<ListenableFuture<CachedLayer>> applicationLayerFutures = new ArrayList<>(3);
      applicationLayerFutures.add(
          buildAndCacheLayerAsync(
              "dependencies",
              sourceFilesConfiguration.getDependenciesFiles(),
              sourceFilesConfiguration.getDependenciesPathOnImage()));
      applicationLayerFutures.add(
          buildAndCacheLayerAsync(
              "resources",
              sourceFilesConfiguration.getResourcesFiles(),
              sourceFilesConfiguration.getResourcesPathOnImage()));
      applicationLayerFutures.add(
          buildAndCacheLayerAsync(
              "classes",
              sourceFilesConfiguration.getClassesFiles(),
              sourceFilesConfiguration.getClassesPathOnImage()));

      return applicationLayerFutures;
    }
  }

  private ListenableFuture<CachedLayer> buildAndCacheLayerAsync(
      String layerType, List<Path> sourceFiles, String extractionPath) {
    String description = "Building " + layerType + " layer";

    return listeningExecutorService.submit(
        () -> {
          try (Timer ignored = new Timer(buildConfiguration.getBuildLogger(), description)) {
            // Don't build the layer if it exists already.
            CachedLayer cachedLayer =
                new CacheReader(cache).getUpToDateLayerBySourceFiles(sourceFiles);
            if (cachedLayer != null) {
              return cachedLayer;
            }

            LayerBuilder layerBuilder =
                new LayerBuilder(
                    sourceFiles, extractionPath, buildConfiguration.getEnableReproducibleBuilds());

            cachedLayer = new CacheWriter(cache).writeLayer(layerBuilder);
            // TODO: Remove
            buildConfiguration
                .getBuildLogger()
                .debug(description + " built " + cachedLayer.getBlobDescriptor().getDigest());
            return cachedLayer;
          }
        });
  }
}
