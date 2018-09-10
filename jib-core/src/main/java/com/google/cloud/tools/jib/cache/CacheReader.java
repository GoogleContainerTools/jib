/*
 * Copyright 2018 Google LLC.
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

import com.google.cloud.tools.jib.filesystem.DirectoryWalker;
import com.google.cloud.tools.jib.image.DescriptorDigest;
import com.google.cloud.tools.jib.image.ImageLayers;
import com.google.cloud.tools.jib.image.LayerEntry;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;

/** Reads image content from the cache. */
public class CacheReader {

  /**
   * Gets the last modified time for the file at {@code path}. If {@code path} is a directory, then
   * gets the latest modified time of its subfiles.
   *
   * @param path the file to check
   * @return the last modified time
   * @throws IOException if checking the last modified time fails
   */
  private static FileTime getLastModifiedTime(Path path) throws IOException {
    if (Files.isDirectory(path)) {
      List<Path> subFiles = new DirectoryWalker(path).walk();
      FileTime maxLastModifiedTime = FileTime.from(Instant.MIN);

      // Finds the max last modified time for the subfiles.
      for (Path subFilePath : subFiles) {
        FileTime subFileLastModifiedTime = Files.getLastModifiedTime(subFilePath);
        if (subFileLastModifiedTime.compareTo(maxLastModifiedTime) > 0) {
          maxLastModifiedTime = subFileLastModifiedTime;
        }
      }

      return maxLastModifiedTime;
    }

    return Files.getLastModifiedTime(path);
  }

  private final Cache cache;

  public CacheReader(Cache cache) {
    this.cache = cache;
  }

  /**
   * @param layerDigest the layer digest of the layer to get.
   * @return the cached layer with digest {@code layerDigest}, or {@code null} if not found.
   */
  @Nullable
  public CachedLayer getLayer(DescriptorDigest layerDigest) {
    return cache.getMetadata().getLayers().get(layerDigest);
  }

  /**
   * Finds the file that stores the content BLOB for an application layer.
   *
   * @param layerEntries the entries for the layer content
   * @return the newest cached layer file that matches the {@code layerType} and {@code
   *     sourceFiles}, or {@code null} if there is no match.
   * @throws CacheMetadataCorruptedException if getting the cache metadata fails.
   */
  @Nullable
  public Path getLayerFile(ImmutableList<LayerEntry> layerEntries)
      throws CacheMetadataCorruptedException {
    CacheMetadata cacheMetadata = cache.getMetadata();
    ImageLayers<CachedLayerWithMetadata> cachedLayers =
        cacheMetadata.filterLayers().byLayerEntries(layerEntries).filter();

    // Finds the newest cached layer for the layer type.
    FileTime newestLastModifiedTime = FileTime.from(Instant.MIN);

    Path newestLayerFile = null;
    for (CachedLayerWithMetadata cachedLayer : cachedLayers) {
      if (cachedLayer.getMetadata() == null) {
        throw new IllegalStateException("Layers with sourceFiles should have metadata");
      }

      FileTime cachedLayerLastModifiedTime = cachedLayer.getMetadata().getLastModifiedTime();
      if (cachedLayerLastModifiedTime.compareTo(newestLastModifiedTime) > 0) {
        newestLastModifiedTime = cachedLayerLastModifiedTime;
        newestLayerFile = cachedLayer.getContentFile();
      }
    }

    return newestLayerFile;
  }

  /**
   * Gets an up-to-date layer that is built from the {@code sourceFiles}.
   *
   * <p>The method returns the first up-to-date layer found. This is safe because the source files
   * will not have been modified since creation of any up-to-date layer (ie. all up-to-date layers
   * should have the same file contents).
   *
   * @param layerEntries the layer's content entries
   * @return an up-to-date layer containing the source files.
   * @throws IOException if reading the source files fails.
   * @throws CacheMetadataCorruptedException if reading the cache metadata fails.
   */
  public Optional<CachedLayerWithMetadata> getUpToDateLayerByLayerEntries(
      ImmutableList<LayerEntry> layerEntries) throws IOException, CacheMetadataCorruptedException {
    // Grabs all the layers that have matching source files.
    ImageLayers<CachedLayerWithMetadata> cachedLayersWithSourceFiles =
        cache.getMetadata().filterLayers().byLayerEntries(layerEntries).filter();
    if (cachedLayersWithSourceFiles.isEmpty()) {
      return Optional.empty();
    }

    // Determines the latest modification time for the source files.
    FileTime sourceFilesLastModifiedTime = FileTime.from(Instant.MIN);
    for (LayerEntry layerEntry : layerEntries) {
      FileTime lastModifiedTime = getLastModifiedTime(layerEntry.getSourceFile());
      if (lastModifiedTime.compareTo(sourceFilesLastModifiedTime) > 0) {
        sourceFilesLastModifiedTime = lastModifiedTime;
      }
    }

    // Checks if at least one of the matched layers is up-to-date.
    for (CachedLayerWithMetadata cachedLayer : cachedLayersWithSourceFiles) {
      if (cachedLayer.getMetadata() == null) {
        throw new IllegalStateException("Layers with sourceFiles should have metadata");
      }

      if (sourceFilesLastModifiedTime.compareTo(cachedLayer.getMetadata().getLastModifiedTime())
          <= 0) {
        // This layer is an up-to-date layer.
        return Optional.of(cachedLayer);
      }
    }

    return Optional.empty();
  }
}
