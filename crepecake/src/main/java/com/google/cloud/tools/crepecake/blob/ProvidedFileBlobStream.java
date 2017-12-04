/*
 * Copyright 2017 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

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
