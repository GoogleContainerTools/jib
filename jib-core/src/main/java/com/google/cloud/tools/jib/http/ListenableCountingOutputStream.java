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

import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.io.OutputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Counts the number of bytes written and reports the count to a callback. The count is reported
 * with certain time delays to avoid calling the callback too often.
 */
class ListenableCountingOutputStream extends OutputStream {

  /** The underlying {@link OutputStream} to wrap and forward bytes to. */
  private final OutputStream underlyingOutputStream;

  /** Receives a count of bytes written since the last call. */
  private final Consumer<Long> byteCountConsumer;

  /** Delay between each call to {@link #byteCountConsumer}. */
  private final Duration delayBetweenCallbacks;

  /** Supplies the current {@link Instant}. */
  private final Supplier<Instant> instantSupplier;

  /** Number of bytes to provide to {@link #byteCountConsumer}. */
  private long byteCount = 0;

  /** Last time {@link #byteCountConsumer} was called. */
  private Instant previousCallback;

  /**
   * Wraps the {@code underlyingOutputStream} to count the bytes written.
   *
   * @param underlyingOutputStream the wrapped {@link OutputStream}
   * @param byteCountConsumer the byte count {@link Consumer}
   * @param delayBetweenCallbacks the minimum delay between each call to {@link #byteCountConsumer}
   */
  ListenableCountingOutputStream(
      OutputStream underlyingOutputStream,
      Consumer<Long> byteCountConsumer,
      Duration delayBetweenCallbacks) {
    this(underlyingOutputStream, byteCountConsumer, delayBetweenCallbacks, Instant::now);
  }

  @VisibleForTesting
  ListenableCountingOutputStream(
      OutputStream underlyingOutputStream,
      Consumer<Long> byteCountConsumer,
      Duration delayBetweenCallbacks,
      Supplier<Instant> instantSupplier) {
    this.underlyingOutputStream = underlyingOutputStream;
    this.byteCountConsumer = byteCountConsumer;
    this.delayBetweenCallbacks = delayBetweenCallbacks;
    this.instantSupplier = instantSupplier;

    previousCallback = instantSupplier.get();
  }

  @Override
  public void write(int singleByte) throws IOException {
    underlyingOutputStream.write(singleByte);
    countBytesWritten(1);
  }

  @Override
  public void write(byte[] byteArray) throws IOException {
    underlyingOutputStream.write(byteArray);
    countBytesWritten(byteArray.length);
  }

  @Override
  public void write(byte byteArray[], int offset, int length) throws IOException {
    underlyingOutputStream.write(byteArray, offset, length);
    countBytesWritten(length);
  }

  @Override
  public void flush() throws IOException {
    underlyingOutputStream.flush();
    callByteCountConsumer(instantSupplier.get());
  }

  @Override
  public void close() throws IOException {
    underlyingOutputStream.close();
    callByteCountConsumer(instantSupplier.get());
  }

  private void countBytesWritten(int byteCount) {
    this.byteCount += byteCount;

    Instant now = instantSupplier.get();
    if (previousCallback.plus(delayBetweenCallbacks).isBefore(now)) {
      callByteCountConsumer(now);
    }
  }

  private void callByteCountConsumer(Instant now) {
    if (byteCount == 0) {
      return;
    }

    byteCountConsumer.accept(byteCount);
    byteCount = 0;
    previousCallback = now;
  }
}
