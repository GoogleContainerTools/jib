package com.google.cloud.tools.crepecake.blob;

import com.google.api.client.http.HttpContent;
import java.io.IOException;
import java.io.OutputStream;
import java.security.DigestException;

/** {@link BlobStream}-backed {@link HttpContent}. */
class BlobStreamHttpContent implements HttpContent {

  private final BlobStream blobStream;

  BlobStreamHttpContent(BlobStream blobStream) {
    this.blobStream = blobStream;
  }

  @Override
  public long getLength() throws IOException {
    // Returns negative value for unknown length.
    return -1;
  }

  @Override
  public String getType() {
    // TODO: This should probably return the actual Content-Type.
    return null;
  }

  @Override
  public boolean retrySupported() {
    return false;
  }

  @Override
  public void writeTo(OutputStream outputStream) throws IOException {
    try {
      blobStream.writeTo(outputStream);
    } catch (DigestException ex) {
      throw new IOException(ex);
    }
  }
}
