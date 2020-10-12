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

package com.google.cloud.tools.jib.cli.cli2;

import com.google.cloud.tools.jib.plugins.common.logging.ConsoleLogger;
import com.google.cloud.tools.jib.plugins.common.logging.ConsoleLoggerBuilder;
import com.google.cloud.tools.jib.plugins.common.logging.SingleThreadedExecutor;
import com.google.common.annotations.VisibleForTesting;

/** A simple cli logger that logs to the command line based on the configured log level. */
public class CliLogger {
  public static ConsoleLogger newLogger(JibCli.Verbosity verbosity) {
    return newLogger(ConsoleLoggerBuilder.rich(new SingleThreadedExecutor(), true), verbosity);
  }

  @VisibleForTesting
  static ConsoleLogger newLogger(ConsoleLoggerBuilder builder, JibCli.Verbosity verbosity) {
    if (verbosity.value() >= JibCli.Verbosity.error.value()) {
      builder.error(System.err::println);
    }
    if (verbosity.value() >= JibCli.Verbosity.warn.value()) {
      builder.warn(System.out::println);
    }
    if (verbosity.value() >= JibCli.Verbosity.lifecycle.value()) {
      builder.lifecycle(System.out::println);
    }
    if (verbosity.value() >= JibCli.Verbosity.info.value()) {
      builder.info(System.out::println);
    }
    if (verbosity.value() >= JibCli.Verbosity.debug.value()) {
      builder.debug(System.out::println);
    }
    return builder.build();
  }
}
