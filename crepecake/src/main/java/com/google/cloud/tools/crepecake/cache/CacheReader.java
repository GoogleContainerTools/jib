package com.google.cloud.tools.crepecake.cache;

import com.google.cloud.tools.crepecake.image.ImageLayers;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Set;

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
   * @throws CacheMetadataCorruptedException
   */
  public File getLayerFile(CachedLayerType layerType, Set<Path> sourceFiles)
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
        File newestLayerFile = null;
        for (CachedLayerWithMetadata cachedLayer : cachedLayers) {
          FileTime cachedLayerLastModifiedTime = cachedLayer.getMetadata().getLastModifiedTime();
          if (cachedLayerLastModifiedTime.compareTo(newestLastModifiedTime) <= 0) {
            continue;
          }

          newestLastModifiedTime = cachedLayerLastModifiedTime;
          newestLayerFile = cachedLayer.getContentFile();
        }

        return newestLayerFile;

      default:
        return null;
    }
  }
}
