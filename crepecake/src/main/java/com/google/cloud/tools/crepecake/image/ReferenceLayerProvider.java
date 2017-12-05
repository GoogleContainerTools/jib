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

/** A {@link Layer} reference that <b>does not</b> have the underlying content. It references the layer with its digest, size, and diff ID. */
class ReferenceLayerProvider implements LayerProvider {

  /** The {@link BlobDescriptor} of the compressed layer content. */
  private final BlobDescriptor blobDescriptor;

  /** The digest of the uncompressed layer content. */
  private final DescriptorDigest diffId;

  /**
   * Instantiate with the digest, size, and diff ID. This can be used to instantiate from references to remote layers.
   */
  ReferenceLayerProvider(DescriptorDigest digest, long size, DescriptorDigest diffId) {
    this(new BlobDescriptor(size, digest), diffId);
  }

  /**
   * Instantiate with a {@link BlobDescriptor} and diff ID. This can be used to instantiate from {@link BlobStream}-constructed layers (i.e. {@link BlobStream} generates a {@link BlobDescriptor}).
   */
  ReferenceLayerProvider(BlobDescriptor blobDescriptor, DescriptorDigest diffId) {
    this.blobDescriptor = blobDescriptor;
    this.diffId = diffId;
  }

  BlobDescriptor getBlobDescriptor() {
    return blobDescriptor;
  }

  DescriptorDigest getDiffId() {
    return diffId;
  }
}
