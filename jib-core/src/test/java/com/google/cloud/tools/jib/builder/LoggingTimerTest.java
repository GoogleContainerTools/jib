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

import java.util.ArrayDeque;
import java.util.Deque;
import org.junit.Assert;
import org.junit.Test;

/** Tests for {@link LoggingTimer}. */
public class LoggingTimerTest {

  private final Deque<String> logMessageQueue = new ArrayDeque<>();

  @Test
  public void testLogging() {
    try (Timer ignored = LoggingTimer.newTimer(logMessageQueue::offer, "label")) {
      // Laps on close.
    }

    String startLogMessage = logMessageQueue.poll();
    Assert.assertNotNull(startLogMessage);
    Assert.assertTrue(startLogMessage.matches("RUNNING\tlabel"));

    String lapLogMessage = logMessageQueue.poll();
    Assert.assertNotNull(lapLogMessage);
    Assert.assertTrue(lapLogMessage.matches("TIMED\tlabel : [0-9]+\\.[0-9]+ ms"));

    Assert.assertTrue(logMessageQueue.isEmpty());
  }
}
