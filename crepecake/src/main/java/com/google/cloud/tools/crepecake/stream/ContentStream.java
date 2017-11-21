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

package com.google.cloud.tools.crepecake.stream;

import com.google.common.io.ByteStreams;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** A read-only {@link OutputStream} for BLOBs. */
public class ContentStream {
  private final ByteArrayOutputStream byteArrayOutputStream;

  /** Initializes an empty BLOB. */
  public ContentStream() {
    byteArrayOutputStream = new ByteArrayOutputStream(0);
  }

  /** Initializes with the contents of the input stream. */
  public ContentStream(InputStream inputStream) throws IOException {
    byteArrayOutputStream = new ByteArrayOutputStream(0);
    ByteStreams.copy(inputStream, byteArrayOutputStream);
  }

  /** Initializes with a string. */
  public ContentStream(String content) throws IOException {
    final byte[] contentBytes = content.getBytes();
    byteArrayOutputStream = new ByteArrayOutputStream(contentBytes.length);
    byteArrayOutputStream.write(contentBytes);
  }

  public void writeTo(OutputStream outputStream) throws IOException {
    byteArrayOutputStream.writeTo(outputStream);
  }
}
