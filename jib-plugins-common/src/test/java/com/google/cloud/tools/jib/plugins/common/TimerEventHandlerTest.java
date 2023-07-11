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

package com.google.cloud.tools.jib.plugins.common;

import com.google.cloud.tools.jib.event.events.TimerEvent;
import com.google.cloud.tools.jib.event.events.TimerEvent.State;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import org.junit.Assert;
import org.junit.jupiter.api.Test;

/** Tests for {@link TimerEventHandler}. */
class TimerEventHandlerTest {

  private final Deque<String> logMessageQueue = new ArrayDeque<>();

  private static final TimerEvent.Timer ROOT_TIMER = Optional::empty;

  @Test
  void testAccept() {
    TimerEventHandler timerEventHandler = new TimerEventHandler(logMessageQueue::add);

    timerEventHandler.accept(
        new TimerEvent(State.START, ROOT_TIMER, Duration.ZERO, Duration.ZERO, "description"));
    timerEventHandler.accept(
        new TimerEvent(State.LAP, ROOT_TIMER, Duration.ofMillis(10), Duration.ZERO, "description"));
    timerEventHandler.accept(
        new TimerEvent(
            State.FINISHED, ROOT_TIMER, Duration.ofMillis(100), Duration.ZERO, "description"));

    timerEventHandler.accept(
        new TimerEvent(
            State.LAP,
            () -> Optional.of(ROOT_TIMER),
            Duration.ZERO,
            Duration.ZERO,
            "child description"));

    String rootStartMessage = logMessageQueue.poll();
    Assert.assertNotNull(rootStartMessage);
    Assert.assertEquals("TIMING\tdescription", rootStartMessage);

    String rootInProgressMessage = logMessageQueue.poll();
    Assert.assertNotNull(rootInProgressMessage);
    Assert.assertEquals("TIMED\tdescription : 10.0 ms", rootInProgressMessage);

    String rootFinishedMessage = logMessageQueue.poll();
    Assert.assertNotNull(rootFinishedMessage);
    Assert.assertEquals("TIMED\tdescription : 100.0 ms", rootFinishedMessage);

    String childMessage = logMessageQueue.poll();
    Assert.assertNotNull(childMessage);
    Assert.assertEquals("\tTIMED\tchild description : 0.0 ms", childMessage);

    Assert.assertTrue(logMessageQueue.isEmpty());
  }
}
