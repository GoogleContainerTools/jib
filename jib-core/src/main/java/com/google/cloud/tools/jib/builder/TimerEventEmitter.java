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
import java.io.Closeable;
import java.time.Duration;

/** Handles {@link Timer}s to emit {@link TimerEvent}s. */
public class TimerEventEmitter implements Closeable {

  private final EventEmitter eventEmitter;
  private final String description;

  private final Timer timer;

  public TimerEventEmitter(EventEmitter eventEmitter, String description) {
    this(eventEmitter, description, new Timer());
  }

  private TimerEventEmitter(EventEmitter eventEmitter, String description, Timer timer) {
    this.eventEmitter = eventEmitter;
    this.description = description;
    this.timer = timer;

    emitTimerEvent(State.START, Duration.ZERO);
  }

  public TimerEventEmitter subTimer(String description) {
    return new TimerEventEmitter(eventEmitter, description, new Timer(timer));
  }

  public void lap() {
    emitTimerEvent(State.IN_PROGRESS, timer.lap());
  }

  @Override
  public void close() {
    emitTimerEvent(State.FINISHED, timer.lap());
  }

  private void emitTimerEvent(State state, Duration duration) {
    eventEmitter.emit(new TimerEvent(state, timer, duration, description));
  }
}
