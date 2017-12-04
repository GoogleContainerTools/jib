package com.google.cloud.tools.crepecake.blob;

import com.google.api.client.http.FileContent;
import com.google.api.client.http.HttpContent;

import java.io.File;

/** Static methods for constructing and using specific implementations of {@link BlobStream}. */
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

  /** Wraps a {@link BlobStream} in an {@link HttpContent}. */
  public static HttpContent toHttpContent(BlobStream blobStream) {
    return new BlobStreamHttpContent(blobStream);
  }
}
