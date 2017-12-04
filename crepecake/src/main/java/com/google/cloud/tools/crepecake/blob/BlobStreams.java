package com.google.cloud.tools.crepecake.blob;

import java.io.InputStream;
import java.io.OutputStream;

/** Static initializers for {@link BlobStream}. */
public class BlobStreams {

  public static BlobStream of(InputStream inputStream) {
    return new InputStreamBackedBlobStream(inputStream);
  }

  public static BlobStream of(String content) {

  }

  public static BlobStream of(OutputStream outputStream) {

  }
}
