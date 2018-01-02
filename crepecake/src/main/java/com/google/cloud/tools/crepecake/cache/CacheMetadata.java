/*
 * Copyright 2017 Google Inc.
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

package com.google.cloud.tools.crepecake.cache;

import com.google.cloud.tools.crepecake.image.DuplicateLayerException;
import com.google.cloud.tools.crepecake.image.ImageLayers;
import com.google.cloud.tools.crepecake.image.LayerPropertyNotFoundException;
import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The cache stores all the layer BLOBs as separate files and the cache metadata contains
 * information about each layer BLOB.
 */
class CacheMetadata {

  private final ImageLayers<CachedLayerWithMetadata> layers = new ImageLayers<>();

  void addLayer(CachedLayerWithMetadata layer)
      throws LayerPropertyNotFoundException, DuplicateLayerException {
    layers.add(layer);
  }

  ImageLayers<CachedLayerWithMetadata> getLayersWithType(CachedLayerType type)
      throws CacheMetadataCorruptedException {
    try {
      ImageLayers<CachedLayerWithMetadata> filteredLayers = new ImageLayers<>();
      for (CachedLayerWithMetadata layer : layers) {
        if (type == layer.getMetadata().getType()) {
          filteredLayers.add(layer);
        }
      }
      return filteredLayers;

    } catch (DuplicateLayerException | LayerPropertyNotFoundException ex) {
      throw new CacheMetadataCorruptedException(ex);
    }
  }

  /** Gets all the layers that have the same set of source directories. */
  ImageLayers<CachedLayerWithMetadata> getLayersWithSourceDirectories(Set<File> sourceDirectories)
      throws CacheMetadataCorruptedException {
    try {
      ImageLayers<CachedLayerWithMetadata> filteredLayers = new ImageLayers<>();

      for (CachedLayerWithMetadata cachedLayer : layers) {
        List<String> cachedLayerSourceDirectoryPaths =
            cachedLayer.getMetadata().getSourceDirectories();
        if (cachedLayerSourceDirectoryPaths == null) {
          continue;
        }

        Set<File> cachedLayerSourceDirectories = new HashSet<>();
        for (String sourceDirectory : cachedLayerSourceDirectoryPaths) {
          cachedLayerSourceDirectories.add(new File(sourceDirectory));
        }
        if (cachedLayerSourceDirectories.equals(sourceDirectories)) {
          filteredLayers.add(cachedLayer);
        }
      }

      return filteredLayers;

    } catch (DuplicateLayerException | LayerPropertyNotFoundException ex) {
      throw new CacheMetadataCorruptedException(ex);
    }
  }

  ImageLayers<CachedLayerWithMetadata> getLayers() {
    return layers;
  }
}
