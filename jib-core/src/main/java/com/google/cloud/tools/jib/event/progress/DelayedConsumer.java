/*
 * Copyright 2019 Google LLC.
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

package com.google.cloud.tools.jib.event.progress;

import java.time.Duration;
import java.time.Instant;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Wraps a {@link Consumer} so that multiple consume calls within a short time are blocked and
 * delayed into a single call.
 */
public class DelayedConsumer<T> implements Consumer<T> {

  private final Consumer<T> originalConsumer;

  /** Delay between each call to {@link #byteCountConsumer}. */
  private final Duration delayBetweenCallbacks;

  /**
   * Binary operator that will "add up" multiple delayed values. Used to accumulate past values in
   * case delays happen so that callback is called once with the "added" value after the delay.
   */
  private final BinaryOperator<T> adder;

  /** Returns the current {@link Instant}. */
  private final Supplier<Instant> getNow;

  /** Last time {@link #byteCountConsumer} was called. */
  private Instant previousCallback;

  private Consumer<T> consumer;

  /** Wraps a consumer with the delay of 100 ms. */
  public DelayedConsumer(Consumer<T> callback, BinaryOperator<T> adder) {
    this(callback, adder, Duration.ofMillis(100), Instant::now);
  }

  public DelayedConsumer(
      Consumer<T> consumer,
      BinaryOperator<T> adder,
      Duration delayBetweenCallbacks,
      Supplier<Instant> getNow) {
    this.originalConsumer = consumer;
    this.consumer = consumer;
    this.adder = adder;
    this.delayBetweenCallbacks = delayBetweenCallbacks;
    this.getNow = getNow;

    previousCallback = getNow.get();
  }

  @Override
  public void accept(T value) {
    Instant now = getNow.get();
    if (previousCallback.plus(delayBetweenCallbacks).isBefore(now)) {
      previousCallback = now;
      consumer.accept(value);
      consumer = originalConsumer;
    } else {
      Consumer<T> currentConsumer = consumer;
      consumer = nextValue -> currentConsumer.accept(adder.apply(nextValue, value));
    }
  }
}
