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

package com.google.cloud.tools.crepecake.blob;

import com.google.cloud.tools.crepecake.hash.ByteHashBuilder;
import com.google.cloud.tools.crepecake.image.DescriptorDigest;
import com.google.cloud.tools.crepecake.image.DigestException;
import java.io.IOException;
import java.io.OutputStream;
import java.security.NoSuchAlgorithmException;

abstract class AbstractHashingBlobStream implements BlobStream {

  private BlobDescriptor writtenBlobDescriptor;

  /**
   * Writes to an {@link OutputStream} and appends the bytes written to a {@link ByteHashBuilder}.
   *
   * @param outputStream the {@link OutputStream} to write to
   * @param byteHashBuilder the {@link ByteHashBuilder} to write to as well
   */
  protected abstract void writeToAndHash(OutputStream outputStream, ByteHashBuilder byteHashBuilder)
      throws IOException;

  @Override
  public void writeTo(OutputStream outputStream)
      throws IOException, NoSuchAlgorithmException, DigestException {
    ByteHashBuilder byteHashBuilder = new ByteHashBuilder();

    writeToAndHash(outputStream, byteHashBuilder);

    DescriptorDigest digest = DescriptorDigest.fromHash(byteHashBuilder.toHash());
    long totalBytes = byteHashBuilder.getTotalBytes();
    writtenBlobDescriptor = new BlobDescriptor(digest, totalBytes);
  }

  @Override
  public BlobDescriptor getWrittenBlobDescriptor() {
    return writtenBlobDescriptor;
  }
}
