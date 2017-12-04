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
