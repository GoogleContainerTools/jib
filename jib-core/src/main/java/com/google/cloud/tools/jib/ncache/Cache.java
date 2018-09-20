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

package com.google.cloud.tools.jib.ncache;

import com.google.cloud.tools.jib.image.DescriptorDigest;
import com.google.cloud.tools.jib.image.LayerEntry;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.Optional;

/** Cache for storing data to be shared between Jib executions. */
public class Cache {

  private final CacheStorage cacheStorage;

  // TODO: Add way to instantiate after #retrieveCacheEntry and #write are implemented.
  private Cache(CacheStorage cacheStorage) {
    this.cacheStorage = cacheStorage;
  }

  /**
   * Retrieves the {@link CacheEntry} that was built from the {@code layerEntries}.
   *
   * @param layerEntries the layer entries to match against
   * @return a {@link CacheEntry} that was built from {@code layerEntries}, or {@link
   *     Optional#empty} if none found
   * @throws IOException if an I/O exception occurs
   * @throws CacheCorruptedException if the cache is corrupted
   */
  // TODO: Add test once other unimplemented methods are implemented.
  public Optional<CacheEntry> retrieveCacheEntry(ImmutableList<LayerEntry> layerEntries)
      throws IOException, CacheCorruptedException {
    Optional<DescriptorDigest> optionalSelectedLayerDigest =
        cacheStorage.select(LayerEntriesSelector.generateSelector(layerEntries));
    if (!optionalSelectedLayerDigest.isPresent()) {
      return Optional.empty();
    }

    return cacheStorage.retrieve(optionalSelectedLayerDigest.get());
  }

  public Optional<CacheEntry> retrieveCacheEntry(DescriptorDigest layerDigest) {
    // TODO: Implement
    return Optional.empty();
  }

  public Optional<CacheEntry> write(CacheWrite cacheWrite) {
    // TODO: Implement
    return Optional.empty();
  }
}
