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
import com.google.cloud.tools.crepecake.cache.CacheWriter;
import com.google.cloud.tools.crepecake.cache.CachedLayer;
import com.google.cloud.tools.crepecake.image.DuplicateLayerException;
import com.google.cloud.tools.crepecake.image.ImageLayers;
import com.google.cloud.tools.crepecake.image.LayerBuilder;
import com.google.cloud.tools.crepecake.image.LayerPropertyNotFoundException;
import com.google.cloud.tools.crepecake.image.UnwrittenLayer;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;

// TODO: Add unit test.
/** Builds and caches application layers. */
class BuildAndCacheApplicationLayersStep implements Step<Void, ImageLayers<CachedLayer>> {

  private final SourceFilesConfiguration sourceFilesConfiguration;
  private final Cache cache;

  BuildAndCacheApplicationLayersStep(
      SourceFilesConfiguration sourceFilesConfiguration, Cache cache) {
    this.sourceFilesConfiguration = sourceFilesConfiguration;
    this.cache = cache;
  }

  @Override
  public ImageLayers<CachedLayer> run(Void input)
      throws IOException, LayerPropertyNotFoundException, DuplicateLayerException {
    // TODO: Check if needs rebuilding.
    CachedLayer dependenciesLayer =
        buildAndCacheLayer(
            sourceFilesConfiguration.getDependenciesFiles(),
            sourceFilesConfiguration.getDependenciesExtractionPath());
    CachedLayer resourcesLayer =
        buildAndCacheLayer(
            sourceFilesConfiguration.getDependenciesFiles(),
            sourceFilesConfiguration.getDependenciesExtractionPath());
    CachedLayer classesLayer =
        buildAndCacheLayer(
            sourceFilesConfiguration.getDependenciesFiles(),
            sourceFilesConfiguration.getDependenciesExtractionPath());

    return new ImageLayers<CachedLayer>()
        .add(dependenciesLayer)
        .add(resourcesLayer)
        .add(classesLayer);
  }

  private CachedLayer buildAndCacheLayer(Set<Path> sourceFiles, String extractionPath)
      throws IOException {
    LayerBuilder layerBuilder = new LayerBuilder();

    for (Path sourceFile : sourceFiles) {
      layerBuilder.addFile(sourceFile, extractionPath);
    }

    UnwrittenLayer builtLayer = layerBuilder.build();

    CacheWriter cacheWriter = new CacheWriter(cache);
    return cacheWriter.writeLayer(builtLayer);
  }
}
