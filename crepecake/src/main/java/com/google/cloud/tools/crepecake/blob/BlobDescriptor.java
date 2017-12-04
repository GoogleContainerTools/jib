package com.google.cloud.tools.crepecake.blob;

import com.google.cloud.tools.crepecake.image.DescriptorDigest;

/** Contains properties describing a BLOB, including its digest and size (in bytes). */
public class BlobDescriptor {

  private final DescriptorDigest digest;
  private final long size;

  public BlobDescriptor(DescriptorDigest digest, long size) {
    this.digest = digest;
    this.size = size;
  }

  public DescriptorDigest getDigest() {
    return digest;
  }

  public long getSize() {
    return size;
  }
}
