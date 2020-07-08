package com.google.cloud.tools.jib.cli.buildfile;

import static org.junit.Assert.*;

import java.time.Instant;
import org.junit.Assert;
import org.junit.Test;

/** Tests for {@link Instants}. */
public class InstantsTest {

  @Test
  public void testFromMillisOrIso8601_millis() {
    Instant parsed = Instants.fromMillisOrIso8601("100", "ignored");
    Assert.assertEquals(Instant.ofEpochMilli(100), parsed);
  }

  @Test
  public void testFromMillisOrIso8601_iso8601() {
    Instant parsed = Instants.fromMillisOrIso8601("2020-06-08T14:54:36+00:00", "ignored");
    Assert.assertEquals(Instant.parse("2020-06-08T14:54:36Z"), parsed);
  }

  @Test
  public void testFromMillisOrIso8601_failed() {
    try {
      Instants.fromMillisOrIso8601("bad-time", "testFieldName");
      Assert.fail();
    } catch (IllegalArgumentException iae) {
      Assert.assertEquals(
          "testFieldName must be a number of milliseconds since epoch or an ISO 8601 formatted date",
          iae.getMessage());
    }
  }
}
