package com.google.cloud.tools.crepecake.blob;

import java.io.IOException;
import java.io.OutputStream;

@FunctionalInterface
public interface BlobStreamWriter {

  void writeTo(OutputStream outputStream) throws IOException;
}
