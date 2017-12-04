package com.google.cloud.tools.crepecake.blob;

import com.google.cloud.tools.crepecake.hash.ByteHashBuilder;
import java.io.IOException;
import java.io.OutputStream;

/** A {@link BlobStream} that streams with a {@link BlobStreamWriter} function. */
class ProvidedWriterBlobStream extends AbstractHashingBlobStream {

  private final BlobStreamWriter writer;

  ProvidedWriterBlobStream(BlobStreamWriter writer) {
    this.writer = writer;
  }

  @Override
  protected void writeToAndHash(OutputStream outputStream, ByteHashBuilder byteHashBuilder)
      throws IOException {
    writer.writeTo(outputStream);
    writer.writeTo(byteHashBuilder);
  }
}
