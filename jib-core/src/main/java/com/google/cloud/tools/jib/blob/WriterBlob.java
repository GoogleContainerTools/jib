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

package com.google.cloud.tools.jib.blob;

import com.google.cloud.tools.jib.hash.CountingDigestOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/** A {@link Blob} that writes with a {@link BlobWriter} function and hashes the bytes. */
class WriterBlob implements Blob {

  private final BlobWriter writer;

  WriterBlob(BlobWriter writer) {
    this.writer = writer;
  }

  @Override
  public BlobDescriptor writeTo(OutputStream outputStream) throws IOException {
    CountingDigestOutputStream countingDigestOutputStream =
        new CountingDigestOutputStream(outputStream);
    writer.writeTo(countingDigestOutputStream);
    countingDigestOutputStream.flush();
    return countingDigestOutputStream.toBlobDescriptor();
  }
}
