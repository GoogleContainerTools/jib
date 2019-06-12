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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link AnsiLoggerWithFooter}. */
public class AnsiLoggerWithFooterTest {

  private final SingleThreadedExecutor singleThreadedExecutor = new SingleThreadedExecutor();

  private final List<String> messages = new ArrayList<>();
  private final List<Level> levels = new ArrayList<>();
  private final Function<Level, Consumer<String>> messageConsumerFactory =
      level ->
          message -> {
            levels.add(level);
            messages.add(message);
          };

  private AnsiLoggerWithFooter testAnsiLoggerWithFooter;

  @Before
  public void setUp() {
    ImmutableMap.Builder<Level, Consumer<String>> messageConsumers = ImmutableMap.builder();
    for (Level level : Level.values()) {
      messageConsumers.put(level, messageConsumerFactory.apply(level));
    }

    testAnsiLoggerWithFooter =
        new AnsiLoggerWithFooter(messageConsumers.build(), singleThreadedExecutor);
  }

  @Test
  public void testTruncateToMaxWidth() {
    List<String> lines =
        Arrays.asList(
            "this line of text is way too long and will be truncated",
            "this line will not be truncated");
    Assert.assertEquals(
        Arrays.asList(
            "this line of text is way too long and will be t...",
            "this line will not be truncated"),
        AnsiLoggerWithFooter.truncateToMaxWidth(lines));
  }

  @Test
  public void testNoLifecycle() {
    try {
      new AnsiLoggerWithFooter(ImmutableMap.of(), singleThreadedExecutor);
      Assert.fail();

    } catch (IllegalArgumentException ex) {
      Assert.assertEquals(
          "Cannot construct AnsiLoggerFooter without LIFECYCLE message consumer", ex.getMessage());
    }
  }

  @Test
  public void testLog_noFooter() {
    testAnsiLoggerWithFooter.log(Level.LIFECYCLE, "lifecycle");
    testAnsiLoggerWithFooter.log(Level.PROGRESS, "progress");
    testAnsiLoggerWithFooter.log(Level.INFO, "info");
    testAnsiLoggerWithFooter.log(Level.DEBUG, "debug");
    testAnsiLoggerWithFooter.log(Level.WARN, "warn");
    testAnsiLoggerWithFooter.log(Level.ERROR, "error");

    singleThreadedExecutor.shutDownAndAwaitTermination();

    Assert.assertEquals(
        Arrays.asList("lifecycle", "progress", "info", "debug", "warn", "error"), messages);
    Assert.assertEquals(
        Arrays.asList(
            Level.LIFECYCLE, Level.PROGRESS, Level.INFO, Level.DEBUG, Level.WARN, Level.ERROR),
        levels);
  }

  @Test
  public void testLog_ignoreIfNoMessageConsumer() {
    AnsiLoggerWithFooter testAnsiLoggerWithFooter =
        new AnsiLoggerWithFooter(
            ImmutableMap.of(Level.LIFECYCLE, messageConsumerFactory.apply(Level.LIFECYCLE)),
            singleThreadedExecutor);

    testAnsiLoggerWithFooter.log(Level.LIFECYCLE, "lifecycle");
    testAnsiLoggerWithFooter.log(Level.PROGRESS, "progress");
    testAnsiLoggerWithFooter.log(Level.INFO, "info");
    testAnsiLoggerWithFooter.log(Level.DEBUG, "debug");
    testAnsiLoggerWithFooter.log(Level.WARN, "warn");
    testAnsiLoggerWithFooter.log(Level.ERROR, "error");

    singleThreadedExecutor.shutDownAndAwaitTermination();

    Assert.assertEquals(Collections.singletonList("lifecycle"), messages);
    Assert.assertEquals(Collections.singletonList(Level.LIFECYCLE), levels);
  }

  @Test
  public void testLog_sameFooter() {
    testAnsiLoggerWithFooter.setFooter(Collections.singletonList("footer"));
    testAnsiLoggerWithFooter.log(Level.INFO, "message");
    testAnsiLoggerWithFooter.log(Level.INFO, "another message");

    singleThreadedExecutor.shutDownAndAwaitTermination();

    Assert.assertEquals(
        Arrays.asList(
            "\033[1mfooter\033[0m",
            "\033[1A\033[0J",
            "\033[1Amessage",
            "\033[1mfooter\033[0m",
            "\033[1A\033[0J",
            "\033[1Aanother message",
            "\033[1mfooter\033[0m"),
        messages);
    Assert.assertEquals(
        Arrays.asList(
            Level.LIFECYCLE,
            Level.LIFECYCLE,
            Level.INFO,
            Level.LIFECYCLE,
            Level.LIFECYCLE,
            Level.INFO,
            Level.LIFECYCLE),
        levels);
  }

  @Test
  public void testLog_changingFooter() {
    testAnsiLoggerWithFooter.setFooter(Collections.singletonList("footer"));
    testAnsiLoggerWithFooter.log(Level.WARN, "message");
    testAnsiLoggerWithFooter.setFooter(Arrays.asList("two line", "footer"));
    testAnsiLoggerWithFooter.log(Level.WARN, "another message");

    singleThreadedExecutor.shutDownAndAwaitTermination();

    Assert.assertEquals(
        Arrays.asList(
            "\033[1mfooter\033[0m",
            "\033[1A\033[0J",
            "\033[1Amessage",
            "\033[1mfooter\033[0m",
            "\033[1A\033[0J",
            "\033[1A\033[1mtwo line\033[0m",
            "\033[1mfooter\033[0m",
            "\033[1A\033[1A\033[0J",
            "\033[1Aanother message",
            "\033[1mtwo line\033[0m",
            "\033[1mfooter\033[0m"),
        messages);
    Assert.assertEquals(
        Arrays.asList(
            Level.LIFECYCLE,
            Level.LIFECYCLE,
            Level.WARN,
            Level.LIFECYCLE,
            Level.LIFECYCLE,
            Level.LIFECYCLE,
            Level.LIFECYCLE,
            Level.LIFECYCLE,
            Level.WARN,
            Level.LIFECYCLE,
            Level.LIFECYCLE),
        levels);
  }
}
