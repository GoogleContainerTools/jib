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
import com.google.cloud.tools.crepecake.blob.BlobStreams;
import java.io.File;

/**
 * A {@link Layer} that has been written out (i.e. to a cache) and has its file-backed content BLOB,
 * digest, size, and diff ID.
 */
public class CachedLayer extends Layer {

  private final File file;
  private final BlobDescriptor blobDescriptor;
  private final DescriptorDigest diffId;

  public CachedLayer(File file, BlobDescriptor blobDescriptor, DescriptorDigest diffId) {
    this.file = file;
    this.blobDescriptor = blobDescriptor;
    this.diffId = diffId;
  }

  /** Gets a new {@link BlobStream} for the content of the cached layer. */
  public BlobStream getBlobStream() {
    return BlobStreams.from(file);
  }

  @Override
  public LayerType getType() {
    return LayerType.CACHED;
  }

  @Override
  public BlobDescriptor getBlobDescriptor() {
    return blobDescriptor;
  }

  @Override
  public DescriptorDigest getDiffId() {
    return diffId;
  }
}
