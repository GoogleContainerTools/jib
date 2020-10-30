/*
 * Copyright 2020 Google LLC.
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

package com.google.cloud.tools.jib.cli.cli2.logging;

import com.google.cloud.tools.jib.plugins.common.logging.ConsoleLogger;
import com.google.cloud.tools.jib.plugins.common.logging.ConsoleLoggerBuilder;
import com.google.cloud.tools.jib.plugins.common.logging.SingleThreadedExecutor;
import com.google.common.annotations.VisibleForTesting;
import java.io.PrintStream;

/** A simple CLI logger that logs to the command line based on the configured log level. */
public class CliLogger {

  /**
   * Create a new logger for the CLI.
   *
   * @param verbosity the configure verbosity
   * @param consoleOutput the configured consoleOutput format
   * @return a new ConsoleLogger instance
   */
  public static ConsoleLogger newLogger(Verbosity verbosity, ConsoleOutput consoleOutput) {
    return newLogger(
        verbosity, consoleOutput, System.out, System.err, new SingleThreadedExecutor());
  }

  @VisibleForTesting
  static ConsoleLogger newLogger(
      Verbosity verbosity,
      ConsoleOutput consoleOutput,
      PrintStream stdout,
      PrintStream stderr,
      SingleThreadedExecutor executor) {
    boolean enableRichProgress =
        isRichConsole(consoleOutput) && verbosity.atLeast(Verbosity.lifecycle);
    ConsoleLoggerBuilder builder =
        enableRichProgress
            ? ConsoleLoggerBuilder.rich(executor, false)
            : ConsoleLoggerBuilder.plain(executor);
    if (verbosity.atLeast(Verbosity.error)) {
      builder.error(stderr::println);
    }
    if (verbosity.atLeast(Verbosity.warn)) {
      builder.warn(stdout::println);
    }
    if (verbosity.atLeast(Verbosity.lifecycle)) {
      builder.lifecycle(stdout::println);
      // Rich progress reporting will be through ProgressEvent (note this is not LogEvent of
      // Level.PROGRESS), so we ignore PROGRESS LogEvent.
      if (!enableRichProgress) {
        builder.progress(stdout::println);
      }
    }
    if (verbosity.atLeast(Verbosity.info)) {
      builder.info(stdout::println);
    }
    if (verbosity.atLeast(Verbosity.debug)) {
      builder.debug(stdout::println);
    }

    return builder.build();
  }

  @VisibleForTesting
  static boolean isRichConsole(ConsoleOutput consoleOutput) {
    switch (consoleOutput) {
      case plain:
        return false;
      case auto:
        // Enables progress footer when ANSI is supported (Windows or TERM not 'dumb').
        return System.getProperty("os.name").startsWith("windows")
            || !"dumb".equals(System.getenv("TERM"));
      case rich:
      default:
        return true;
    }
  }
}
