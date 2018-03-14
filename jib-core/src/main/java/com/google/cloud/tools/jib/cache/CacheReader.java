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

package com.google.cloud.tools.jib.cache;

import com.google.cloud.tools.jib.image.DescriptorDigest;
import com.google.cloud.tools.jib.image.ImageLayers;
import com.google.cloud.tools.jib.image.LayerPropertyNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import javax.annotation.Nullable;

/** Reads image content from the cache. */
public class CacheReader {

  /**
   * @return the last modified time for the file at {@code path}. Recursively finds the most recent
   *     last modified time for all subfiles if the file is a directory.
   */
  private static FileTime getLastModifiedTime(Path path) throws IOException {
    FileTime lastModifiedTime = Files.getLastModifiedTime(path);

    if (Files.isReadable(path)) {
      try (Stream<Path> fileStream = Files.walk(path)) {
        Optional<FileTime> maxLastModifiedTime =
            fileStream
                .map(
                    subFilePath -> {
                      try {
                        return Files.getLastModifiedTime(subFilePath);

                      } catch (IOException ex) {
                        throw new UncheckedIOException(ex);
                      }
                    })
                .max(FileTime::compareTo);

        if (!maxLastModifiedTime.isPresent()) {
          throw new IllegalStateException(
              "Could not get last modified time for all files in directory '" + path + "'");
        }
        if (maxLastModifiedTime.get().compareTo(lastModifiedTime) > 0) {
          lastModifiedTime = maxLastModifiedTime.get();
        }

      } catch (UncheckedIOException ex) {
        throw ex.getCause();
      }
    }

    return lastModifiedTime;
  }

  private final Cache cache;

  public CacheReader(Cache cache) {
    this.cache = cache;
  }

  /** @return the cached layer with digest {@code layerDigest}, or {@code null} if not found */
  @Nullable
  public CachedLayer getLayer(DescriptorDigest layerDigest) throws LayerPropertyNotFoundException {
    return cache.getMetadata().getLayers().get(layerDigest);
  }

  /**
   * Finds the file that stores the content BLOB for an application layer.
   *
   * @param sourceFiles the source files the layer must be built from
   * @return the newest cached layer file that matches the {@code layerType} and {@code
   *     sourceFiles}, or {@code null} if there is no match
   */
  @Nullable
  public Path getLayerFile(List<Path> sourceFiles) throws CacheMetadataCorruptedException {
    CacheMetadata cacheMetadata = cache.getMetadata();
    ImageLayers<CachedLayerWithMetadata> cachedLayers =
        cacheMetadata.filterLayers().bySourceFiles(sourceFiles).filter();

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
   * should have the same file contents)
   */
  @Nullable
  public CachedLayer getUpToDateLayerBySourceFiles(List<Path> sourceFiles)
      throws IOException, CacheMetadataCorruptedException {
    // Grabs all the layers that have matching source files.
    ImageLayers<CachedLayerWithMetadata> cachedLayersWithSourceFiles =
        cache.getMetadata().filterLayers().bySourceFiles(sourceFiles).filter();
    if (cachedLayersWithSourceFiles.isEmpty()) {
      return null;
    }

    // Determines the latest modification time for the source files.
    FileTime sourceFilesLastModifiedTime = FileTime.from(Instant.MIN);
    for (Path path : sourceFiles) {
      FileTime lastModifiedTime = getLastModifiedTime(path);
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
        return cachedLayer;
      }
    }

    return null;
  }
}
