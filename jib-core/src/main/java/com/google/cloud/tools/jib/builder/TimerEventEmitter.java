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

  /**
   * Creates a new {@link TimerEventEmitter}.
   *
   * @param eventEmitter the {@link EventEmitter} used to emit the {@link TimerEvent}s
   * @param description the default description for the {@link TimerEvent}s
   */
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

    emitTimerEvent(State.START, Duration.ZERO, description);
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
   * Captures the time since last lap or creation and emits an {@link State#LAP} {@link TimerEvent}.
   *
   * @see #lap(String) for using a different description
   */
  public void lap() {
    emitTimerEvent(State.LAP, timer.lap(), description);
  }

  /**
   * Captures the time since last lap or creation and emits an {@link State#LAP} {@link TimerEvent}.
   *
   * @param newDescription the description to use instead of the {@link TimerEventEmitter}'s
   *     description
   */
  public void lap(String newDescription) {
    emitTimerEvent(State.LAP, timer.lap(), newDescription);
  }

  /** Laps and emits an {@link State#FINISHED} {@link TimerEvent} upon close. */
  @Override
  public void close() {
    emitTimerEvent(State.FINISHED, timer.lap(), description);
  }

  private void emitTimerEvent(State state, Duration duration, String eventDescription) {
    eventEmitter.emit(
        new TimerEvent(state, timer, duration, timer.getElapsedTime(), eventDescription));
  }
}
