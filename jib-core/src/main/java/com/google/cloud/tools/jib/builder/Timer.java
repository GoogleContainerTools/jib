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

package com.google.cloud.tools.jib.builder;

import java.io.Closeable;
import java.util.function.Consumer;

/** Times code execution intervals. Call {@link #lap} at the end of each interval. */
// TODO: Replace com.google.cloud.tools.jib.Timer with this.
public class Timer implements Closeable {

  /** Consumes time in nanoseconds. */
  private final Consumer<Long> timeConsumer;

  private long startTime = System.nanoTime();

  /**
   * Instantiate with a consumer for timed laps.
   *
   * @param timeConsumer consumes time in nanoseconds
   */
  Timer(Consumer<Long> timeConsumer) {
    this.timeConsumer = timeConsumer;
  }

  /** Consumes the time since last lap or creation. */
  public void lap() {
    long time = System.nanoTime() - startTime;
    timeConsumer.accept(time);
    startTime = System.nanoTime();
  }

  @Override
  public void close() {
    lap();
  }
}
