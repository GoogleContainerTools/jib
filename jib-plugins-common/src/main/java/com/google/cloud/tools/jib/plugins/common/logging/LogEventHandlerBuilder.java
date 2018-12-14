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

import com.google.cloud.tools.jib.event.events.LogEvent;
import com.google.cloud.tools.jib.event.events.LogEvent.Level;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import java.util.function.Consumer;
import java.util.function.Function;

/** Builds a handler for {@link LogEvent}. */
public class LogEventHandlerBuilder {

  /**
   * Alias for function that takes a map from {@link Level} to a log message {@link Consumer} and
   * creates a {@link ConsoleLogger}.
   */
  @VisibleForTesting
  @FunctionalInterface
  interface ConsoleLoggerFactory
      extends Function<ImmutableMap<Level, Consumer<String>>, ConsoleLogger> {}

  /**
   * Starts a {@link LogEventHandlerBuilder} for rich logging (ANSI support with footer).
   *
   * @param singleThreadedExecutor a {@link SingleThreadedExecutor} to ensure that all messages are
   *     logged in a sequential, deterministic order
   * @return a new {@link LogEventHandlerBuilder}
   */
  public static LogEventHandlerBuilder rich(SingleThreadedExecutor singleThreadedExecutor) {
    return new LogEventHandlerBuilder(
        messageConsumers -> new AnsiLoggerWithFooter(messageConsumers, singleThreadedExecutor));
  }

  /**
   * Starts a {@link LogEventHandlerBuilder} for plain-text logging (no ANSI support).
   *
   * @param singleThreadedExecutor a {@link SingleThreadedExecutor} to ensure that all messages are
   *     logged in a sequential, deterministic order
   * @return a new {@link LogEventHandlerBuilder}
   */
  public static LogEventHandlerBuilder plain(SingleThreadedExecutor singleThreadedExecutor) {
    return new LogEventHandlerBuilder(
        messageConsumers -> new PlainConsoleLogger(messageConsumers, singleThreadedExecutor));
  }

  private final ImmutableMap.Builder<Level, Consumer<String>> messageConsumers =
      ImmutableMap.builder();
  private final ConsoleLoggerFactory consoleLoggerFactory;

  @VisibleForTesting
  LogEventHandlerBuilder(ConsoleLoggerFactory consoleLoggerFactory) {
    this.consoleLoggerFactory = consoleLoggerFactory;
  }

  /**
   * Sets the {@link Consumer} to log a {@link Level#LIFECYCLE} message.
   *
   * @param messageConsumer the message {@link Consumer}
   * @return this
   */
  public LogEventHandlerBuilder lifecycle(Consumer<String> messageConsumer) {
    messageConsumers.put(Level.LIFECYCLE, messageConsumer);
    return this;
  }

  /**
   * Sets the {@link Consumer} to log a {@link Level#PROGRESS} message.
   *
   * @param messageConsumer the message {@link Consumer}
   * @return this
   */
  public LogEventHandlerBuilder progress(Consumer<String> messageConsumer) {
    messageConsumers.put(Level.PROGRESS, messageConsumer);
    return this;
  }

  /**
   * Sets the {@link Consumer} to log a {@link Level#DEBUG} message.
   *
   * @param messageConsumer the message {@link Consumer}
   * @return this
   */
  public LogEventHandlerBuilder debug(Consumer<String> messageConsumer) {
    messageConsumers.put(Level.DEBUG, messageConsumer);
    return this;
  }

  /**
   * Sets the {@link Consumer} to log an {@link Level#ERROR} message.
   *
   * @param messageConsumer the message {@link Consumer}
   * @return this
   */
  public LogEventHandlerBuilder error(Consumer<String> messageConsumer) {
    messageConsumers.put(Level.ERROR, messageConsumer);
    return this;
  }

  /**
   * Sets the {@link Consumer} to log an {@link Level#INFO} message.
   *
   * @param messageConsumer the message {@link Consumer}
   * @return this
   */
  public LogEventHandlerBuilder info(Consumer<String> messageConsumer) {
    messageConsumers.put(Level.INFO, messageConsumer);
    return this;
  }

  /**
   * Sets the {@link Consumer} to log a {@link Level#WARN} message.
   *
   * @param messageConsumer the message {@link Consumer}
   * @return this
   */
  public LogEventHandlerBuilder warn(Consumer<String> messageConsumer) {
    messageConsumers.put(Level.WARN, messageConsumer);
    return this;
  }

  /**
   * Builds the {@link LogEvent} handler.
   *
   * @return the {@link Consumer}
   */
  public Consumer<LogEvent> build() {
    ConsoleLogger consoleLogger = consoleLoggerFactory.apply(messageConsumers.build());
    return logEvent -> consoleLogger.log(logEvent.getLevel(), logEvent.getMessage());
  }
}
