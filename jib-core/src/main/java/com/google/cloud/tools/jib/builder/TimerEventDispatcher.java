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

import com.google.cloud.tools.jib.event.EventDispatcher;
import com.google.cloud.tools.jib.event.events.TimerEvent;
import com.google.cloud.tools.jib.event.events.TimerEvent.State;
import com.google.common.annotations.VisibleForTesting;
import java.io.Closeable;
import java.time.Clock;
import java.time.Duration;
import javax.annotation.Nullable;

/** Handles {@link Timer}s to dispatch {@link TimerEvent}s. */
public class TimerEventDispatcher implements Closeable {

  private static final Clock DEFAULT_CLOCK = Clock.systemUTC();

  private final EventDispatcher eventDispatcher;
  private final String description;

  private final Clock clock;
  private final Timer timer;

  /**
   * Creates a new {@link TimerEventDispatcher}.
   *
   * @param eventDispatcher the {@link EventDispatcher} used to dispatch the {@link TimerEvent}s
   * @param description the default description for the {@link TimerEvent}s
   */
  public TimerEventDispatcher(EventDispatcher eventDispatcher, String description) {
    this(eventDispatcher, description, DEFAULT_CLOCK, null);
  }

  @VisibleForTesting
  TimerEventDispatcher(
      EventDispatcher eventDispatcher,
      String description,
      Clock clock,
      @Nullable Timer parentTimer) {
    this.eventDispatcher = eventDispatcher;
    this.description = description;
    this.clock = clock;
    this.timer = new Timer(clock, parentTimer);

    dispatchTimerEvent(State.START, Duration.ZERO, description);
  }

  /**
   * Creates a new {@link TimerEventDispatcher} with its parent timer as this.
   *
   * @param description a new description
   * @return the new {@link TimerEventDispatcher}
   */
  public TimerEventDispatcher subTimer(String description) {
    return new TimerEventDispatcher(eventDispatcher, description, clock, timer);
  }

  /**
   * Captures the time since last lap or creation and dispatches an {@link State#LAP} {@link
   * TimerEvent}.
   *
   * @see #lap(String) for using a different description
   */
  public void lap() {
    dispatchTimerEvent(State.LAP, timer.lap(), description);
  }

  /**
   * Captures the time since last lap or creation and dispatches an {@link State#LAP} {@link
   * TimerEvent}.
   *
   * @param newDescription the description to use instead of the {@link TimerEventDispatcher}'s
   *     description
   */
  public void lap(String newDescription) {
    dispatchTimerEvent(State.LAP, timer.lap(), newDescription);
  }

  /** Laps and dispatches a {@link State#FINISHED} {@link TimerEvent} upon close. */
  @Override
  public void close() {
    dispatchTimerEvent(State.FINISHED, timer.lap(), description);
  }

  private void dispatchTimerEvent(State state, Duration duration, String eventDescription) {
    eventDispatcher.dispatch(
        new TimerEvent(state, timer, duration, timer.getElapsedTime(), eventDescription));
  }
}
