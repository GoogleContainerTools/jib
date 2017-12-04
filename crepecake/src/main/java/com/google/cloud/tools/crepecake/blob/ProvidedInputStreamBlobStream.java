package com.google.cloud.tools.crepecake.blob;

import com.google.cloud.tools.crepecake.hash.ByteHashBuilder;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** A {@link BlobStream} that streams from an {@link InputStream}. */
class ProvidedInputStreamBlobStream extends AbstractHashingBlobStream {

  private final InputStream inputStream;

  private final byte[] byteBuffer = new byte[8192];

  ProvidedInputStreamBlobStream(InputStream inputStream) {
    this.inputStream = inputStream;
  }

  @Override
  protected void writeToAndHash(OutputStream outputStream, ByteHashBuilder byteHashBuilder)
      throws IOException {
    int bytesRead;
    while ((bytesRead = inputStream.read(byteBuffer)) != -1) {
      // Writes to the output stream and builds the BLOB's hash as well.
      outputStream.write(byteBuffer, 0, bytesRead);
      byteHashBuilder.write(byteBuffer, 0, bytesRead);
    }
  }
}
