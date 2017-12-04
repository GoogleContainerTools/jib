package com.google.cloud.tools.crepecake.blob;

import com.google.cloud.tools.crepecake.hash.ByteHashBuilder;
import com.google.cloud.tools.crepecake.image.DescriptorDigest;
import com.google.cloud.tools.crepecake.image.DigestException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.NoSuchAlgorithmException;

/** A {@link BlobStream} that streams from an {@link InputStream}. */
class InputStreamBackedBlobStream implements BlobStream {

  private final InputStream inputStream;

  private final byte[] byteBuffer = new byte[8192];

  InputStreamBackedBlobStream(InputStream inputStream) {
    this.inputStream = inputStream;
  }

  @Override
  public BlobDescriptor writeTo(OutputStream outputStream) throws IOException, NoSuchAlgorithmException, DigestException {
    ByteHashBuilder byteHashBuilder = new ByteHashBuilder();

    long totalBytes = 0;

    int bytesRead;
    while ((bytesRead = inputStream.read(byteBuffer)) != -1) {
      // Writes to the output stream and builds the BLOB's hash as well.
      outputStream.write(byteBuffer, 0, bytesRead);
      byteHashBuilder.append(byteBuffer, 0, bytesRead);
      totalBytes += bytesRead;
    }

    DescriptorDigest digest = DescriptorDigest.fromHash(byteHashBuilder.buildHash());
    return new BlobDescriptor(digest, totalBytes);
  }
}
