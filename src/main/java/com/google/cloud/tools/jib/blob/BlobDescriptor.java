/*
 * Copyright 2018 Google Inc.
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

package com.google.cloud.tools.jib.blob;

import com.google.cloud.tools.jib.hash.CountingDigestOutputStream;
import com.google.cloud.tools.jib.image.DescriptorDigest;
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** Contains properties describing a BLOB, including its digest and possibly its size (in bytes). */
public class BlobDescriptor {

  private final DescriptorDigest digest;

  /** The size of the BLOB (in bytes). Negative if unknown. */
  private final long size;

  /**
   * Creates a new {@link BlobDescriptor} from the contents of an {@link InputStream} while piping
   * to an {@link OutputStream}. Does not close either streams.
   */
  static BlobDescriptor fromPipe(InputStream inputStream, OutputStream outputStream)
      throws IOException {
    CountingDigestOutputStream countingDigestOutputStream =
        new CountingDigestOutputStream(outputStream);
    ByteStreams.copy(inputStream, countingDigestOutputStream);
    countingDigestOutputStream.flush();
    return countingDigestOutputStream.toBlobDescriptor();
  }

  public BlobDescriptor(long size, DescriptorDigest digest) {
    this.size = size;
    this.digest = digest;
  }

  /** Initialize with just digest. */
  public BlobDescriptor(DescriptorDigest digest) {
    this(-1, digest);
  }

  public boolean hasSize() {
    return size >= 0;
  }

  public DescriptorDigest getDigest() {
    return digest;
  }

  public long getSize() {
    return size;
  }

  /**
   * Two {@link BlobDescriptor} objects are equal if their
   *
   * <ol>
   *   <li>{@code digest}s are not null and equal, and
   *   <li>{@code size}s are non-negative and equal
   * </ol>
   */
  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (size < 0 || !(obj instanceof BlobDescriptor)) {
      return false;
    }

    BlobDescriptor other = (BlobDescriptor) obj;
    return size == other.getSize() && digest.equals(other.getDigest());
  }

  @Override
  public int hashCode() {
    int result = digest.hashCode();
    result = 31 * result + (int) (size ^ (size >>> 32));
    return result;
  }
}
