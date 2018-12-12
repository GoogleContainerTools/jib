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

import com.google.common.util.concurrent.Futures;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Keeps all log messages in a sequential, deterministic order along with an additional footer that
 * always appears below log messages. This is intended to log both the messages and the footer to
 * the same console.
 *
 * <p>Make sure to call {@link #shutDown} when finished.
 */
class AnsiLoggerWithFooter {

  /** ANSI escape sequence for moving the cursor up one line. */
  private static final String CURSOR_UP_SEQUENCE = "\033[1A";

  /** ANSI escape sequence for erasing to end of display. */
  private static final String ERASE_DISPLAY_BELOW = "\033[0J";

  /** ANSI escape sequence for setting all further characters to bold. */
  private static final String BOLD = "\033[1m";

  /** ANSI escape sequence for setting all further characters to not bold. */
  private static final String UNBOLD = "\033[0m";

  private static final Duration EXECUTOR_SHUTDOWN_WAIT = Duration.ofSeconds(1);

  private final ExecutorService executorService = Executors.newSingleThreadExecutor();

  private final Consumer<String> plainLogger;

  private List<String> footerLines = Collections.emptyList();

  AnsiLoggerWithFooter(Consumer<String> plainLogger) {
    this.plainLogger = plainLogger;
  }

  /** Shuts down the {@link #executorService}. */
  public void shutDown() {
    executorService.shutdown();
  }

  /** Waits for the {@link #executorService} to terminate. */
  public void awaitTermination() {
    try {
      if (!executorService.awaitTermination(
          EXECUTOR_SHUTDOWN_WAIT.getSeconds(), TimeUnit.SECONDS)) {
        executorService.shutdownNow();
      }
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
    }
  }

  /**
   * Runs {@code messageLogger} asynchronously.
   *
   * @param messageLogger the {@link Runnable} intended to synchronously log a message to the
   *     console
   * @return a {@link Future} to track completion
   */
  public Future<Void> log(Runnable messageLogger) {
    return log(messageLogger, footerLines);
  }

  /**
   * Sets the footer asynchronously. This will replace the previously-printed footer with the new
   * {@code footerLines}.
   *
   * <p>The footer is printed in <strong>bold</strong>.
   *
   * @param footerLines the footer, with each line as an element (no newline at end)
   * @return a {@link Future} to track completion
   */
  public Future<Void> setFooter(List<String> footerLines) {
    if (footerLines.equals(this.footerLines)) {
      return Futures.immediateFuture(null);
    }

    return log(() -> {}, footerLines);
  }

  private Future<Void> log(Runnable messageLogger, List<String> newFooterLines) {
    return executorService.submit(
        () -> {
          StringBuilder plainLogBuilder = new StringBuilder();

          // Moves the cursor up to the start of the footer.
          // TODO: Optimize to single init.
          for (int i = 0; i < this.footerLines.size(); i++) {
            // Moves cursor up.
            plainLogBuilder.append(CURSOR_UP_SEQUENCE);
          }

          // Erases everything below cursor.
          plainLogBuilder.append(ERASE_DISPLAY_BELOW);

          // Writes out logMessage and footer.
          plainLogger.accept(plainLogBuilder.toString());
          messageLogger.run();

          if (newFooterLines.size() > 0) {
            StringBuilder footerBuilder = new StringBuilder();
            for (String newFooterLine : newFooterLines) {
              footerBuilder.append(BOLD).append(newFooterLine).append(UNBOLD).append('\n');
            }
            // Removes last newline.
            footerBuilder.setLength(footerBuilder.length() - 1);
            plainLogger.accept(footerBuilder.toString());
          }

          this.footerLines = newFooterLines;

          return null;
        });
  }
}
