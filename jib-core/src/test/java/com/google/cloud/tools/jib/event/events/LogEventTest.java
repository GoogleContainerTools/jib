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

import com.google.cloud.tools.jib.event.EventHandlers;
import com.google.cloud.tools.jib.event.JibEventType;
import com.google.cloud.tools.jib.event.events.LogEvent.Level;
import java.util.ArrayDeque;
import java.util.Deque;
import org.junit.Assert;
import org.junit.Test;

/** Tests for {@link LogEvent}. */
public class LogEventTest {

  private final Deque<LogEvent> receivedLogEvents = new ArrayDeque<>();

  // Note that in actual code, the event handler should NOT perform thread unsafe operations like
  // here.
  private final EventHandlers eventHandlers =
      EventHandlers.builder().add(JibEventType.LOGGING, receivedLogEvents::offer).build();

  @Test
  public void testFactories() {
    eventHandlers.dispatch(LogEvent.error("error"));
    eventHandlers.dispatch(LogEvent.lifecycle("lifecycle"));
    eventHandlers.dispatch(LogEvent.progress("progress"));
    eventHandlers.dispatch(LogEvent.warn("warn"));
    eventHandlers.dispatch(LogEvent.info("info"));
    eventHandlers.dispatch(LogEvent.debug("debug"));

    verifyNextLogEvent(Level.ERROR, "error");
    verifyNextLogEvent(Level.LIFECYCLE, "lifecycle");
    verifyNextLogEvent(Level.PROGRESS, "progress");
    verifyNextLogEvent(Level.WARN, "warn");
    verifyNextLogEvent(Level.INFO, "info");
    verifyNextLogEvent(Level.DEBUG, "debug");
    Assert.assertTrue(receivedLogEvents.isEmpty());
  }

  private void verifyNextLogEvent(Level level, String message) {
    Assert.assertFalse(receivedLogEvents.isEmpty());

    LogEvent logEvent = receivedLogEvents.poll();

    Assert.assertEquals(level, logEvent.getLevel());
    Assert.assertEquals(message, logEvent.getMessage());
  }
}
