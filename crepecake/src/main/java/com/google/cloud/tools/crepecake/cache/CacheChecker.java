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

import com.google.cloud.tools.crepecake.image.ImageLayers;
import com.google.cloud.tools.crepecake.image.ReferenceLayer;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Checks if cached data is outdated. */
public class CacheChecker {

  private final Cache cache;

  public CacheChecker(Cache cache) {
    this.cache = cache;
  }

  /** @return true if the base image layers exist in the cache; false otherwise */
  public boolean areBaseImageLayersCached(ImageLayers<ReferenceLayer> baseImageLayers)
      throws CacheMetadataCorruptedException {
    // Gets the base image layers.
    ImageLayers<CachedLayerWithMetadata> cachedLayers =
        cache.getMetadata().getLayersWithType(CachedLayerType.BASE);

    if (cachedLayers.size() < baseImageLayers.size()) {
      return false;
    }

    for (ReferenceLayer baseImageLayer : baseImageLayers) {
      if (!cachedLayers.has(baseImageLayer.getBlobDescriptor().getDigest())) {
        return false;
      }
    }

    return true;
  }

  /**
   * Checks all cached layers built from the source directories to see if the source directories has
   * been modified since the newest layer build.
   *
   * @param sourceDirectories the source directories to check
   * @return true if no cached layer exists that are up-to-date with the source directories; false
   *     otherwise.
   */
  public boolean areSourceDirectoriesModified(Set<File> sourceDirectories) throws IOException {
    ImageLayers<CachedLayerWithMetadata> cachedLayers = cache.getMetadata().getLayers();

    for (CachedLayerWithMetadata cachedLayer : cachedLayers) {
      // Checks if the cached layer has the same source directories.
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

      // Checks if the layer is outdated.
      long lastModifiedTime = cachedLayer.getMetadata().getLastModifiedTime();
      boolean hasOutdatedFile = false;
      for (File file : sourceDirectories) {
        if (isFileModifiedRecursive(file, lastModifiedTime)) {
          hasOutdatedFile = true;
          break;
        }
      }
      if (hasOutdatedFile) {
        continue;
      }

      // This layer is an up-to-date layer.
      return false;
    }

    return true;
  }

  /**
   * Checks the file has been modified since the {@code lastModifiedTime}. Recursively checks all
   * subfiles if {@code file} is a directory.
   */
  private boolean isFileModifiedRecursive(File file, long lastModifiedTime) throws IOException {
    if (file.lastModified() > lastModifiedTime) {
      return true;
    }

    if (file.isDirectory()) {
      File[] subFiles = file.listFiles();
      if (null == subFiles) {
        throw new IOException("Failed to read directory: " + file.getAbsolutePath());
      }
      for (final File subFile : subFiles) {
        if (isFileModifiedRecursive(subFile, lastModifiedTime)) {
          return true;
        }
      }
    }

    return false;
  }
}
