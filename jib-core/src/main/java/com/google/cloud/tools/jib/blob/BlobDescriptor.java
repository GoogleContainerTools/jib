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

package com.google.cloud.tools.jib.blob;

import com.google.cloud.tools.jib.api.DescriptorDigest;

/** Contains properties describing a BLOB, including its digest and possibly its size (in bytes). */
public class BlobDescriptor {

  private final DescriptorDigest digest;

  /** The size of the BLOB (in bytes). Negative if unknown. */
  private final long size;

  public BlobDescriptor(long size, DescriptorDigest digest) {
    this.size = size;
    this.digest = digest;
  }

  /**
   * Initialize with just digest.
   *
   * @param digest the digest to initialize the {@link BlobDescriptor} from
   */
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

  @Override
  public String toString() {
    return "digest: " + digest + ", size: " + size;
  }
}
