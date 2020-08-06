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

package com.google.cloud.tools.jib.cli.buildfile;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.cloud.tools.jib.api.buildplan.FilePermissions;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** Tests for {@link FilePropertiesSpec}. */
public class FilePropertiesSpecTest {

  private static final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

  @Test
  public void testFilePropertiesSpec_full() throws JsonProcessingException {
    String data =
        "filePermissions: 644\n"
            + "directoryPermissions: 755\n"
            + "user: goose\n"
            + "group: birds\n"
            + "timestamp: 1\n";

    FilePropertiesSpec parsed = mapper.readValue(data, FilePropertiesSpec.class);
    Assert.assertEquals(FilePermissions.fromOctalString("644"), parsed.getFilePermissions().get());
    Assert.assertEquals(
        FilePermissions.fromOctalString("755"), parsed.getDirectoryPermissions().get());
    Assert.assertEquals("goose", parsed.getUser().get());
    Assert.assertEquals("birds", parsed.getGroup().get());
    Assert.assertEquals(Instant.ofEpochMilli(1), parsed.getTimestamp().get());
  }

  @Test
  public void testFilePropertiesSpec_badFilePermissions() throws JsonProcessingException {
    String data = "filePermissions: 888";

    try {
      mapper.readValue(data, FilePropertiesSpec.class);
      Assert.fail();
    } catch (JsonMappingException ex) {
      Assert.assertEquals(
          "octalPermissions must be a 3-digit octal number (000-777)", ex.getCause().getMessage());
    }
  }

  @Test
  public void testFilePropertiesSpec_badDirectoryPermissions() throws JsonProcessingException {
    String data = "directoryPermissions: 888";

    try {
      mapper.readValue(data, FilePropertiesSpec.class);
      Assert.fail();
    } catch (JsonMappingException ex) {
      Assert.assertEquals(
          "octalPermissions must be a 3-digit octal number (000-777)", ex.getCause().getMessage());
    }
  }

  @Test
  public void testFilePropertiesSpec_timestampSpecIso8601() throws JsonProcessingException {
    String data = "timestamp: 2020-06-08T14:54:36+00:00";

    FilePropertiesSpec parsed = mapper.readValue(data, FilePropertiesSpec.class);
    Assert.assertEquals(Instant.parse("2020-06-08T14:54:36Z"), parsed.getTimestamp().get());
  }

  @Test
  public void testFilePropertiesSpec_badTimestamp() throws JsonProcessingException {
    String data = "timestamp: hi";

    try {
      mapper.readValue(data, FilePropertiesSpec.class);
      Assert.fail();
    } catch (JsonMappingException ex) {
      Assert.assertEquals(
          "timestamp must be a number of milliseconds since epoch or an ISO 8601 formatted date",
          ex.getCause().getMessage());
    }
  }

  @Test
  public void testFilePropertiesSpec_failOnUnknown() throws JsonProcessingException {
    String data = "badkey: badvalue";

    try {
      mapper.readValue(data, FilePropertiesSpec.class);
      Assert.fail();
    } catch (UnrecognizedPropertyException upe) {
      MatcherAssert.assertThat(
          upe.getMessage(), CoreMatchers.containsString("Unrecognized field \"badkey\""));
    }
  }

  @RunWith(Parameterized.class)
  public static class OptionalStringTests {

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> data() {
      return Arrays.asList(
          new Object[][] {
            {"filePermissions"}, {"directoryPermissions"}, {"user"}, {"group"}, {"timestamp"}
          });
    }

    @Parameterized.Parameter public String fieldName;

    @Test
    public void testFilePropertiesSpec_noEmptyValues() {
      String data = fieldName + ": ' '";

      try {
        mapper.readValue(data, FilePropertiesSpec.class);
        Assert.fail();
      } catch (JsonProcessingException ex) {
        Assert.assertEquals(
            "Property '" + fieldName + "' cannot be an empty string", ex.getCause().getMessage());
      }
    }

    @Test
    public void testFilePropertiesSpec_nullOkay() throws JsonProcessingException {
      String data = fieldName + ": null";

      mapper.readValue(data, FilePropertiesSpec.class);
      // pass
    }
  }
}
