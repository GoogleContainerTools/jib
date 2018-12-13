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

package com.google.cloud.tools.jib.plugins.common.logging;

import com.google.cloud.tools.jib.event.events.LogEvent.Level;
import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import org.junit.Assert;
import org.junit.Test;

/** Tests for {@link PlainConsoleLogger}. */
public class PlainConsoleLoggerTest {

  @Test
  public void testLog() {
    List<Level> levels = new ArrayList<>();
    List<String> messages = new ArrayList<>();

    ImmutableMap.Builder<Level, Consumer<String>> messageConsumers = ImmutableMap.builder();
    for (Level level : Level.values()) {
      messageConsumers.put(
          level,
          message -> {
            levels.add(level);
            messages.add(message);
          });
    }

    SingleThreadedExecutor singleThreadedExecutor = new SingleThreadedExecutor();
    PlainConsoleLogger plainConsoleLogger =
        new PlainConsoleLogger(messageConsumers.build(), singleThreadedExecutor);

    plainConsoleLogger.log(Level.LIFECYCLE, "lifecycle");
    plainConsoleLogger.log(Level.PROGRESS, "progress");
    plainConsoleLogger.log(Level.INFO, "info");
    plainConsoleLogger.log(Level.DEBUG, "debug");
    plainConsoleLogger.log(Level.WARN, "warn");
    plainConsoleLogger.log(Level.ERROR, "error");

    singleThreadedExecutor.shutDownAndAwaitTermination();

    Assert.assertEquals(
        Arrays.asList(
            Level.LIFECYCLE, Level.PROGRESS, Level.INFO, Level.DEBUG, Level.WARN, Level.ERROR),
        levels);
    Assert.assertEquals(
        Arrays.asList("lifecycle", "progress", "info", "debug", "warn", "error"), messages);
  }
}
