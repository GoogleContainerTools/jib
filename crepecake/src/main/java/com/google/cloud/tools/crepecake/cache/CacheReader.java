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

package com.google.cloud.tools.crepecake.cache;

import com.google.cloud.tools.crepecake.image.ImageLayers;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;

/** Reads image content from the cache. */
public class CacheReader {

  private final Cache cache;

  public CacheReader(Cache cache) {
    this.cache = cache;
  }

  /**
   * Finds the file that stores the content BLOB for an application layer.
   *
   * @param layerType the type of layer
   * @param sourceFiles the source files the layer must be built from
   * @return the newest cached layer file that matches the {@code layerType} and {@code sourceFiles}
   */
  public Path getLayerFile(CachedLayerType layerType, List<Path> sourceFiles)
      throws CacheMetadataCorruptedException {
    switch (layerType) {
      case DEPENDENCIES:
      case RESOURCES:
      case CLASSES:
        CacheMetadata cacheMetadata = cache.getMetadata();
        ImageLayers<CachedLayerWithMetadata> cachedLayers =
            cacheMetadata.filterLayers().byType(layerType).bySourceFiles(sourceFiles).filter();

        // Finds the newest cached layer for the layer type.
        FileTime newestLastModifiedTime = FileTime.from(Instant.MIN);

        Path newestLayerFile = null;
        for (CachedLayerWithMetadata cachedLayer : cachedLayers) {
          FileTime cachedLayerLastModifiedTime = cachedLayer.getMetadata().getLastModifiedTime();
          if (cachedLayerLastModifiedTime.compareTo(newestLastModifiedTime) > 0) {
            newestLastModifiedTime = cachedLayerLastModifiedTime;
            newestLayerFile = cachedLayer.getContentFile();
          }
        }

        return newestLayerFile;

      default:
        throw new UnsupportedOperationException("Can only find layer files for application layers");
    }
  }
}
