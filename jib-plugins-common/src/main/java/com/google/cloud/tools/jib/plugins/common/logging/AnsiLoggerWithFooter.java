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
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Logs to a console supporting ANSI escape sequences and keeps an additional footer that always
 * appears below log messages.
 */
class AnsiLoggerWithFooter implements ConsoleLogger {

  /**
   * Maximum width of a footer line. Having width too large can mess up the display when the console
   * width is too small.
   */
  private static final int MAX_FOOTER_WIDTH = 50;

  /** ANSI escape sequence template for moving the cursor up multiple lines. */
  private static final String CURSOR_UP_SEQUENCE_TEMPLATE = "\033[%dA";

  /** ANSI escape sequence for moving the cursor up. */
  private static final String CURSOR_UP_SEQUENCE = String.format(CURSOR_UP_SEQUENCE_TEMPLATE, 1);

  /** ANSI escape sequence for erasing to end of display. */
  private static final String ERASE_DISPLAY_BELOW = "\033[0J";

  /** ANSI escape sequence for setting all further characters to bold. */
  private static final String BOLD = "\033[1m";

  /** ANSI escape sequence for setting all further characters to not bold. */
  private static final String UNBOLD = "\033[0m";

  /**
   * Makes sure each line of text in {@code lines} is at most {@link #MAX_FOOTER_WIDTH} characters
   * long. If a line of text exceeds {@link #MAX_FOOTER_WIDTH} characters, the line is truncated to
   * {@link #MAX_FOOTER_WIDTH} characters with the last 3 characters as {@code ...}.
   *
   * @param lines the lines of text
   * @return the truncated lines of text
   */
  @VisibleForTesting
  static List<String> truncateToMaxWidth(List<String> lines) {
    List<String> truncatedLines = new ArrayList<>();
    for (String line : lines) {
      if (line.length() > MAX_FOOTER_WIDTH) {
        truncatedLines.add(line.substring(0, MAX_FOOTER_WIDTH - 3) + "...");
      } else {
        truncatedLines.add(line);
      }
    }
    return truncatedLines;
  }

  private final ImmutableMap<Level, Consumer<String>> messageConsumers;
  private final Consumer<String> lifecycleConsumer;
  private final SingleThreadedExecutor singleThreadedExecutor;

  private List<String> footerLines = Collections.emptyList();

  // When a footer is erased, makes the logger go up two lines (and then down one line by calling
  // "accept()" once) before printing the next message. This is useful to correct an issue in Maven:
  // https://github.com/GoogleContainerTools/jib/issues/1952
  private boolean enableTwoCursorUpJump;

  /**
   * Creates a new {@link AnsiLoggerWithFooter}.
   *
   * @param messageConsumers map from each {@link Level} to a corresponding message logger
   * @param singleThreadedExecutor a {@link SingleThreadedExecutor} to ensure that all messages are
   *     logged in a sequential, deterministic order
   * @param enableTwoCursorUpJump allows the logger to move the cursor up twice at once. Fixes a
   *     logging issue in Maven (https://github.com/GoogleContainerTools/jib/issues/1952) but causes
   *     a problem in Gradle (https://github.com/GoogleContainerTools/jib/issues/1963)
   */
  AnsiLoggerWithFooter(
      ImmutableMap<Level, Consumer<String>> messageConsumers,
      SingleThreadedExecutor singleThreadedExecutor,
      boolean enableTwoCursorUpJump) {
    Preconditions.checkArgument(
        messageConsumers.containsKey(Level.LIFECYCLE),
        "Cannot construct AnsiLoggerFooter without LIFECYCLE message consumer");
    this.messageConsumers = messageConsumers;
    lifecycleConsumer = Preconditions.checkNotNull(messageConsumers.get(Level.LIFECYCLE));
    this.singleThreadedExecutor = singleThreadedExecutor;
    this.enableTwoCursorUpJump = enableTwoCursorUpJump;
  }

  @Override
  public void log(Level logLevel, String message) {
    if (!messageConsumers.containsKey(logLevel)) {
      return;
    }
    Consumer<String> messageConsumer = messageConsumers.get(logLevel);

    singleThreadedExecutor.execute(
        () -> {
          boolean didErase = eraseFooter();
          // If a previous footer was erased, the message needs to go up a line.
          if (didErase) {
            if (enableTwoCursorUpJump) {
              messageConsumer.accept(String.format(CURSOR_UP_SEQUENCE_TEMPLATE, 2));
              messageConsumer.accept(message);
            } else {
              messageConsumer.accept(CURSOR_UP_SEQUENCE + message);
            }
          } else {
            messageConsumer.accept(message);
          }

          printInBold(footerLines);
        });
  }

  /**
   * Sets the footer asynchronously. This will replace the previously-printed footer with the new
   * {@code footerLines}.
   *
   * <p>The footer is printed in <strong>bold</strong>.
   *
   * @param newFooterLines the footer, with each line as an element (no newline at end)
   */
  @Override
  public void setFooter(List<String> newFooterLines) {
    List<String> truncatedNewFooterLines = truncateToMaxWidth(newFooterLines);

    if (truncatedNewFooterLines.equals(footerLines)) {
      return;
    }

    singleThreadedExecutor.execute(
        () -> {
          boolean didErase = eraseFooter();
          // If a previous footer was erased, the first new footer line needs to go up a line.
          if (didErase) {
            if (enableTwoCursorUpJump) {
              lifecycleConsumer.accept(String.format(CURSOR_UP_SEQUENCE_TEMPLATE, 2));
              printInBold(truncatedNewFooterLines);
            } else {
              printInBold(truncatedNewFooterLines, CURSOR_UP_SEQUENCE);
            }
          } else {
            printInBold(truncatedNewFooterLines);
          }

          footerLines = truncatedNewFooterLines;
        });
  }

  /**
   * Erases the footer. Do <em>not</em> call outside of a task submitted to {@link
   * #singleThreadedExecutor}.
   *
   * @return {@code true} if anything was erased; {@code false} otherwise
   */
  private boolean eraseFooter() {
    if (footerLines.isEmpty()) {
      return false;
    }

    StringBuilder footerEraserBuilder = new StringBuilder();

    // Moves the cursor up to the start of the footer.
    footerEraserBuilder.append(String.format(CURSOR_UP_SEQUENCE_TEMPLATE, footerLines.size()));
    // Erases everything below cursor.
    footerEraserBuilder.append(ERASE_DISPLAY_BELOW);

    lifecycleConsumer.accept(footerEraserBuilder.toString());

    return true;
  }

  private void printInBold(List<String> lines) {
    printInBold(lines, "");
  }

  private void printInBold(List<String> lines, String firstLinePrefix) {
    for (int i = 0; i < lines.size(); i++) {
      lifecycleConsumer.accept((i == 0 ? firstLinePrefix : "") + BOLD + lines.get(i) + UNBOLD);
    }
  }
}
