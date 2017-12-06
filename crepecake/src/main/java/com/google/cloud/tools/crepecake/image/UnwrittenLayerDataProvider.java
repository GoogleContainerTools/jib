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
import com.google.common.io.ByteStreams;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.NoSuchAlgorithmException;

/** A layer that has not been written out and only has the unwritten content {@link BlobStream}. */
class UnwrittenLayerDataProvider extends LayerDataProvider {

  private final BlobStream compressedBlobStream;
  private final BlobStream uncompressedBlobStream;

  /**
   * @param compressedBlobStream the compressed {@link BlobStream} of the layer content
   * @param uncompressedBlobStream the uncompressed {@link BlobStream} of the layer content
   */
  UnwrittenLayerDataProvider(BlobStream compressedBlobStream, BlobStream uncompressedBlobStream) {
    this.compressedBlobStream = compressedBlobStream;
    this.uncompressedBlobStream = uncompressedBlobStream;
  }

  /**
   * Writes the compressed layer BLOB to a file and returns a {@link CachedLayerDataProvider} that
   * represents the new cached layer.
   */
  CachedLayerDataProvider writeTo(File file)
      throws NoSuchAlgorithmException, IOException, DigestException {
    try (OutputStream fileOutputStream = new BufferedOutputStream(new FileOutputStream(file))) {
      compressedBlobStream.writeTo(fileOutputStream);
      uncompressedBlobStream.writeTo(ByteStreams.nullOutputStream());

      BlobDescriptor blobDescriptor = compressedBlobStream.getWrittenBlobDescriptor();
      DescriptorDigest diffId = uncompressedBlobStream.getWrittenBlobDescriptor().getDigest();

      return new CachedLayerDataProvider(file, blobDescriptor, diffId);
    }
  }

  @Override
  BlobDescriptor getBlobDescriptor() throws LayerException {
    throw new LayerException("Blob descriptor not available for unwritten layer");
  }

  @Override
  DescriptorDigest getDiffId() throws LayerException {
    throw new LayerException("Diff ID not available for unwritten layer");
  }
}
