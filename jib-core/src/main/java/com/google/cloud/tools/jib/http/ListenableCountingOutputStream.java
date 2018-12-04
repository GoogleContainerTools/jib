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
import com.google.common.base.Preconditions;
import java.io.IOException;
import java.io.OutputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.annotation.Nullable;

/**
 * Counts the number of bytes written and reports the count to a callback. The count is reported
 * with certain time delays to avoid calling the callback too often.
 */
class ListenableCountingOutputStream extends OutputStream {

  /** Partial {@link Builder} interface for setting the {@link #delayBetweenCallbacks}. */
  interface DelayBuilder {

    /**
     * Sets the delay between each call to {@link #byteCountConsumer}.
     *
     * @param delayBetweenCallbacks the delay between calls
     * @return a {@link CallbackBuilder}
     */
    CallbackBuilder every(Duration delayBetweenCallbacks);
  }

  /** Partial {@link Builder} interface for setting the {@link #byteCountConsumer}. */
  interface CallbackBuilder {

    /**
     * Sets the {@link #byteCountConsumer} that is with a count of bytes written.
     *
     * @param byteCountConsumer the byte count {@link Consumer}
     * @return a {@link ListenableCountingOutputStream}
     */
    ListenableCountingOutputStream forEachByteCount(Consumer<Long> byteCountConsumer);
  }

  /** Builds a {@link ListenableCountingOutputStream}. */
  static class Builder implements DelayBuilder, CallbackBuilder {

    private final OutputStream underlyingOutputStream;
    @Nullable private Duration delayBetweenCallbacks;

    /**
     * Instantiate with {@link #wrap}.
     *
     * @param underlyingOutputStream the underlying {@link OutputStream}
     */
    private Builder(OutputStream underlyingOutputStream) {
      this.underlyingOutputStream = underlyingOutputStream;
    }

    @Override
    public CallbackBuilder every(Duration delayBetweenCallbacks) {
      this.delayBetweenCallbacks = delayBetweenCallbacks;
      return this;
    }

    @Override
    public ListenableCountingOutputStream forEachByteCount(Consumer<Long> byteCountConsumer) {
      return new ListenableCountingOutputStream(
          underlyingOutputStream,
          byteCountConsumer,
          Preconditions.checkNotNull(delayBetweenCallbacks),
          Instant::now);
    }
  }

  /**
   * Wraps an {@link OutputStream} to count the bytes written.
   *
   * @param underlyingOutputStream the wrapped {@link OutputStream}
   * @return a {@link DelayBuilder} to set the delay between byte count reports
   */
  static DelayBuilder wrap(OutputStream underlyingOutputStream) {
    return new Builder(underlyingOutputStream);
  }

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
    wroteBytes(1);
  }

  @Override
  public void write(byte[] byteArray) throws IOException {
    underlyingOutputStream.write(byteArray);
    wroteBytes(byteArray.length);
  }

  @Override
  public void write(byte byteArray[], int offset, int length) throws IOException {
    underlyingOutputStream.write(byteArray, offset, length);
    wroteBytes(length);
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

  private void wroteBytes(int byteCount) {
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
