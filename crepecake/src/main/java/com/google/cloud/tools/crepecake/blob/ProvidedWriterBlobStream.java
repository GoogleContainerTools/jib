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
import java.io.IOException;
import java.io.OutputStream;

/** A {@link BlobStream} that streams with a {@link BlobStreamWriter} function. */
class ProvidedWriterBlobStream extends AbstractHashingBlobStream {

  private final BlobStreamWriter writer;

  ProvidedWriterBlobStream(BlobStreamWriter writer) {
    this.writer = writer;
  }

  @Override
  protected void writeToAndHash(OutputStream outputStream, ByteHashBuilder byteHashBuilder)
      throws IOException {
    writer.writeTo(outputStream);
    writer.writeTo(byteHashBuilder);
  }
}
