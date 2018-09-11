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

package com.google.cloud.tools.jib.gradle;

import com.google.cloud.tools.jib.event.events.LogEvent;
import java.util.function.Consumer;
import org.gradle.api.logging.Logger;

/** Handles {@link LogEvent}s by passing to the Gradle {@link Logger}. */
public class LogEventHandler implements Consumer<LogEvent> {

  private final Logger logger;

  LogEventHandler(Logger logger) {
    this.logger = logger;
  }

  @Override
  public void accept(LogEvent logEvent) {
    switch (logEvent.getLevel()) {
      case LIFECYCLE:
        logger.lifecycle(logEvent.getMessage());
        break;

      case DEBUG:
        logger.debug(logEvent.getMessage());
        break;

      case ERROR:
        logger.error(logEvent.getMessage());
        break;

      case INFO:
        logger.info(logEvent.getMessage());
        break;

      case WARN:
        logger.warn("warning: " + logEvent.getMessage());
        break;
    }
  }
}
