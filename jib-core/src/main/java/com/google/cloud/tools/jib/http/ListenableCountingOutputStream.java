/*
 * Copyright 2018 Google LLC.
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

package com.google.cloud.tools.jib.http;

import java.io.IOException;
import java.io.OutputStream;
import java.util.function.Consumer;

/** Counts the number of bytes written and reports the count to a callback. */
public class ListenableCountingOutputStream extends OutputStream {

  /** The underlying {@link OutputStream} to wrap and forward bytes to. */
  private final OutputStream underlyingOutputStream;

  /** Receives a count of bytes written since the last call. */
  private final Consumer<Long> byteCountConsumer;

  /** Number of bytes to provide to {@link #byteCountConsumer}. */
  private long byteCount = 0;

  /**
   * Wraps the {@code underlyingOutputStream} to count the bytes written.
   *
   * @param underlyingOutputStream the wrapped {@link OutputStream}
   * @param byteCountConsumer the byte count {@link Consumer}
   */
  public ListenableCountingOutputStream(
      OutputStream underlyingOutputStream, Consumer<Long> byteCountConsumer) {
    this.underlyingOutputStream = underlyingOutputStream;
    this.byteCountConsumer = byteCountConsumer;
  }

  @Override
  public void write(int singleByte) throws IOException {
    underlyingOutputStream.write(singleByte);
    addAndCallByteCountConsumer(1);
  }

  @Override
  public void write(byte[] byteArray) throws IOException {
    underlyingOutputStream.write(byteArray);
    addAndCallByteCountConsumer(byteArray.length);
  }

  @Override
  public void write(byte byteArray[], int offset, int length) throws IOException {
    underlyingOutputStream.write(byteArray, offset, length);
    addAndCallByteCountConsumer(length);
  }

  @Override
  public void flush() throws IOException {
    underlyingOutputStream.flush();
    addAndCallByteCountConsumer(0);
  }

  @Override
  public void close() throws IOException {
    underlyingOutputStream.close();
    addAndCallByteCountConsumer(0);
  }

  private void addAndCallByteCountConsumer(int written) {
    this.byteCount += written;
    if (byteCount == 0) {
      return;
    }

    byteCountConsumer.accept(byteCount);
    byteCount = 0;
  }
}
