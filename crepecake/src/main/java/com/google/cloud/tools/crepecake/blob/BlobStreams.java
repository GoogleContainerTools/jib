package com.google.cloud.tools.crepecake.blob;

import java.io.InputStream;

/** Static initializers for {@link BlobStream}. */
public class BlobStreams {

  public static BlobStream empty() {
    return new EmptyBlobStream();
  }

  public static BlobStream from(InputStream inputStream) {
    return new ProvidedInputStreamBlobStream(inputStream);
  }

  public static BlobStream from(String content) {
    return new ProvidedStringBlobStream(content);
  }

  public static BlobStream from(BlobStreamWriter writer) {
    return new ProvidedWriterBlobStream(writer);
  }
}
