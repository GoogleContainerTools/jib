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

import com.google.api.client.http.HttpContent;
import com.google.common.io.ByteStreams;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** A read-only {@link OutputStream} for BLOBs. */
public class BlobStream implements HttpContent {
  private final ByteArrayOutputStream byteArrayOutputStream;

  /** The length of the BLOB; -1 if not known. */
  private long length = -1;

  /** Initializes an empty BLOB. */
  public BlobStream() {
    length = 0;
    byteArrayOutputStream = new ByteArrayOutputStream(0);
  }

  /** Initializes with the contents of the input blob. */
  public BlobStream(InputStream inputStream) throws IOException {
    byteArrayOutputStream = new ByteArrayOutputStream();
    ByteStreams.copy(inputStream, byteArrayOutputStream);
  }

  /** Initializes with a string. */
  public BlobStream(String content) throws IOException {
    final byte[] contentBytes = content.getBytes();
    length = contentBytes.length;
    byteArrayOutputStream = new ByteArrayOutputStream(contentBytes.length);
    byteArrayOutputStream.write(contentBytes);
  }

  @Override
  public long getLength() throws IOException {
    return length;
  }

  @Override
  public String getType() {
    return null;
  }

  @Override
  public boolean retrySupported() {
    return true;
  }

  public void writeTo(OutputStream outputStream) throws IOException {
    byteArrayOutputStream.writeTo(outputStream);
  }
}
