package com.google.cloud.tools.crepecake.blob;

import com.google.cloud.tools.crepecake.hash.ByteHashBuilder;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** A {@link BlobStream} that streams from a {@link File}. */
class ProvidedFileBlobStream extends AbstractHashingBlobStream {

  private final File file;

  private final byte[] byteBuffer = new byte[8192];

  ProvidedFileBlobStream(File file) {
    this.file = file;
  }

  @Override
  protected void writeToAndHash(OutputStream outputStream, ByteHashBuilder byteHashBuilder)
      throws IOException {
    InputStream fileStream = new BufferedInputStream(new FileInputStream(file));

    int bytesRead;
    while ((bytesRead = fileStream.read(byteBuffer)) != -1) {
      // Writes to the output stream and builds the BLOB's hash as well.
      outputStream.write(byteBuffer, 0, bytesRead);
      byteHashBuilder.write(byteBuffer, 0, bytesRead);
    }
  }
}
