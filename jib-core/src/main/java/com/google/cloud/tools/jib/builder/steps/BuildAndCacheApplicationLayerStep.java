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

package com.google.cloud.tools.jib.builder.steps;

import com.google.cloud.tools.jib.Timer;
import com.google.cloud.tools.jib.async.AsyncStep;
import com.google.cloud.tools.jib.builder.BuildConfiguration;
import com.google.cloud.tools.jib.builder.SourceFilesConfiguration;
import com.google.cloud.tools.jib.cache.Cache;
import com.google.cloud.tools.jib.cache.CacheMetadataCorruptedException;
import com.google.cloud.tools.jib.cache.CacheReader;
import com.google.cloud.tools.jib.cache.CacheWriter;
import com.google.cloud.tools.jib.cache.CachedLayerWithMetadata;
import com.google.cloud.tools.jib.image.LayerEntry;
import com.google.cloud.tools.jib.image.ReproducibleLayerBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.io.IOException;
import java.util.concurrent.Callable;

/** Builds and caches application layers. */
class BuildAndCacheApplicationLayerStep
    implements AsyncStep<CachedLayerWithMetadata>, Callable<CachedLayerWithMetadata> {

  private static final String DESCRIPTION = "Building application layers";

  /**
   * Makes a list of {@link BuildAndCacheApplicationLayerStep} for dependencies, resources, and
   * classes layers.
   */
  static ImmutableList<BuildAndCacheApplicationLayerStep> makeList(
      ListeningExecutorService listeningExecutorService,
      BuildConfiguration buildConfiguration,
      SourceFilesConfiguration sourceFilesConfiguration,
      Cache cache) {
    try (Timer ignored = new Timer(buildConfiguration.getBuildLogger(), DESCRIPTION)) {
      return ImmutableList.of(
          new BuildAndCacheApplicationLayerStep(
              "dependencies",
              listeningExecutorService,
              buildConfiguration,
              ImmutableList.of(
                  new LayerEntry(
                      sourceFilesConfiguration.getDependenciesFiles(),
                      sourceFilesConfiguration.getDependenciesPathOnImage())),
              cache),
          new BuildAndCacheApplicationLayerStep(
              "resources",
              listeningExecutorService,
              buildConfiguration,
              ImmutableList.of(
                  new LayerEntry(
                      sourceFilesConfiguration.getResourcesFiles(),
                      sourceFilesConfiguration.getResourcesPathOnImage())),
              cache),
          new BuildAndCacheApplicationLayerStep(
              "classes",
              listeningExecutorService,
              buildConfiguration,
              ImmutableList.of(
                  new LayerEntry(
                      sourceFilesConfiguration.getClassesFiles(),
                      sourceFilesConfiguration.getClassesPathOnImage())),
              cache));
    }
  }

  private final String layerType;
  private final BuildConfiguration buildConfiguration;
  private final ImmutableList<LayerEntry> layerEntries;
  private final Cache cache;

  private final ListenableFuture<CachedLayerWithMetadata> listenableFuture;

  private BuildAndCacheApplicationLayerStep(
      String layerType,
      ListeningExecutorService listeningExecutorService,
      BuildConfiguration buildConfiguration,
      ImmutableList<LayerEntry> layerEntries,
      Cache cache) {
    this.layerType = layerType;
    this.buildConfiguration = buildConfiguration;
    this.layerEntries = layerEntries;
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
      CachedLayerWithMetadata cachedLayer =
          new CacheReader(cache).getUpToDateLayerByLayerEntries(layerEntries);
      if (cachedLayer != null) {
        return cachedLayer;
      }

      ReproducibleLayerBuilder reproducibleLayerBuilder = new ReproducibleLayerBuilder();
      for (LayerEntry layerEntry : layerEntries) {
        reproducibleLayerBuilder.addFiles(
            layerEntry.getSourceFiles(), layerEntry.getExtractionPath());
      }

      cachedLayer = new CacheWriter(cache).writeLayer(reproducibleLayerBuilder);

      buildConfiguration
          .getBuildLogger()
          .debug(description + " built " + cachedLayer.getBlobDescriptor().getDigest());

      return cachedLayer;
    }
  }
}
