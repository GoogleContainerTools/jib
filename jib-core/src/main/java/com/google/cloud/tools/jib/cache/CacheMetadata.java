/*
 * Copyright 2017 Google LLC.
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
import com.google.cloud.tools.jib.image.LayerEntry;
import com.google.cloud.tools.jib.image.LayerPropertyNotFoundException;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import java.nio.file.Paths;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import javax.annotation.Nullable;

/**
 * The cache stores all the layer BLOBs as separate files and the cache metadata contains
 * information about each layer BLOB.
 */
class CacheMetadata {

  static class Builder {

    private final ImageLayers.Builder<CachedLayerWithMetadata> layersBuilder =
        ImageLayers.builder();

    private Builder(ImageLayers<CachedLayerWithMetadata> initialLayers) {
      layersBuilder.addAll(initialLayers);
    }

    private Builder() {}

    /**
     * Adds a layer. This method is <b>NOT</b> thread-safe.
     *
     * @param layer the layer to add
     */
    Builder addLayer(CachedLayerWithMetadata layer) {
      layersBuilder.add(layer);
      return this;
    }

    CacheMetadata build() {
      return new CacheMetadata(layersBuilder.build());
    }
  }

  /** Can be used to filter layers in the metadata. */
  static class LayerFilter {

    /**
     * Checks if the layer entries matches the metadata layer entries
     *
     * @param layerEntries the layer entries to check
     * @param metadataEntries the metadata entries to match against
     * @return {@code true} if the layer entries match the metadata entries; {@code false} otherwise
     */
    @VisibleForTesting
    static boolean doLayerEntriesMatchMetadataEntries(
        ImmutableList<LayerEntry> layerEntries,
        ImmutableList<LayerMetadata.LayerMetadataEntry> metadataEntries) {
      // Checks the layer entries are the same as the metadata layer entries.
      if (layerEntries.size() != metadataEntries.size()) {
        return false;
      }
      return pairwiseCompareAllPass(
          layerEntries,
          metadataEntries,
          (layerEntry, metadataEntry) -> {
            // Checks extraction path not equal.
            if (!layerEntry.getExtractionPath().equals(metadataEntry.getExtractionPath())) {
              return false;
            }

            // Checks for any source file not equal.
            if (layerEntry.getSourceFiles().size()
                != metadataEntry.getSourceFilesStrings().size()) {
              return false;
            }
            return pairwiseCompareAllPass(
                layerEntry.getSourceFiles(),
                metadataEntry.getSourceFilesStrings(),
                (sourceFile, sourceFileString) -> sourceFile.equals(Paths.get(sourceFileString)));
          });
    }

    private static <A, B> boolean pairwiseCompareAllPass(
        List<A> listA, List<B> listB, BiPredicate<A, B> compare) {
      return Streams.zip(listA.stream(), listB.stream(), compare::test)
          .allMatch(Predicate.isEqual(true));
    }

    private final ImageLayers<CachedLayerWithMetadata> layers;

    @Nullable private ImmutableList<LayerEntry> layerEntries;

    private LayerFilter(ImageLayers<CachedLayerWithMetadata> layers) {
      this.layers = layers;
    }

    /** Filters to a certain list of {@link LayerEntry}s. */
    LayerFilter byLayerEntries(ImmutableList<LayerEntry> layerEntries) {
      this.layerEntries = layerEntries;
      return this;
    }

    /** Applies the filters to the metadata layers. */
    ImageLayers<CachedLayerWithMetadata> filter() throws CacheMetadataCorruptedException {
      try {
        ImageLayers.Builder<CachedLayerWithMetadata> filteredLayersBuilder = ImageLayers.builder();

        for (CachedLayerWithMetadata layer : layers) {
          if (layerEntries != null) {
            if (layer.getMetadata() == null) {
              // There is no metadata, so it doesn't pass the filter.
              continue;
            }

            if (!doLayerEntriesMatchMetadataEntries(
                layerEntries, layer.getMetadata().getEntries())) {
              // The layer entries do not match.
              continue;
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

  static Builder builder() {
    return new Builder();
  }

  private final ImageLayers<CachedLayerWithMetadata> layers;

  private CacheMetadata(ImageLayers<CachedLayerWithMetadata> layers) {
    this.layers = layers;
  }

  ImageLayers<CachedLayerWithMetadata> getLayers() {
    return layers;
  }

  LayerFilter filterLayers() {
    return new LayerFilter(layers);
  }

  /** @return a {@link Builder} starts with all the layers in this metadata */
  Builder newAppendingBuilder() {
    return new Builder(layers);
  }
}
