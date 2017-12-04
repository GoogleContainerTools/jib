package com.google.cloud.tools.crepecake.blob;

import com.google.api.client.http.HttpContent;

import java.io.IOException;
import java.io.OutputStream;

class BlobStreamHttpContent implements HttpContent {


  @Override
  public long getLength() throws IOException {
    return 0;
  }

  @Override
  public String getType() {
    return null;
  }

  @Override
  public boolean retrySupported() {
    return false;
  }

  @Override
  public void writeTo(OutputStream out) throws IOException {

  }
}
