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

import com.google.cloud.tools.jib.blob.Blob;
import com.google.cloud.tools.jib.image.DescriptorDigest;
import com.google.cloud.tools.jib.image.LayerEntry;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import javax.annotation.concurrent.Immutable;

/**
 * Cache for storing data to be shared between Jib executions.
 *
 * <p>Uses the default cache storage engine ({@link DefaultCacheStorage}) with layer entries as the
 * selector ({@link LayerEntriesSelector}).
 *
 * <p>This class is immutable and safe to use across threads.
 */
@Immutable
public class Cache {

  /**
   * Initializes the cache using {@code cacheDirectory} for storage.
   *
   * @param cacheDirectory the directory for the cache. Creates the directory if it does not exist.
   * @return a new {@link Cache}
   * @throws IOException if an I/O exception occurs
   */
  public static Cache withDirectory(Path cacheDirectory) throws IOException {
    Files.createDirectories(cacheDirectory);
    return new Cache(DefaultCacheStorage.withDirectory(cacheDirectory));
  }

  private final CacheStorage cacheStorage;

  private Cache(CacheStorage cacheStorage) {
    this.cacheStorage = cacheStorage;
  }

  /**
   * Saves a cache entry with a compressed layer {@link Blob}. Use {@link
   * #writeUncompressedLayer(Blob, ImmutableList)} to save a cache entry with an uncompressed layer
   * {@link Blob} and include a selector.
   *
   * @param compressedLayerBlob the compressed layer {@link Blob}
   * @return the {@link CachedLayer} for the written layer
   * @throws IOException if an I/O exception occurs
   */
  public CachedLayer writeCompressedLayer(Blob compressedLayerBlob) throws IOException {
    return cacheStorage.write(compressedLayerBlob);
  }

  /**
   * Saves a cache entry with an uncompressed layer {@link Blob} and an additional selector digest.
   * Use {@link #writeCompressedLayer(Blob)} to save a compressed layer {@link Blob}.
   *
   * @param uncompressedLayerBlob the layer {@link Blob}
   * @param layerEntries the layer entries that make up the layer
   * @return the {@link CachedLayer} for the written layer
   * @throws IOException if an I/O exception occurs
   */
  public CachedLayer writeUncompressedLayer(
      Blob uncompressedLayerBlob, ImmutableList<LayerEntry> layerEntries) throws IOException {
    return cacheStorage.write(
        new UncompressedCacheWrite(
            uncompressedLayerBlob, LayerEntriesSelector.generateSelector(layerEntries)));
  }

  /**
   * Retrieves the {@link CachedLayer} that was built from the {@code layerEntries}.
   *
   * @param layerEntries the layer entries to match against
   * @return a {@link CachedLayer} that was built from {@code layerEntries}, if found
   * @throws IOException if an I/O exception occurs
   * @throws CacheCorruptedException if the cache is corrupted
   */
  public Optional<CachedLayer> retrieve(ImmutableList<LayerEntry> layerEntries)
      throws IOException, CacheCorruptedException {
    Optional<DescriptorDigest> optionalSelectedLayerDigest =
        cacheStorage.select(LayerEntriesSelector.generateSelector(layerEntries));
    if (!optionalSelectedLayerDigest.isPresent()) {
      return Optional.empty();
    }

    return cacheStorage.retrieve(optionalSelectedLayerDigest.get());
  }

  /**
   * Retrieves the {@link CachedLayer} for the layer with digest {@code layerDigest}.
   *
   * @param layerDigest the layer digest
   * @return the {@link CachedLayer} referenced by the layer digest, if found
   * @throws CacheCorruptedException if the cache was found to be corrupted
   * @throws IOException if an I/O exception occurs
   */
  public Optional<CachedLayer> retrieve(DescriptorDigest layerDigest)
      throws IOException, CacheCorruptedException {
    return cacheStorage.retrieve(layerDigest);
  }
}
