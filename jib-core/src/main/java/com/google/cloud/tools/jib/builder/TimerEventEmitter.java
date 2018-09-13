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

import com.google.cloud.tools.jib.event.EventEmitter;
import com.google.cloud.tools.jib.event.events.TimerEvent;
import com.google.cloud.tools.jib.event.events.TimerEvent.State;
import com.google.common.annotations.VisibleForTesting;
import java.io.Closeable;
import java.time.Clock;
import java.time.Duration;
import javax.annotation.Nullable;

/** Handles {@link Timer}s to emit {@link TimerEvent}s. */
public class TimerEventEmitter implements Closeable {

  private static final Clock DEFAULT_CLOCK = Clock.systemUTC();

  private final EventEmitter eventEmitter;
  private final String description;

  private final Clock clock;
  private final Timer timer;

  public TimerEventEmitter(EventEmitter eventEmitter, String description) {
    this(eventEmitter, description, DEFAULT_CLOCK, null);
  }

  @VisibleForTesting
  TimerEventEmitter(
      EventEmitter eventEmitter, String description, Clock clock, @Nullable Timer parentTimer) {
    this.eventEmitter = eventEmitter;
    this.description = description;
    this.clock = clock;
    this.timer = new Timer(clock, parentTimer);

    emitTimerEvent(State.START, Duration.ZERO);
  }

  /**
   * Creates a new {@link TimerEventEmitter} with its parent timer as this.
   *
   * @param description a new description
   * @return the new {@link TimerEventEmitter}
   */
  public TimerEventEmitter subTimer(String description) {
    return new TimerEventEmitter(eventEmitter, description, clock, timer);
  }

  /**
   * Captures the time since last lap or creation and emits an {@link State#IN_PROGRESS} {@link
   * TimerEvent}.
   */
  public void lap() {
    emitTimerEvent(State.IN_PROGRESS, timer.lap());
  }

  /** Laps and emits an {@link State#FINISHED} {@link TimerEvent} upon close. */
  @Override
  public void close() {
    emitTimerEvent(State.FINISHED, timer.lap());
  }

  private void emitTimerEvent(State state, Duration duration) {
    eventEmitter.emit(new TimerEvent(state, timer, duration, timer.getElapsedTime(), description));
  }
}
