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

import com.google.cloud.tools.jib.event.events.TimerEvent;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import javax.annotation.Nullable;

/** Times code execution intervals. Call {@link #lap} at the end of each interval. */
class Timer implements TimerEvent.Timer {

  private final Clock clock;
  @Nullable private final Timer parentTimer;

  private final Instant startTime;
  private Instant lapStartTime;

  Timer(Clock clock, @Nullable Timer parentTimer) {
    this.clock = clock;
    this.parentTimer = parentTimer;

    startTime = clock.instant();
    lapStartTime = startTime;
  }

  @Override
  public Optional<TimerEvent.Timer> getParent() {
    return Optional.ofNullable(parentTimer);
  }

  /**
   * Captures the time since last lap or creation, and resets the start time.
   *
   * @return the duration of the last lap, or since creation
   */
  Duration lap() {
    Instant now = clock.instant();
    Duration duration = Duration.between(lapStartTime, now);
    lapStartTime = now;
    return duration;
  }

  /**
   * Gets the total elapsed time since creation.
   *
   * @return the total elapsed time
   */
  Duration getElapsedTime() {
    return Duration.between(startTime, clock.instant());
  }
}
