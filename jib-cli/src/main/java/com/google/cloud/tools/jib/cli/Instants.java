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

package com.google.cloud.tools.jib.cli;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;

/** Helper class to convert various strings in a buildfile to Instants. */
public class Instants {
  /**
   * Parses a time string into Instant. The string must be time in milliseconds since unix epoch or
   * an iso8601 datetime.
   *
   * @param time in milliseconds since epoch or iso8601 format
   * @param fieldName name of field being parsed (for error messaging)
   * @return Instant value of parsed time
   */
  public static Instant fromMillisOrIso8601(String time, String fieldName) {
    try {
      return Instant.ofEpochMilli(Long.parseLong(time));
    } catch (NumberFormatException nfe) {
      // TODO: copied from PluginConfigurationProcessor, find a way to share better
      try {
        DateTimeFormatter formatter =
            new DateTimeFormatterBuilder()
                .append(DateTimeFormatter.ISO_DATE_TIME)
                .optionalStart()
                .appendOffset("+HHmm", "+0000")
                .optionalEnd()
                .toFormatter();
        return formatter.parse(time, Instant::from);
      } catch (DateTimeParseException dtpe) {
        throw new IllegalArgumentException(
            fieldName
                + " must be a number of milliseconds since epoch or an ISO 8601 formatted date");
      }
    }
  }
}
