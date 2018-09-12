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

import com.google.cloud.tools.jib.event.DefaultEventEmitter;
import com.google.cloud.tools.jib.event.EventEmitter;
import com.google.cloud.tools.jib.event.EventHandlers;
import com.google.cloud.tools.jib.event.JibEventType;
import com.google.cloud.tools.jib.event.events.TimerEvent;
import com.google.cloud.tools.jib.event.events.TimerEvent.State;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import org.junit.Assert;
import org.junit.Test;

/** Tests for {@link TimerEventEmitter}. */
public class TimerEventEmitterTest {

  private final Deque<TimerEvent> timerEventQueue = new ArrayDeque<>();

  @Test
  public void testLogging() throws InterruptedException {
    EventEmitter eventEmitter =
        new DefaultEventEmitter(new EventHandlers().add(JibEventType.TIMER, timerEventQueue::add));

    try (TimerEventEmitter parentTimerEventEmitter =
        new TimerEventEmitter(eventEmitter, "description")) {
      TimeUnit.MILLISECONDS.sleep(1);
      parentTimerEventEmitter.lap();
      try (TimerEventEmitter ignored = parentTimerEventEmitter.subTimer("child description")) {
        TimeUnit.MILLISECONDS.sleep(1);
        // Laps on close.
      }
    }

    TimerEvent.Timer parentTimer = verifyNextTimerEvent(null, State.START, "description");
    verifyNextTimerEvent(null, State.IN_PROGRESS, "description");
    verifyNextTimerEvent(parentTimer, State.START, "child description");
    verifyNextTimerEvent(parentTimer, State.FINISHED, "child description");
    verifyNextTimerEvent(null, State.FINISHED, "description");

    Assert.assertTrue(timerEventQueue.isEmpty());
  }

  /**
   * Verifies the next {@link TimerEvent} on the {@link #timerEventQueue}.
   *
   * @param expectedParentTimer the expected parent {@link TimerEvent.Timer}, or {@code null} if
   *     none expected
   * @param expectedState the expected {@link TimerEvent.State}
   * @param expectedDescription the expected description
   * @return the verified {@link TimerEvent}
   */
  private TimerEvent.Timer verifyNextTimerEvent(
      @Nullable TimerEvent.Timer expectedParentTimer,
      State expectedState,
      String expectedDescription) {
    TimerEvent timerEvent = timerEventQueue.poll();
    Assert.assertNotNull(timerEvent);

    if (expectedParentTimer == null) {
      Assert.assertFalse(timerEvent.getTimer().getParent().isPresent());
    } else {
      Assert.assertTrue(timerEvent.getTimer().getParent().isPresent());
      Assert.assertSame(expectedParentTimer, timerEvent.getTimer().getParent().get());
    }
    Assert.assertEquals(expectedState, timerEvent.getState());
    if (expectedState == State.START) {
      Assert.assertEquals(Duration.ZERO, timerEvent.getDuration());
    } else {
      Assert.assertTrue(timerEvent.getDuration().compareTo(Duration.ZERO) > 0);
    }
    Assert.assertEquals(expectedDescription, timerEvent.getDescription());

    return timerEvent.getTimer();
  }
}
