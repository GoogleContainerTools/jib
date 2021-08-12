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
import com.google.common.collect.ImmutableMap;
import java.util.function.Consumer;
import java.util.function.Function;

/** Builds a {@link ConsoleLogger}. */
public class ConsoleLoggerBuilder {

  /**
   * Alias for function that takes a map from {@link Level} to a log message {@link Consumer} and
   * creates a {@link ConsoleLogger}.
   */
  @VisibleForTesting
  @FunctionalInterface
  interface ConsoleLoggerFactory
      extends Function<ImmutableMap<Level, Consumer<String>>, ConsoleLogger> {}

  /**
   * Starts a {@link ConsoleLoggerBuilder} for rich logging (ANSI support with footer).
   *
   * @param singleThreadedExecutor a {@link SingleThreadedExecutor} to ensure that all messages are
   *     logged in a sequential, deterministic order
   * @param enableTwoCursorUpJump allows the logger to move the cursor up twice at once. Fixes a
   *     logging issue in Maven (https://github.com/GoogleContainerTools/jib/issues/1952) but causes
   *     a problem in Gradle (https://github.com/GoogleContainerTools/jib/issues/1963)
   * @return a new {@link ConsoleLoggerBuilder}
   */
  public static ConsoleLoggerBuilder rich(
      SingleThreadedExecutor singleThreadedExecutor, boolean enableTwoCursorUpJump) {
    return new ConsoleLoggerBuilder(
        messageConsumerMap ->
            new AnsiLoggerWithFooter(
                messageConsumerMap, singleThreadedExecutor, enableTwoCursorUpJump));
  }

  /**
   * Starts a {@link ConsoleLoggerBuilder} for plain-text logging (no ANSI support).
   *
   * @param singleThreadedExecutor a {@link SingleThreadedExecutor} to ensure that all messages are
   *     logged in a sequential, deterministic order
   * @return a new {@link ConsoleLoggerBuilder}
   */
  public static ConsoleLoggerBuilder plain(SingleThreadedExecutor singleThreadedExecutor) {
    return new ConsoleLoggerBuilder(
        messageConsumerMap -> new PlainConsoleLogger(messageConsumerMap, singleThreadedExecutor));
  }

  private final ImmutableMap.Builder<Level, Consumer<String>> messageConsumers =
      ImmutableMap.builder();
  private final ConsoleLoggerFactory consoleLoggerFactory;

  @VisibleForTesting
  ConsoleLoggerBuilder(ConsoleLoggerFactory consoleLoggerFactory) {
    this.consoleLoggerFactory = consoleLoggerFactory;
  }

  /**
   * Sets the {@link Consumer} to log a {@link Level#LIFECYCLE} message.
   *
   * @param messageConsumer the message {@link Consumer}
   * @return this
   */
  public ConsoleLoggerBuilder lifecycle(Consumer<String> messageConsumer) {
    messageConsumers.put(Level.LIFECYCLE, messageConsumer);
    return this;
  }

  /**
   * Sets the {@link Consumer} to log a {@link Level#PROGRESS} message.
   *
   * @param messageConsumer the message {@link Consumer}
   * @return this
   */
  public ConsoleLoggerBuilder progress(Consumer<String> messageConsumer) {
    messageConsumers.put(Level.PROGRESS, messageConsumer);
    return this;
  }

  /**
   * Sets the {@link Consumer} to log a {@link Level#DEBUG} message.
   *
   * @param messageConsumer the message {@link Consumer}
   * @return this
   */
  public ConsoleLoggerBuilder debug(Consumer<String> messageConsumer) {
    messageConsumers.put(Level.DEBUG, messageConsumer);
    return this;
  }

  /**
   * Sets the {@link Consumer} to log an {@link Level#ERROR} message.
   *
   * @param messageConsumer the message {@link Consumer}
   * @return this
   */
  public ConsoleLoggerBuilder error(Consumer<String> messageConsumer) {
    messageConsumers.put(Level.ERROR, messageConsumer);
    return this;
  }

  /**
   * Sets the {@link Consumer} to log an {@link Level#INFO} message.
   *
   * @param messageConsumer the message {@link Consumer}
   * @return this
   */
  public ConsoleLoggerBuilder info(Consumer<String> messageConsumer) {
    messageConsumers.put(Level.INFO, messageConsumer);
    return this;
  }

  /**
   * Sets the {@link Consumer} to log a {@link Level#WARN} message.
   *
   * @param messageConsumer the message {@link Consumer}
   * @return this
   */
  public ConsoleLoggerBuilder warn(Consumer<String> messageConsumer) {
    messageConsumers.put(Level.WARN, messageConsumer);
    return this;
  }

  /**
   * Builds the {@link ConsoleLogger}.
   *
   * @return the {@link ConsoleLogger}
   */
  public ConsoleLogger build() {
    return consoleLoggerFactory.apply(messageConsumers.build());
  }
}
