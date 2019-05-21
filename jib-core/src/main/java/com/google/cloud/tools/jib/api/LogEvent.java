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

package com.google.cloud.tools.jib.api;

import com.google.common.annotations.VisibleForTesting;
import java.util.Objects;

/** Log message event. */
public class LogEvent implements JibEvent {

  /** Log levels, in order of verbosity. */
  public enum Level {

    /** Something went wrong. */
    ERROR,

    /** Something might not work as intended. */
    WARN,

    /** Default. */
    LIFECYCLE,

    /** Same as {@link #LIFECYCLE}, except represents progress updates. */
    PROGRESS,

    /**
     * Details that can be ignored.
     *
     * <p>Use {@link #LIFECYCLE} for progress-indicating messages.
     */
    INFO,

    /** Useful for debugging. */
    DEBUG
  }

  public static LogEvent error(String message) {
    return new LogEvent(Level.ERROR, message);
  }

  public static LogEvent lifecycle(String message) {
    return new LogEvent(Level.LIFECYCLE, message);
  }

  public static LogEvent progress(String message) {
    return new LogEvent(Level.PROGRESS, message);
  }

  public static LogEvent warn(String message) {
    return new LogEvent(Level.WARN, message);
  }

  public static LogEvent info(String message) {
    return new LogEvent(Level.INFO, message);
  }

  public static LogEvent debug(String message) {
    return new LogEvent(Level.DEBUG, message);
  }

  private final Level level;
  private final String message;

  private LogEvent(Level level, String message) {
    this.level = level;
    this.message = message;
  }

  /**
   * Gets the log level to log at.
   *
   * @return the log level
   */
  public Level getLevel() {
    return level;
  }

  /**
   * Gets the log message.
   *
   * @return the log message
   */
  public String getMessage() {
    return message;
  }

  @VisibleForTesting
  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    }
    if (!(other instanceof LogEvent)) {
      return false;
    }

    LogEvent otherLogEvent = (LogEvent) other;
    return level == otherLogEvent.level && message.equals(otherLogEvent.message);
  }

  @VisibleForTesting
  @Override
  public int hashCode() {
    return Objects.hash(level, message);
  }

  @Override
  public String toString() {
    return "LogEvent [level=" + level + ", message=" + message + "]";
  }
}
