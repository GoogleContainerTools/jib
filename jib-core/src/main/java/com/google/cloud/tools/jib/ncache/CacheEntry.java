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

import com.google.cloud.tools.jib.blob.Blob;
import com.google.cloud.tools.jib.image.DescriptorDigest;
import java.util.Optional;

/**
 * Represents a cache entry for a layer stored in the cache. <b>Implementations must be
 * immutable.</b>
 */
public interface CacheEntry {

  /**
   * Gets the digest of the layer.
   *
   * @return the layer digest
   */
  DescriptorDigest getLayerDigest();

  /**
   * Gets the diff ID of the layer. The diff ID is the digest of the uncompressed layer contents,
   * whereas the {@link #getLayerDigest} is the digest of the compressed layer contents.
   *
   * @return the layer diff ID
   */
  DescriptorDigest getLayerDiffId();

  /**
   * Gets the size of the layer, in bytes.
   *
   * @return the layer size
   */
  long getLayerSize();

  /**
   * Gets the {@link Blob} for the layer. This {@link Blob} should be able to be used multiple
   * times.
   *
   * @return the layer {@link Blob}
   */
  Blob getLayerBlob();

  /**
   * Gets the optional metadata blob for the layer. The metadata is in the same format as supplied
   * when writing to the cache with {@link CacheWrite}. This {@link Blob} should be able to be used
   * multiple times.
   *
   * @return the metadata {@link Blob}
   */
  Optional<Blob> getMetadataBlob();
}
