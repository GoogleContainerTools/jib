package com.google.cloud.tools.crepecake.blob;


import com.google.cloud.tools.crepecake.hash.ByteHashBuilder;
import com.google.cloud.tools.crepecake.image.DescriptorDigest;
import com.google.cloud.tools.crepecake.image.DigestException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.NoSuchAlgorithmException;

/** A {@link BlobStream} that streams with a {@link BlobStreamWriter} function. */
public class ProvidedWriterBlobStream implements BlobStream {

  private final BlobStreamWriter writer;

  ProvidedWriterBlobStream(BlobStreamWriter writer) {
    this.writer = writer;
  }

  @Override
  public BlobDescriptor writeTo(OutputStream outputStream) throws IOException, NoSuchAlgorithmException, DigestException {
    ByteHashBuilder byteHashBuilder = new ByteHashBuilder();

    writer.writeTo(outputStream);
    writer.writeTo(byteHashBuilder);

    DescriptorDigest digest = DescriptorDigest.fromHash(byteHashBuilder.toHash());
    return new BlobDescriptor(digest, totalBytes);
  }
}
