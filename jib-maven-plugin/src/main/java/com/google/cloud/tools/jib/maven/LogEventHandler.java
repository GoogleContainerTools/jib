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

package com.google.cloud.tools.jib.maven;

import com.google.cloud.tools.jib.event.events.LogEvent;
import com.google.cloud.tools.jib.plugins.common.AnsiLoggerWithFooter;
import java.util.function.Consumer;
import org.apache.maven.plugin.logging.Log;

/** Handles {@link LogEvent}s by passing to the Maven {@link Log}. */
class LogEventHandler implements Consumer<LogEvent> {

  private final Log log;
  private final AnsiLoggerWithFooter ansiLoggerWithFooter;

  LogEventHandler(Log log, AnsiLoggerWithFooter ansiLoggerWithFooter) {
    this.log = log;
    this.ansiLoggerWithFooter = ansiLoggerWithFooter;
  }

  @Override
  public void accept(LogEvent logEvent) {
    switch (logEvent.getLevel()) {
      case LIFECYCLE:
        if (log.isInfoEnabled()) {
          ansiLoggerWithFooter.log(log::info, logEvent.getMessage());
        }
        break;

      case DEBUG:
        if (log.isDebugEnabled()) {
          ansiLoggerWithFooter.log(log::debug, logEvent.getMessage());
        }
        break;

      case ERROR:
        if (log.isErrorEnabled()) {
          ansiLoggerWithFooter.log(log::error, logEvent.getMessage());
        }
        break;

      case INFO:
        // Use lifecycle for progress-indicating messages.
        if (log.isDebugEnabled()) {
          ansiLoggerWithFooter.log(log::debug, logEvent.getMessage());
        }
        break;

      case WARN:
        if (log.isWarnEnabled()) {
          ansiLoggerWithFooter.log(log::warn, logEvent.getMessage());
        }
        break;

      default:
        throw new IllegalStateException("Unknown LogEvent.Level: " + logEvent.getLevel());
    }
  }
}
