package com.google.cloud.tools.crepecake.blob;

import java.io.InputStream;

/** Static initializers for {@link BlobStream}. */
public class BlobStreams {

  public static BlobStream of(InputStream inputStream) {
    return new ProvidedInputStreamBlobStream(inputStream);
  }

  public static BlobStream of(String content) {

  }

  public static BlobStream of(BlobStreamWriter writer) {
    return new ProvidedWriterBlobStream(writer);
  }
}
