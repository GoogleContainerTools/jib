package com.google.cloud.tools.crepecake.blob;

import com.google.cloud.tools.crepecake.hash.ByteHashBuilder;
import com.google.common.base.Charsets;
import java.io.IOException;
import java.io.OutputStream;

class ProvidedStringBlobStream extends AbstractHashingBlobStream {

  private final byte[] contentBytes;

  ProvidedStringBlobStream(String content) {
    contentBytes = content.getBytes(Charsets.UTF_8);
  }

  @Override
  protected void writeToAndHash(OutputStream outputStream, ByteHashBuilder byteHashBuilder)
      throws IOException {
    outputStream.write(contentBytes);
    byteHashBuilder.write(contentBytes);
  }
}
