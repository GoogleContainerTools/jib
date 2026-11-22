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

package com.google.cloud.tools.jib.image;

import com.google.cloud.tools.jib.api.DescriptorDigest;
import com.google.cloud.tools.jib.api.buildplan.CompressionAlgorithm;
import com.google.cloud.tools.jib.blob.Blob;
import com.google.cloud.tools.jib.blob.BlobDescriptor;

/**
 * Represents a layer in an image. Implementations represent the various types of layers.
 *
 * <p>An image layer consists of:
 *
 * <ul>
 *   <li>Content BLOB
 *   <li>
 *       <ul>
 *         <li>The compressed archive (tarball gzip or zstd) of the partial filesystem changeset.
 *       </ul>
 *   <li>Content Digest
 *   <li>
 *       <ul>
 *         <li>The SHA-256 hash of the content BLOB.
 *       </ul>
 *   <li>Content Size
 *   <li>
 *       <ul>
 *         <li>The size (in bytes) of the content BLOB.
 *       </ul>
 *   <li>Diff ID
 *   <li>
 *       <ul>
 *         <li>The SHA-256 hash of the uncompressed archive (tarball) of the partial filesystem
 *             changeset.
 *       </ul>
 * </ul>
 */
public interface Layer {

  /**
   * Returns the compressed archive (tarball gzip or zstd) of the partial filesystem changeset.
   *
   * @return the layer's content BLOB
   * @throws LayerPropertyNotFoundException if not available
   */
  Blob getBlob() throws LayerPropertyNotFoundException;

  // TODO: Remove this
  /**
   * Returns this layer's content descriptor.
   *
   * @return the layer's content {@link BlobDescriptor}
   * @throws LayerPropertyNotFoundException if not available
   */
  BlobDescriptor getBlobDescriptor() throws LayerPropertyNotFoundException;

  /**
   * Returns the SHA-256 hash of the uncompressed archive (tarball) of the partial filesystem
   * changeset.
   *
   * @return the layer's diff ID
   * @throws LayerPropertyNotFoundException if not available
   */
  DescriptorDigest getDiffId() throws LayerPropertyNotFoundException;

  /**
   * Return the layer archive compression algorithm, based on declared metadata.
   *
   * <p>When decompressing, the actual compression algorithm should rather be deducted by looking at
   * magic bytes in the blob.
   *
   * @return the layer's compression algorithm
   * @throws LayerPropertyNotFoundException if not available
   */
  CompressionAlgorithm getCompressionAlgorithm() throws LayerPropertyNotFoundException;
}
