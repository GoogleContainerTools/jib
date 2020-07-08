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
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.cloud.tools.jib.api.buildplan.FilePermissions;
import java.time.Instant;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/** Tests for {@link FilePropertiesSpec}. */
public class FilePropertiesSpecTest {

  private static ObjectMapper filePropertiesSpecMapper;

  @BeforeClass
  public static void createObjectMapper() {
    filePropertiesSpecMapper =
        new ObjectMapper(new YAMLFactory())
            .configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, true);
  }

  @Test
  public void testFilePropertiesSpec_full() throws JsonProcessingException {
    String data =
        ""
            + "filePermissions: 644\n"
            + "directoryPermissions: 755\n"
            + "user: goose\n"
            + "group: birds\n"
            + "timestamp: 1\n";

    FilePropertiesSpec parsed = filePropertiesSpecMapper.readValue(data, FilePropertiesSpec.class);
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
      filePropertiesSpecMapper.readValue(data, FilePropertiesSpec.class);
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
      filePropertiesSpecMapper.readValue(data, FilePropertiesSpec.class);
      Assert.fail();
    } catch (JsonMappingException ex) {
      Assert.assertEquals(
          "octalPermissions must be a 3-digit octal number (000-777)", ex.getCause().getMessage());
    }
  }

  @Test
  public void testFilePropertiesSpec_timestampSpecIso8601() throws JsonProcessingException {
    String data = "timestamp: 2020-06-08T14:54:36+00:00";

    FilePropertiesSpec parsed = filePropertiesSpecMapper.readValue(data, FilePropertiesSpec.class);
    Assert.assertEquals(Instant.parse("2020-06-08T14:54:36Z"), parsed.getTimestamp().get());
  }

  @Test
  public void testFilePropertiesSpec_badTimestamp() throws JsonProcessingException {
    String data = "timestamp: hi";

    try {
      filePropertiesSpecMapper.readValue(data, FilePropertiesSpec.class);
      Assert.fail();
    } catch (JsonMappingException ex) {
      Assert.assertEquals(
          "timestamp must be a number of milliseconds since epoch or an ISO 8601 formatted date",
          ex.getCause().getMessage());
    }
  }
}
