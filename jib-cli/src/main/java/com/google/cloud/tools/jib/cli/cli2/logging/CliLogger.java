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

/** A simple cli logger that logs to the command line based on the configured log level. */
public class CliLogger {

  /**
   * Create a new logger for the cli.
   *
   * @param verbosity the configure verbosity
   * @param consoleOutput the configured consoleOutput format
   * @return a new ConsoleLogger instance
   */
  public static ConsoleLogger newLogger(Verbosity verbosity, ConsoleOutput consoleOutput) {
    CliLogger cliLogger = new CliLogger(verbosity, System.out, System.err);
    boolean isRichConsole = isRichConsole(consoleOutput);

    return newLogger(cliLogger, isRichConsole, new SingleThreadedExecutor());
  }

  @VisibleForTesting
  static ConsoleLogger newLogger(
      CliLogger cliLogger, boolean isRichConsole, SingleThreadedExecutor executor) {
    // rich logger will use an explicit progress event handler
    ConsoleLoggerBuilder builder =
        isRichConsole
            ? ConsoleLoggerBuilder.rich(executor, true)
            : ConsoleLoggerBuilder.plain(executor).progress(cliLogger::lifecycle);
    builder.error(cliLogger::error);
    builder.warn(cliLogger::warn);
    builder.lifecycle(cliLogger::lifecycle);
    builder.info(cliLogger::info);
    builder.debug(cliLogger::debug);

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

  private final Verbosity verbosity;
  private final PrintStream out;
  private final PrintStream err;

  @VisibleForTesting
  CliLogger(Verbosity verbosity, PrintStream out, PrintStream err) {
    this.verbosity = verbosity;
    this.out = out;
    this.err = err;
  }

  @VisibleForTesting
  void debug(String message) {
    if (verbosity.atLeast(Verbosity.debug)) {
      out.println(message);
    }
  }

  @VisibleForTesting
  void info(String message) {
    if (verbosity.atLeast(Verbosity.info)) {
      out.println(message);
    }
  }

  @VisibleForTesting
  void lifecycle(String message) {
    if (verbosity.atLeast(Verbosity.lifecycle)) {
      out.println(message);
    }
  }

  @VisibleForTesting
  void warn(String message) {
    if (verbosity.atLeast(Verbosity.warn)) {
      out.println(message);
    }
  }

  @VisibleForTesting
  void error(String message) {
    if (verbosity.atLeast(Verbosity.error)) {
      err.println(message);
    }
  }
}
