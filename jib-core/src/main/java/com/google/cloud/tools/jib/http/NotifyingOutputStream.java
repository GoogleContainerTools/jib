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
public class NotifyingOutputStream extends OutputStream {

  /** The underlying {@link OutputStream} to wrap and forward bytes to. */
  private final OutputStream underlyingOutputStream;

  /** Receives a count of bytes written since the last call. */
  private final Consumer<Long> byteCountListener;

  /** Number of bytes to provide to {@link #byteCountListener}. */
  private long byteCount = 0;

  /**
   * Wraps the {@code underlyingOutputStream} to count the bytes written.
   *
   * @param underlyingOutputStream the wrapped {@link OutputStream}
   * @param byteCountListener the byte count {@link Consumer}
   */
  public NotifyingOutputStream(
      OutputStream underlyingOutputStream, Consumer<Long> byteCountListener) {
    this.underlyingOutputStream = underlyingOutputStream;
    this.byteCountListener = byteCountListener;
  }

  @Override
  public void write(int singleByte) throws IOException {
    underlyingOutputStream.write(singleByte);
    countAndCallListener(1);
  }

  @Override
  public void write(byte[] byteArray) throws IOException {
    underlyingOutputStream.write(byteArray);
    countAndCallListener(byteArray.length);
  }

  @Override
  public void write(byte byteArray[], int offset, int length) throws IOException {
    underlyingOutputStream.write(byteArray, offset, length);
    countAndCallListener(length);
  }

  @Override
  public void flush() throws IOException {
    underlyingOutputStream.flush();
    countAndCallListener(0);
  }

  @Override
  public void close() throws IOException {
    underlyingOutputStream.close();
    countAndCallListener(0);
  }

  private void countAndCallListener(int written) {
    this.byteCount += written;
    if (byteCount == 0) {
      return;
    }

    byteCountListener.accept(byteCount);
    byteCount = 0;
  }
}
