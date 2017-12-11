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

import com.google.cloud.tools.crepecake.image.DescriptorDigest;
import javax.annotation.Nullable;

/** Contains properties describing a BLOB, including size (in bytes) and possibly its digest. */
public class BlobDescriptor {

  @Nullable private final DescriptorDigest digest;

  private final long size;

  public BlobDescriptor(long size, DescriptorDigest digest) {
    this.size = size;
    this.digest = digest;
  }

  public BlobDescriptor(long size) {
    this(size, null);
  }

  public boolean hasDigest() {
    return digest != null;
  }

  @Nullable
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
   *   <li>{@code digest}s are not null and equal
   *   <li>{@code size} is equal
   * </ol>
   */
  @Override
  public boolean equals(Object obj) {
    if (digest == null || !(obj instanceof BlobDescriptor)) {
      return false;
    }

    BlobDescriptor other = (BlobDescriptor) obj;
    return digest.equals(other.getDigest()) && size == other.getSize();
  }

  @Override
  public int hashCode() {
    int result = digest != null ? digest.hashCode() : 0;
    result = 31 * result + (int) (size ^ (size >>> 32));
    return result;
  }
}
