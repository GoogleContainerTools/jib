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

package com.google.cloud.tools.crepecake.image;

import com.google.cloud.tools.crepecake.blob.BlobStream;

/** Different types of {@link Layer}s have different properties. */
public enum LayerType {

  /**
   * A layer that has not been written out and only has the unwritten content {@link BlobStream}.
   * Once written, this layer becomes a {@code CACHED} layer.
   */
  UNWRITTEN,

  /**
   * A layer that has been written out (i.e. to a cache) and has its file-backed content BLOB,
   * digest, size, and diff ID.
   */
  CACHED,

  /**
   * A layer that does not have its content BLOB. It is only referenced by its digest, size, and
   * diff ID.
   */
  REFERENCE,

  /**
   * A layer that has its content BLOB, digest, and size, but not its diff ID. The content BLOB can
   * be decompressed to get the diff ID.
   */
  REFERENCE_NO_DIFF_ID,
}
