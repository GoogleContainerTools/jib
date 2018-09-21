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
import java.io.IOException;
import java.util.Optional;
import java.util.Set;

/**
 * Interface for queries to a cache storage engine.
 *
 * <p>The cache storage engine stores layer data as {@link CacheWrite}s. These entries are read out
 * as {@link CacheEntry}s. Cache entries can be retrieved by the layer digest.
 *
 * <p>The cache entries can also be queried by an arbitrarily-defined selector (in digest format).
 * The selectors do not need to be unique. An example of a selector could be the digest of the list
 * of source file paths that constructed the cached layer.
 *
 * <p><b>Implementations must be thread-safe and should be immutable.</b>
 */
public interface CacheStorage {

  /**
   * Saves the {@link CacheWrite}.
   *
   * @param cacheWrite the {@link CacheWrite}
   * @return the {@link CacheEntry} for the written {@link CacheWrite}
   * @throws IOException if an I/O exception occurs
   */
  CacheEntry write(CacheWrite cacheWrite) throws IOException;

  /**
   * Fetches all the layer digests stored.
   *
   * @return the set of layer digests (that can be retrieved via {@link #retrieve})
   * @throws CacheCorruptedException if the cache was found to be corrupted
   * @throws IOException if an I/O exception occurs
   */
  Set<DescriptorDigest> fetchDigests() throws IOException, CacheCorruptedException;

  /**
   * Retrieves the {@link CacheEntry} for the layer with digest {@code layerDigest}.
   *
   * @param layerDigest the layer digest
   * @return the {@link CacheEntry} referenced by the layer digest
   * @throws CacheCorruptedException if the cache was found to be corrupted
   * @throws IOException if an I/O exception occurs
   */
  Optional<CacheEntry> retrieve(DescriptorDigest layerDigest)
      throws IOException, CacheCorruptedException;

  /**
   * Queries for layer digests that can be selected with the {@code selector}.
   *
   * @param selector the selector to query with
   * @return the layer digest selected, or {@link Optional#empty} if none found
   * @throws IOException if an I/O exception occurs
   */
  Optional<DescriptorDigest> select(DescriptorDigest selector) throws IOException;
}
