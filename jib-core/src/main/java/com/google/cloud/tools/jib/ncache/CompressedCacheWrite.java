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
import javax.annotation.concurrent.Immutable;

/**
 * Represents compressed layer data to write to the cache.
 *
 * <b>Implementation is immutable and thread-safe.</b>
 */
@Immutable
class CompressedCacheWrite {

  private final Blob compressedLayerBlob;

  CompressedCacheWrite(Blob compressedLayerBlob) {
    this.compressedLayerBlob = compressedLayerBlob;
  }

  /**
   * Gets the {@link Blob} containing the compressed layer contents.
   *
   * @return the layer {@link Blob}
   */
  Blob getCompressedLayerBlob() {
    return compressedLayerBlob;
  }
}
