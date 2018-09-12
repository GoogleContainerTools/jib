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
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import javax.annotation.Nullable;

/** Times code execution intervals. Call {@link #lap} at the end of each interval. */
// TODO: Replace com.google.cloud.tools.jib.Timer with this.
class Timer implements TimerEvent.Timer {

  @Nullable private final Timer parentTimer;

  private Instant startTime = Instant.now();

  Timer() {
    this(null);
  }

  Timer(@Nullable Timer parentTimer) {
    this.parentTimer = parentTimer;
  }

  @Override
  public Optional<? extends Timer> getParent() {
    return Optional.ofNullable(parentTimer);
  }

  /**
   * Captures the time since last lap or creation, and resets the start time.
   *
   * @return the duration of the last lap, or since creation
   */
  Duration lap() {
    Instant now = Instant.now();
    Duration duration = Duration.between(startTime, now);
    startTime = now;
    return duration;
  }
}
