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

import com.google.cloud.tools.jib.api.LogEvent.Level;
import com.google.common.collect.ImmutableMap;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import org.junit.Assert;
import org.junit.Test;

/** Tests for {@link PlainConsoleLogger}. */
public class PlainConsoleLoggerTest {

  private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(3);

  private final SingleThreadedExecutor singleThreadedExecutor = new SingleThreadedExecutor();

  private final List<Level> levels = new ArrayList<>();
  private final List<String> messages = new ArrayList<>();

  private PlainConsoleLogger testPlainConsoleLogger;

  @Test
  public void testLog() {
    ImmutableMap.Builder<Level, Consumer<String>> messageConsumers = ImmutableMap.builder();
    for (Level level : Level.values()) {
      messageConsumers.put(level, createMessageConsumer(level));
    }

    testPlainConsoleLogger =
        new PlainConsoleLogger(messageConsumers.build(), singleThreadedExecutor);

    testPlainConsoleLogger.log(Level.LIFECYCLE, "lifecycle");
    testPlainConsoleLogger.log(Level.PROGRESS, "progress");
    testPlainConsoleLogger.log(Level.INFO, "info");
    testPlainConsoleLogger.log(Level.DEBUG, "debug");
    testPlainConsoleLogger.log(Level.WARN, "warn");
    testPlainConsoleLogger.log(Level.ERROR, "error");

    singleThreadedExecutor.shutDownAndAwaitTermination(SHUTDOWN_TIMEOUT);

    Assert.assertEquals(
        Arrays.asList(
            Level.LIFECYCLE, Level.PROGRESS, Level.INFO, Level.DEBUG, Level.WARN, Level.ERROR),
        levels);
    Assert.assertEquals(
        Arrays.asList("lifecycle", "progress", "info", "debug", "warn", "error"), messages);
  }

  @Test
  public void testLog_filterOutColors() {
    ImmutableMap.Builder<Level, Consumer<String>> messageConsumers = ImmutableMap.builder();
    for (Level level : Level.values()) {
      messageConsumers.put(level, createMessageConsumer(level));
    }

    testPlainConsoleLogger =
            new PlainConsoleLogger(messageConsumers.build(), singleThreadedExecutor);

    testPlainConsoleLogger.log(Level.LIFECYCLE, "\u001B[36;1mlifecycle\u001B[0m");
    testPlainConsoleLogger.log(Level.PROGRESS, "\u001B[33mprogress\u001B[0m");
    testPlainConsoleLogger.log(Level.ERROR, "\u001B[31;1merror\u001B[0m");

    singleThreadedExecutor.shutDownAndAwaitTermination(SHUTDOWN_TIMEOUT);

    Assert.assertEquals(
            Arrays.asList(
                    Level.LIFECYCLE, Level.PROGRESS, Level.ERROR),
            levels);
    Assert.assertEquals(
            Arrays.asList("lifecycle", "progress", "error"), messages);
  }

  @Test
  public void testLog_ignoreIfNoMessageConsumer() {
    testPlainConsoleLogger =
        new PlainConsoleLogger(
            ImmutableMap.of(Level.WARN, createMessageConsumer(Level.WARN)), singleThreadedExecutor);

    testPlainConsoleLogger.log(Level.LIFECYCLE, "lifecycle");
    testPlainConsoleLogger.log(Level.PROGRESS, "progress");
    testPlainConsoleLogger.log(Level.INFO, "info");
    testPlainConsoleLogger.log(Level.DEBUG, "debug");
    testPlainConsoleLogger.log(Level.WARN, "warn");
    testPlainConsoleLogger.log(Level.ERROR, "error");

    singleThreadedExecutor.shutDownAndAwaitTermination(SHUTDOWN_TIMEOUT);

    Assert.assertEquals(Collections.singletonList(Level.WARN), levels);
    Assert.assertEquals(Collections.singletonList("warn"), messages);
  }

  private Consumer<String> createMessageConsumer(Level level) {
    return message -> {
      levels.add(level);
      messages.add(message);
    };
  }
}
