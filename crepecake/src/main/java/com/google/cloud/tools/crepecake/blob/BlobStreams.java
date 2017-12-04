package com.google.cloud.tools.crepecake.blob;

import java.io.File;

/** Static initializers for {@link BlobStream}. */
public class BlobStreams {

  public static BlobStream empty() {
    return new EmptyBlobStream();
  }

  public static BlobStream from(File file) {
    return new ProvidedFileBlobStream(file);
  }

  public static BlobStream from(String content) {
    return new ProvidedStringBlobStream(content);
  }

  public static BlobStream from(BlobStreamWriter writer) {
    return new ProvidedWriterBlobStream(writer);
  }
}
