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

package com.google.cloud.tools.jib.gradle;

import com.google.cloud.tools.jib.api.LogEvent;
import com.google.cloud.tools.jib.plugins.extension.ExtensionLogger;
import java.util.function.Consumer;

/** Logger for Gradle plugin extensions. */
class GradleExtensionLogger implements ExtensionLogger {

  private final Consumer<LogEvent> logger;

  GradleExtensionLogger(Consumer<LogEvent> logger) {
    this.logger = logger;
  }

  @Override
  public void log(ExtensionLogger.LogLevel logLevel, String message) {
    switch (logLevel) {
      case ERROR:
        logger.accept(LogEvent.error(message));
        break;
      case WARN:
        logger.accept(LogEvent.warn(message));
        break;
      case LIFECYCLE:
        logger.accept(LogEvent.lifecycle(message));
        break;
      case INFO:
        logger.accept(LogEvent.info(message));
        break;
      case DEBUG:
        logger.accept(LogEvent.debug(message));
        break;
      default:
        throw new RuntimeException();
    }
  }
}
