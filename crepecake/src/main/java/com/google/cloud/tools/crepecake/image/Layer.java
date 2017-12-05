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

import com.google.cloud.tools.crepecake.blob.BlobDescriptor;
import com.google.cloud.tools.crepecake.blob.BlobStream;
import javax.annotation.Nullable;

/**
 * Represents a layer in an image.
 *
 * <p>An image layer consists of:
 *
 * <ul>
 *   <li>Content BLOB
 *   <li>
 *       <ul>
 *         <li>The compressed archive (tarball gzip) of the partial filesystem changeset.
 *       </ul>
 *
 *   <li>Content Digest
 *   <li>
 *       <ul>
 *         <li>The SHA-256 hash of the content BLOB.
 *       </ul>
 *
 *   <li>Content Size
 *   <li>
 *       <ul>
 *         <li>The size (in bytes) of the content BLOB.
 *       </ul>
 *
 *   <li>Diff ID
 *   <li>
 *       <ul>
 *         <li>The SHA-256 hash of the uncompressed archive (tarball) of the partial filesystem
 *             changeset.
 *       </ul>
 *
 * </ul>
 */
public class Layer {

  /** Different types have different properties. */
  protected enum Type {
    /** A layer that has not been written out and only has the unwritten content {@link BlobStream}. Once written, this layer becomes a {@code CACHED} layer. */
    UNWRITTEN,

    /** A layer that has been written out (i.e. to a cache) and has its file-backed content BLOB, digest, size, and diff ID. */
    CACHED,

    /** A layer that does not have its content BLOB. It is only referenced by its digest, size, and diff ID. */
    REFERENCE,

    /** A layer that has its content BLOB, digest, and size, but not its diff ID. The content BLOB can be decompressed to get the diff ID. */
    REFERENCE_NO_DIFF_ID,
  }

  private Type type;
  private LayerProvider layerProvider;

  public static Layer newUnwritten(BlobStream compressedBlobStream, BlobStream uncompressedBlobStream) {
    return new Layer(Type.UNWRITTEN, new UnwrittenLayerProvider(compressedBlobStream, uncompressedBlobStream));
  }

  private Layer(Type type, LayerProvider layerProvider) {
    this.type = type;
    this.layerProvider = layerProvider;
  }


}
