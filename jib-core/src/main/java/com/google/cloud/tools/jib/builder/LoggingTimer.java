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

package com.google.cloud.tools.jib.builder;

import com.google.cloud.tools.jib.event.events.LogEvent;
import java.util.function.Consumer;

/** Gets {@link com.google.cloud.tools.jib.builder.Timer}s that produces consumable log messages. */
public class LoggingTimer {

  private static StringBuilder getTabs(int depth) {
    StringBuilder tabs = new StringBuilder();
    for (int i = 0; i < depth; i++) {
      tabs.append("\t");
    }
    return tabs;
  }

  /**
   * Creates a new {@link com.google.cloud.tools.jib.builder.Timer} that produces consumable log
   * messages..
   *
   * @param logMessageConsumer consumers the log messages
   * @param label a label for what is being timed
   * @return a new {@link com.google.cloud.tools.jib.builder.Timer}
   */
  public static Timer newTimer(Consumer<String> logMessageConsumer, String label) {
    return newTimer(logMessageConsumer, label, 0);
  }

  /**
   * Creates a new {@link com.google.cloud.tools.jib.builder.Timer} that emits {@link LogEvent}s.
   *
   * @param logMessageConsumer consumers the log messages
   * @param label a label for what is being timed
   * @param depth the tabulation depth at which to place the log messages
   * @return a new {@link com.google.cloud.tools.jib.builder.Timer}
   */
  public static Timer newTimer(Consumer<String> logMessageConsumer, String label, int depth) {
    logMessageConsumer.accept(getTabs(depth).append("TIMING\t").append(label).toString());

    return new Timer(
        nanoTime ->
            logMessageConsumer.accept(
                getTabs(depth)
                    .append("TIMED\t")
                    .append(label)
                    .append(" : ")
                    .append(nanoTime / 1000 / 1000.0)
                    .append(" ms")
                    .toString()));
  }

  private LoggingTimer() {}
}
