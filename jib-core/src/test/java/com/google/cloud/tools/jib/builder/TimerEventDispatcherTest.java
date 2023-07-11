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

import com.google.cloud.tools.jib.event.EventHandlers;
import com.google.cloud.tools.jib.event.events.TimerEvent;
import com.google.cloud.tools.jib.event.events.TimerEvent.State;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import org.junit.Assert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** Tests for {@link TimerEventDispatcher}. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TimerEventDispatcherTest {

  private final Deque<TimerEvent> timerEventQueue = new ArrayDeque<>();

  @Mock private Clock mockClock;

  @Test
  void testLogging() {
    EventHandlers eventHandlers =
        EventHandlers.builder().add(TimerEvent.class, timerEventQueue::add).build();

    Mockito.when(mockClock.instant()).thenReturn(Instant.EPOCH);
    try (TimerEventDispatcher parentTimerEventDispatcher =
        new TimerEventDispatcher(eventHandlers, "description", mockClock, null)) {
      Mockito.when(mockClock.instant()).thenReturn(Instant.EPOCH.plusMillis(1));
      parentTimerEventDispatcher.lap();
      Mockito.when(mockClock.instant()).thenReturn(Instant.EPOCH.plusMillis(1).plusNanos(1));
      try (TimerEventDispatcher ignored =
          parentTimerEventDispatcher.subTimer("child description")) {
        Mockito.when(mockClock.instant()).thenReturn(Instant.EPOCH.plusMillis(2));
        // Laps on close.
      }
    }

    TimerEvent timerEvent = getNextTimerEvent();
    verifyNoParent(timerEvent);
    verifyStartState(timerEvent);
    verifyDescription(timerEvent, "description");

    TimerEvent.Timer parentTimer = timerEvent.getTimer();

    timerEvent = getNextTimerEvent();
    verifyNoParent(timerEvent);
    verifyStateFirstLap(timerEvent, State.LAP);
    verifyDescription(timerEvent, "description");

    timerEvent = getNextTimerEvent();
    verifyParent(timerEvent, parentTimer);
    verifyStartState(timerEvent);
    verifyDescription(timerEvent, "child description");

    timerEvent = getNextTimerEvent();
    verifyParent(timerEvent, parentTimer);
    verifyStateFirstLap(timerEvent, State.FINISHED);
    verifyDescription(timerEvent, "child description");

    timerEvent = getNextTimerEvent();
    verifyNoParent(timerEvent);
    verifyStateNotFirstLap(timerEvent, State.FINISHED);
    verifyDescription(timerEvent, "description");

    Assert.assertTrue(timerEventQueue.isEmpty());
  }

  /**
   * Verifies that the {@code timerEvent}'s timer has no parent.
   *
   * @param timerEvent the {@link TimerEvent} to verify
   */
  private void verifyNoParent(TimerEvent timerEvent) {
    Assert.assertFalse(timerEvent.getTimer().getParent().isPresent());
  }

  /**
   * Verifies that the {@code timerEvent}'s timer has parent {@code expectedParentTimer}.
   *
   * @param timerEvent the {@link TimerEvent} to verify
   * @param expectedParentTimer the expected parent timer
   */
  private void verifyParent(TimerEvent timerEvent, TimerEvent.Timer expectedParentTimer) {
    Assert.assertTrue(timerEvent.getTimer().getParent().isPresent());
    Assert.assertSame(expectedParentTimer, timerEvent.getTimer().getParent().get());
  }

  /**
   * Verifies that the {@code timerEvent}'s state is {@link State#START}.
   *
   * @param timerEvent the {@link TimerEvent} to verify
   */
  private void verifyStartState(TimerEvent timerEvent) {
    Assert.assertEquals(State.START, timerEvent.getState());
    Assert.assertEquals(Duration.ZERO, timerEvent.getDuration());
    Assert.assertEquals(Duration.ZERO, timerEvent.getElapsed());
  }

  /**
   * Verifies that the {@code timerEvent}'s state is {@code expectedState} and that this is the
   * first lap for the timer.
   *
   * @param timerEvent the {@link TimerEvent} to verify
   * @param expectedState the expected {@link State}
   */
  private void verifyStateFirstLap(TimerEvent timerEvent, State expectedState) {
    Assert.assertEquals(expectedState, timerEvent.getState());
    Assert.assertTrue(timerEvent.getDuration().compareTo(Duration.ZERO) > 0);
    Assert.assertEquals(0, timerEvent.getElapsed().compareTo(timerEvent.getDuration()));
  }

  /**
   * Verifies that the {@code timerEvent}'s state is {@code expectedState} and that this is not the
   * first lap for the timer.
   *
   * @param timerEvent the {@link TimerEvent} to verify
   * @param expectedState the expected {@link State}
   */
  private void verifyStateNotFirstLap(TimerEvent timerEvent, State expectedState) {
    Assert.assertEquals(expectedState, timerEvent.getState());
    Assert.assertTrue(timerEvent.getDuration().compareTo(Duration.ZERO) > 0);
    Assert.assertTrue(timerEvent.getElapsed().compareTo(timerEvent.getDuration()) > 0);
  }

  /**
   * Verifies that the {@code timerEvent}'s description is {@code expectedDescription}.
   *
   * @param timerEvent the {@link TimerEvent} to verify
   * @param expectedDescription the expected description
   */
  private void verifyDescription(TimerEvent timerEvent, String expectedDescription) {
    Assert.assertEquals(expectedDescription, timerEvent.getDescription());
  }

  /**
   * Gets the next {@link TimerEvent} on the {@link #timerEventQueue}.
   *
   * @return the next {@link TimerEvent}
   */
  private TimerEvent getNextTimerEvent() {
    TimerEvent timerEvent = timerEventQueue.poll();
    Assert.assertNotNull(timerEvent);
    return timerEvent;
  }
}
