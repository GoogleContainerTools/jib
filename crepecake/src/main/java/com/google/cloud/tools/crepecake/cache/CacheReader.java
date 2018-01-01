package com.google.cloud.tools.crepecake.cache;

import com.google.cloud.tools.crepecake.image.ImageLayers;
import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Reads image content from the cache. */
public class CacheReader {

  private final Cache cache;

  public CacheReader(Cache cache) {
    this.cache = cache;
  }

  /**
   * Gets the file that stores the content BLOB for an application layer.
   *
   * @param layerType the type of layer
   * @param sourceDirectories the source directories the layer must be built from
   * @return
   * @throws CacheMetadataCorruptedException
   */
  public File getLayerFile(CachedLayerType layerType, Set<File> sourceDirectories)
      throws CacheMetadataCorruptedException {
    switch (layerType) {
      case DEPENDENCIES:
      case RESOURCES:
      case CLASSES:
        CacheMetadata cacheMetadata = cache.getMetadata();
        ImageLayers<CachedLayerWithMetadata> cachedLayers =
            cacheMetadata.getLayersWithType(layerType);

        // Finds the newest cached layer for the layer type.
        long newestLastModifiedTime = 0;
        File newestLayerFile = null;
        for (CachedLayerWithMetadata cachedLayer : cachedLayers) {
          // Checks if the cached layer has the same source directories.
          // TODO: Consolidate this with the same code in CacheChecker.
          List<String> cachedLayerSourceDirectoryPaths =
              cachedLayer.getMetadata().getSourceDirectories();
          if (cachedLayerSourceDirectoryPaths == null) {
            continue;
          }

          Set<File> cachedLayerSourceDirectories = new HashSet<>();
          for (String sourceDirectory : cachedLayerSourceDirectoryPaths) {
            cachedLayerSourceDirectories.add(new File(sourceDirectory));
          }
          if (!cachedLayerSourceDirectories.equals(sourceDirectories)) {
            continue;
          }

          long cachedLayerLastModifiedTime = cachedLayer.getMetadata().getLastModifiedTime();
          if (cachedLayerLastModifiedTime <= newestLastModifiedTime) {
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
