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

import com.google.cloud.tools.jib.api.LogEvent;
import com.google.cloud.tools.jib.api.LogEvent.Level;
import java.util.List;

/** Logs messages to the console. Implementations must be thread-safe. */
public interface ConsoleLogger {

  /**
   * Logs {@code message} to the console at {@link Level#LIFECYCLE}.
   *
   * @param logLevel the log level for the {@code message}
   * @param message the message
   */
  void log(LogEvent.Level logLevel, String message);

  /**
   * Sets the footer.
   *
   * @param footerLines the footer, with each line as an element (no newline at end)
   */
  void setFooter(List<String> footerLines);
}
