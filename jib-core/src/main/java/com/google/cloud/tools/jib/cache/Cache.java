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

import com.google.cloud.tools.jib.blob.Blobs;
import com.google.cloud.tools.jib.cache.json.CacheMetadataTemplate;
import com.google.cloud.tools.jib.json.JsonTemplateMapper;
import com.google.common.annotations.VisibleForTesting;
import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.util.List;

/** Manages a cache. Implementation is thread-safe. */
public class Cache implements Closeable {

  /**
   * Initializes a cache with a directory. This also loads the cache metadata if it exists in the
   * directory.
   *
   * @param cacheDirectory the directory to use for the cache.
   * @return the initialized cache.
   * @throws NotDirectoryException if {@code cacheDirectory} is not a directory.
   * @throws CacheMetadataCorruptedException if loading the cache metadata fails.
   */
  public static Cache init(Path cacheDirectory)
      throws NotDirectoryException, CacheMetadataCorruptedException {
    if (!Files.isDirectory(cacheDirectory)) {
      throw new NotDirectoryException("The cache can only write to a directory");
    }
    CacheMetadata cacheMetadata = loadCacheMetadata(cacheDirectory);

    return new Cache(cacheDirectory, cacheMetadata);
  }

  private static CacheMetadata loadCacheMetadata(Path cacheDirectory)
      throws CacheMetadataCorruptedException {
    Path cacheMetadataJsonFile = cacheDirectory.resolve(CacheFiles.METADATA_FILENAME);

    if (!Files.exists(cacheMetadataJsonFile)) {
      return CacheMetadata.builder().build();
    }

    try {
      CacheMetadataTemplate cacheMetadataJson =
          JsonTemplateMapper.readJsonFromFileWithLock(
              cacheMetadataJsonFile, CacheMetadataTemplate.class);
      return CacheMetadataTranslator.fromTemplate(cacheMetadataJson, cacheDirectory);
    } catch (IOException ex) {
      // The cache metadata is probably corrupted.
      throw new CacheMetadataCorruptedException(ex);
    }
  }

  /** The path to the root of the cache. */
  private final Path cacheDirectory;

  /** The metadata that corresponds to the cache at {@link #cacheDirectory}. */
  private final CacheMetadata cacheMetadata;

  /** Builds the updated cache metadata to save back to the cache. */
  private final CacheMetadata.Builder cacheMetadataBuilder;

  private Cache(Path cacheDirectory, CacheMetadata cacheMetadata) {
    this.cacheDirectory = cacheDirectory;
    this.cacheMetadata = cacheMetadata;
    cacheMetadataBuilder = cacheMetadata.newAppendingBuilder();
  }

  /**
   * Finishes the use of the cache by flushing any unsaved changes.
   *
   * @throws IOException if saving the cache metadata fails.
   */
  @Override
  public void close() throws IOException {
    saveCacheMetadata(cacheDirectory);
  }

  /**
   * Adds the cached layer to the cache metadata. This is <b>NOT</b> thread-safe.
   *
   * @param cachedLayers the layers to add
   */
  public void addCachedLayersToMetadata(List<CachedLayer> cachedLayers) {
    for (CachedLayer cachedLayer : cachedLayers) {
      cacheMetadataBuilder.addLayer(new CachedLayerWithMetadata(cachedLayer, null));
    }
  }

  /**
   * Adds the cached layer to the cache metadata. This is <b>NOT</b> thread-safe.
   *
   * @param cachedLayersWithMetadata the layers to add
   */
  public void addCachedLayersWithMetadataToMetadata(
      List<CachedLayerWithMetadata> cachedLayersWithMetadata) {
    for (CachedLayerWithMetadata cachedLayerWithMetadata : cachedLayersWithMetadata) {
      cacheMetadataBuilder.addLayer(cachedLayerWithMetadata);
    }
  }

  @VisibleForTesting
  Path getCacheDirectory() {
    return cacheDirectory;
  }

  @VisibleForTesting
  CacheMetadata getMetadata() {
    return cacheMetadata;
  }

  @VisibleForTesting
  CacheMetadata getUpdatedMetadata() {
    return cacheMetadataBuilder.build();
  }

  /** Saves the updated cache metadata back to the cache. */
  private void saveCacheMetadata(Path cacheDirectory) throws IOException {
    Path cacheMetadataJsonFile = cacheDirectory.resolve(CacheFiles.METADATA_FILENAME);

    CacheMetadataTemplate cacheMetadataJson =
        CacheMetadataTranslator.toTemplate(cacheMetadataBuilder.build());

    Blobs.writeToFileWithLock(JsonTemplateMapper.toBlob(cacheMetadataJson), cacheMetadataJsonFile);
  }
}
