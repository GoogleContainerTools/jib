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

import com.google.cloud.tools.jib.event.JibEvent;
import java.time.Duration;
import java.util.Optional;

/**
 * Timer event for timing various part of Jib's execution.
 *
 * <p>Timer events follow a specific {@link Timer} through a {@link State#START}, {@link
 * State#IN_PROGRESS}, and {@link State#FINISHED} states. The duration indicates the duration since
 * the last {@link TimerEvent} emitted for the {@link Timer}.
 *
 * <p>Timers can also define a hierarchy.
 */
public class TimerEvent implements JibEvent {

  /** The state of the timing. */
  public enum State {
    START,
    IN_PROGRESS,
    FINISHED
  }

  /** Defines a timer hierarchy. */
  public interface Timer {

    /**
     * Gets the parent of this {@link Timer}.
     *
     * @return the parent of this {@link Timer}
     */
    Optional<? extends Timer> getParent();
  }

  private final State state;
  private final Timer timer;
  private final Duration duration;
  private final String description;

  /**
   * Creates a new {@link TimerEvent}. For internal use only.
   *
   * @param state the state of the {@link Timer}
   * @param timer the {@link Timer}
   * @param duration the lap duration
   * @param description the description of this event
   */
  public TimerEvent(State state, Timer timer, Duration duration, String description) {
    this.state = state;
    this.timer = timer;
    this.duration = duration;
    this.description = description;
  }

  public State getState() {
    return state;
  }

  public Timer getTimer() {
    return timer;
  }

  public Duration getDuration() {
    return duration;
  }

  public String getDescription() {
    return description;
  }
}
