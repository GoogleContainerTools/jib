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

import com.google.cloud.tools.crepecake.image.DigestException;
import com.google.common.base.Charsets;
import java.io.IOException;
import java.io.OutputStream;
import java.security.NoSuchAlgorithmException;

/** A {@link BlobStream} that streams from a {@link String}. */
class StringBlobStream implements BlobStream {

  private final String content;

  private BlobDescriptor writtenBlobDescriptor;

  StringBlobStream(String content) {
    this.content = content;
  }

  @Override
  public void writeTo(OutputStream outputStream)
      throws IOException, NoSuchAlgorithmException, DigestException {
    byte[] contentBytes = content.getBytes(Charsets.UTF_8);
    outputStream.write(contentBytes);
    outputStream.flush();
    writtenBlobDescriptor = new BlobDescriptor(content.length());
  }

  @Override
  public BlobDescriptor getWrittenBlobDescriptor() {
    return writtenBlobDescriptor;
  }
}
