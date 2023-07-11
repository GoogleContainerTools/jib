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
import org.junit.jupiter.api.Test;

/** Tests for {@link AnsiLoggerWithFooter}. */
class AnsiLoggerWithFooterTest {

  private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(3);

  private final SingleThreadedExecutor singleThreadedExecutor = new SingleThreadedExecutor();

  private final List<String> messages = new ArrayList<>();
  private final List<Level> levels = new ArrayList<>();

  @Test
  void testTruncateToMaxWidth() {
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
  void testNoLifecycle() {
    try {
      new AnsiLoggerWithFooter(ImmutableMap.of(), singleThreadedExecutor, false);
      Assert.fail();

    } catch (IllegalArgumentException ex) {
      Assert.assertEquals(
          "Cannot construct AnsiLoggerFooter without LIFECYCLE message consumer", ex.getMessage());
    }
  }

  @Test
  void testLog_noFooter() {
    AnsiLoggerWithFooter testAnsiLoggerWithFooter = createTestLogger(false);
    testAnsiLoggerWithFooter.log(Level.LIFECYCLE, "lifecycle");
    testAnsiLoggerWithFooter.log(Level.PROGRESS, "progress");
    testAnsiLoggerWithFooter.log(Level.INFO, "info");
    testAnsiLoggerWithFooter.log(Level.DEBUG, "debug");
    testAnsiLoggerWithFooter.log(Level.WARN, "warn");
    testAnsiLoggerWithFooter.log(Level.ERROR, "error");

    singleThreadedExecutor.shutDownAndAwaitTermination(SHUTDOWN_TIMEOUT);

    Assert.assertEquals(
        Arrays.asList("lifecycle", "progress", "info", "debug", "warn", "error"), messages);
    Assert.assertEquals(
        Arrays.asList(
            Level.LIFECYCLE, Level.PROGRESS, Level.INFO, Level.DEBUG, Level.WARN, Level.ERROR),
        levels);
  }

  @Test
  void testLog_ignoreIfNoMessageConsumer() {
    AnsiLoggerWithFooter testAnsiLoggerWithFooter =
        new AnsiLoggerWithFooter(
            ImmutableMap.of(Level.LIFECYCLE, createMessageConsumer(Level.LIFECYCLE)),
            singleThreadedExecutor,
            false);

    testAnsiLoggerWithFooter.log(Level.LIFECYCLE, "lifecycle");
    testAnsiLoggerWithFooter.log(Level.PROGRESS, "progress");
    testAnsiLoggerWithFooter.log(Level.INFO, "info");
    testAnsiLoggerWithFooter.log(Level.DEBUG, "debug");
    testAnsiLoggerWithFooter.log(Level.WARN, "warn");
    testAnsiLoggerWithFooter.log(Level.ERROR, "error");

    singleThreadedExecutor.shutDownAndAwaitTermination(SHUTDOWN_TIMEOUT);

    Assert.assertEquals(Collections.singletonList("lifecycle"), messages);
    Assert.assertEquals(Collections.singletonList(Level.LIFECYCLE), levels);
  }

  @Test
  void testLog_sameFooter() {
    AnsiLoggerWithFooter testAnsiLoggerWithFooter = createTestLogger(false);
    testAnsiLoggerWithFooter.setFooter(Collections.singletonList("footer"));
    testAnsiLoggerWithFooter.log(Level.INFO, "message");
    testAnsiLoggerWithFooter.log(Level.INFO, "another message");

    singleThreadedExecutor.shutDownAndAwaitTermination(SHUTDOWN_TIMEOUT);

    Assert.assertEquals(
        Arrays.asList(
            "\033[1mfooter\033[0m", // single-line footer in bold

            // now triggered by logging a message
            "\033[1A\033[0J", // cursor up and erase to the end
            "\033[1Amessage", // cursor up + message
            "\033[1mfooter\033[0m", // footer

            // by logging another message
            "\033[1A\033[0J", // cursor up and erase
            "\033[1Aanother message", // cursor up + message
            "\033[1mfooter\033[0m"), // footer
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
  void testLog_sameFooterWithEnableTwoCursorUpJump() {
    AnsiLoggerWithFooter testAnsiLoggerWithFooter = createTestLogger(true);
    testAnsiLoggerWithFooter.setFooter(Collections.singletonList("footer"));
    testAnsiLoggerWithFooter.log(Level.INFO, "message");
    testAnsiLoggerWithFooter.log(Level.INFO, "another message");

    singleThreadedExecutor.shutDownAndAwaitTermination(SHUTDOWN_TIMEOUT);

    Assert.assertEquals(
        Arrays.asList(
            "\033[1mfooter\033[0m", // single-line footer in bold

            // now triggered by logging a message
            "\033[1A\033[0J", // cursor up and erase to the end
            "\033[2A", // up two lines
            "message",
            "\033[1mfooter\033[0m", // footer

            // by logging another message
            "\033[1A\033[0J", // cursor up and erase
            "\033[2A", // up two
            "another message",
            "\033[1mfooter\033[0m"), // footer
        messages);
    Assert.assertEquals(
        Arrays.asList(
            Level.LIFECYCLE,
            Level.LIFECYCLE,
            Level.INFO,
            Level.INFO,
            Level.LIFECYCLE,
            Level.LIFECYCLE,
            Level.INFO,
            Level.INFO,
            Level.LIFECYCLE),
        levels);
  }

  @Test
  void testLog_changingFooter() {
    AnsiLoggerWithFooter testAnsiLoggerWithFooter = createTestLogger(false);
    testAnsiLoggerWithFooter.setFooter(Collections.singletonList("footer"));
    testAnsiLoggerWithFooter.log(Level.WARN, "message");
    testAnsiLoggerWithFooter.setFooter(Arrays.asList("two line", "footer"));
    testAnsiLoggerWithFooter.log(Level.WARN, "another message");

    singleThreadedExecutor.shutDownAndAwaitTermination(SHUTDOWN_TIMEOUT);

    Assert.assertEquals(
        Arrays.asList(
            "\033[1mfooter\033[0m", // single-line footer in bold

            // now triggered by logging a warning
            "\033[1A\033[0J", // cursor up and erase to the end
            "\033[1Amessage", // cursor up + message
            "\033[1mfooter\033[0m", // footer

            // by setting a two-line footer
            "\033[1A\033[0J", // cursor up and erase
            "\033[1A\033[1mtwo line\033[0m", // cursor up + footer line 1
            "\033[1mfooter\033[0m", // footer line 2

            // by logging another warning
            "\033[2A\033[0J", // cursor up twice (to erase two-line footer) and erase
            "\033[1Aanother message", // cursor up + message
            "\033[1mtwo line\033[0m", // footer line 1
            "\033[1mfooter\033[0m"), // footer line 2
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

  @Test
  void testLog_changingFooterWithEnableTwoCursorUpJump() {
    AnsiLoggerWithFooter testAnsiLoggerWithFooter = createTestLogger(true);
    testAnsiLoggerWithFooter.setFooter(Collections.singletonList("footer"));
    testAnsiLoggerWithFooter.log(Level.WARN, "message");
    testAnsiLoggerWithFooter.setFooter(Arrays.asList("two line", "footer"));
    testAnsiLoggerWithFooter.log(Level.WARN, "another message");

    singleThreadedExecutor.shutDownAndAwaitTermination(SHUTDOWN_TIMEOUT);

    Assert.assertEquals(
        Arrays.asList(
            "\033[1mfooter\033[0m", // single-line footer in bold

            // now triggered by logging a warning
            "\033[1A\033[0J", // cursor up and erase to the end
            "\033[2A", // up two lines
            "message",
            "\033[1mfooter\033[0m", // footer

            // by setting a two-line footer
            "\033[1A\033[0J", // cursor up and erase
            "\033[2A", // up two lines
            "\033[1mtwo line\033[0m", // footer line 1
            "\033[1mfooter\033[0m", // footer line 2

            // by logging another warning
            "\033[2A\033[0J", // cursor up twice (to erase two-line footer) and erase
            "\033[2A", // up two lines
            "another message",
            "\033[1mtwo line\033[0m", // footer line 1
            "\033[1mfooter\033[0m"), // footer line 2
        messages);
    Assert.assertEquals(
        Arrays.asList(
            Level.LIFECYCLE,
            Level.LIFECYCLE,
            Level.WARN,
            Level.WARN,
            Level.LIFECYCLE,
            Level.LIFECYCLE,
            Level.LIFECYCLE,
            Level.LIFECYCLE,
            Level.LIFECYCLE,
            Level.LIFECYCLE,
            Level.WARN,
            Level.WARN,
            Level.LIFECYCLE,
            Level.LIFECYCLE),
        levels);
  }

  private AnsiLoggerWithFooter createTestLogger(boolean enableTwoCursorUpJump) {
    ImmutableMap.Builder<Level, Consumer<String>> messageConsumers = ImmutableMap.builder();
    for (Level level : Level.values()) {
      messageConsumers.put(level, createMessageConsumer(level));
    }

    return new AnsiLoggerWithFooter(
        messageConsumers.build(), singleThreadedExecutor, enableTwoCursorUpJump);
  }

  private Consumer<String> createMessageConsumer(Level level) {
    return message -> {
      levels.add(level);
      messages.add(message);
    };
  }
}
