package com.google.cloud.tools.jib.cli.buildfile;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;

/** Helper class to convert various strings to Instants. */
public class Instants {
  /**
   * Parses a string into Instant, string must be time in millis or iso8601 datetime
   *
   * @param time in millis or is8601 format
   * @param fieldName name of field being parse (for error messaging)
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
