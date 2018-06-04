/*
 * Copyright 2017 Google LLC. All rights reserved.
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

package com.google.cloud.tools.jib.cache;

import com.google.cloud.tools.jib.image.ImageLayers;
import com.google.cloud.tools.jib.image.LayerPropertyNotFoundException;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

/**
 * The cache stores all the layer BLOBs as separate files and the cache metadata contains
 * information about each layer BLOB.
 */
class CacheMetadata {

  private final ImageLayers.Builder<CachedLayerWithMetadata> layersBuilder = ImageLayers.builder();

  /** Can be used to filter layers in the metadata. */
  static class LayerFilter {

    private final ImageLayers<CachedLayerWithMetadata> layers;

    @Nullable private ImmutableList<Path> sourceFiles;

    private LayerFilter(ImageLayers<CachedLayerWithMetadata> layers) {
      this.layers = layers;
    }

    /** Filters to a certain list of source files. */
    LayerFilter bySourceFiles(ImmutableList<Path> sourceFiles) {
      this.sourceFiles = sourceFiles;
      return this;
    }

    /** Applies the filters to the metadata layers. */
    ImageLayers<CachedLayerWithMetadata> filter() throws CacheMetadataCorruptedException {
      try {
        ImageLayers.Builder<CachedLayerWithMetadata> filteredLayersBuilder = ImageLayers.builder();

        for (CachedLayerWithMetadata layer : layers) {
          if (sourceFiles != null) {
            if (layer.getMetadata() == null) {
              continue;
            }
            List<String> cachedLayerSourceFilePaths = layer.getMetadata().getSourceFiles();
            if (cachedLayerSourceFilePaths != null) {
              List<Path> cachedLayerSourceFiles = new ArrayList<>();
              for (String sourceFile : cachedLayerSourceFilePaths) {
                cachedLayerSourceFiles.add(Paths.get(sourceFile));
              }
              if (!cachedLayerSourceFiles.equals(sourceFiles)) {
                continue;
              }
            }
          }

          filteredLayersBuilder.add(layer);
        }

        return filteredLayersBuilder.build();

      } catch (LayerPropertyNotFoundException ex) {
        throw new CacheMetadataCorruptedException(ex);
      }
    }
  }

  ImageLayers<CachedLayerWithMetadata> getLayers() {
    return layersBuilder.build();
  }

  synchronized void addLayer(CachedLayerWithMetadata layer) throws LayerPropertyNotFoundException {
    layersBuilder.add(layer);
  }

  LayerFilter filterLayers() {
    return new LayerFilter(layersBuilder.build());
  }
}
