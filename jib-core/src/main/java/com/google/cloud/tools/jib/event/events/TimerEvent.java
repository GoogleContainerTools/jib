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

package com.google.cloud.tools.jib.event.events;

import com.google.cloud.tools.jib.api.JibEvent;
import java.time.Duration;
import java.util.Optional;

/**
 * Timer event for timing various part of Jib's execution.
 *
 * <p>Timer events follow a specific {@link Timer} through a {@link State#START}, {@link State#LAP},
 * and {@link State#FINISHED} states. The duration indicates the duration since the last {@link
 * TimerEvent} dispatched for the {@link Timer}.
 *
 * <p>Timers can also define a hierarchy.
 */
public class TimerEvent implements JibEvent {

  /** The state of the timing. */
  public enum State {

    /** The timer has started timing. {@link #getDuration} is 0. {@link #getElapsed} is 0. */
    START,

    /**
     * The timer timed a lap. {@link #getDuration} is the time since the last event. {@link
     * #getElapsed} is the total elapsed time.
     */
    LAP,

    /**
     * The timer has finished timing. {@link #getDuration} is the time since the last event. {@link
     * #getElapsed} is the total elapsed time.
     */
    FINISHED
  }

  /** Defines a timer hierarchy. */
  public interface Timer {

    /**
     * Gets the parent of this {@link Timer}.
     *
     * @return the parent of this {@link Timer}
     */
    Optional<Timer> getParent();
  }

  private final State state;
  private final Timer timer;
  private final Duration duration;
  private final Duration elapsed;
  private final String description;

  /**
   * Creates a new {@link TimerEvent}. For internal use only.
   *
   * @param state the state of the {@link Timer}
   * @param timer the {@link Timer}
   * @param duration the lap duration
   * @param elapsed the total elapsed time since the timer was created
   * @param description the description of this event
   */
  public TimerEvent(
      State state, Timer timer, Duration duration, Duration elapsed, String description) {
    this.state = state;
    this.timer = timer;
    this.duration = duration;
    this.elapsed = elapsed;
    this.description = description;
  }

  /**
   * Gets the state of the timer.
   *
   * @return the state of the timer
   * @see State
   */
  public State getState() {
    return state;
  }

  /**
   * Gets the timer this event is for.
   *
   * @return the timer
   */
  public Timer getTimer() {
    return timer;
  }

  /**
   * Gets the duration since the last {@link TimerEvent} for this timer.
   *
   * @return the duration since the last {@link TimerEvent} for this timer.
   */
  public Duration getDuration() {
    return duration;
  }

  /**
   * Gets the total elapsed duration since this timer was created.
   *
   * @return the duration since this timer was created
   */
  public Duration getElapsed() {
    return elapsed;
  }

  /**
   * Gets the description associated with this event.
   *
   * @return the description
   */
  public String getDescription() {
    return description;
  }
}
