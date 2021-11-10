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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.cloud.tools.jib.api.buildplan.FilePermissions;
import java.time.Instant;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link FilePropertiesSpec}. */
@RunWith(JUnitParamsRunner.class)
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
    assertThat(parsed.getFilePermissions().get()).isEqualTo(FilePermissions.fromOctalString("644"));
    assertThat(parsed.getDirectoryPermissions().get())
        .isEqualTo(FilePermissions.fromOctalString("755"));
    assertThat(parsed.getUser().get()).isEqualTo("goose");
    assertThat(parsed.getGroup().get()).isEqualTo("birds");
    assertThat(parsed.getTimestamp().get()).isEqualTo(Instant.ofEpochMilli(1));
  }

  @Test
  public void testFilePropertiesSpec_badFilePermissions() {
    String data = "filePermissions: 888";

    Exception exception =
        assertThrows(
            JsonMappingException.class, () -> mapper.readValue(data, FilePropertiesSpec.class));
    assertThat(exception)
        .hasCauseThat()
        .hasMessageThat()
        .isEqualTo("octalPermissions must be a 3-digit octal number (000-777)");
  }

  @Test
  public void testFilePropertiesSpec_badDirectoryPermissions() {
    String data = "directoryPermissions: 888";

    Exception exception =
        assertThrows(
            JsonMappingException.class, () -> mapper.readValue(data, FilePropertiesSpec.class));
    assertThat(exception)
        .hasCauseThat()
        .hasMessageThat()
        .isEqualTo("octalPermissions must be a 3-digit octal number (000-777)");
  }

  @Test
  public void testFilePropertiesSpec_timestampSpecIso8601() throws JsonProcessingException {
    String data = "timestamp: 2020-06-08T14:54:36+00:00";

    FilePropertiesSpec parsed = mapper.readValue(data, FilePropertiesSpec.class);
    assertThat(parsed.getTimestamp().get()).isEqualTo(Instant.parse("2020-06-08T14:54:36Z"));
  }

  @Test
  public void testFilePropertiesSpec_badTimestamp() {
    String data = "timestamp: hi";

    Exception exception =
        assertThrows(
            JsonMappingException.class, () -> mapper.readValue(data, FilePropertiesSpec.class));
    assertThat(exception)
        .hasCauseThat()
        .hasMessageThat()
        .isEqualTo(
            "timestamp must be a number of milliseconds since epoch or an ISO 8601 formatted date");
  }

  @Test
  public void testFilePropertiesSpec_failOnUnknown() {
    String data = "badkey: badvalue";

    Exception exception =
        assertThrows(
            UnrecognizedPropertyException.class,
            () -> mapper.readValue(data, FilePropertiesSpec.class));
    assertThat(exception).hasMessageThat().contains("Unrecognized field \"badkey\"");
  }

  @Test
  @Parameters(value = {"filePermissions", "directoryPermissions", "user", "group", "timestamp"})
  public void testFilePropertiesSpec_noEmptyValues(String fieldName) {
    String data = fieldName + ": ' '";

    Exception exception =
        assertThrows(
            JsonProcessingException.class, () -> mapper.readValue(data, FilePropertiesSpec.class));
    assertThat(exception)
        .hasCauseThat()
        .hasMessageThat()
        .isEqualTo("Property '" + fieldName + "' cannot be an empty string");
  }

  @Test
  @Parameters(value = {"filePermissions", "directoryPermissions", "user", "group", "timestamp"})
  public void testFilePropertiesSpec_nullOkay(String fieldName) throws JsonProcessingException {
    String data = fieldName + ": null";

    FilePropertiesSpec parsed = mapper.readValue(data, FilePropertiesSpec.class);
    assertThat(parsed.getFilePermissions().isPresent()).isFalse();
    assertThat(parsed.getDirectoryPermissions().isPresent()).isFalse();
    assertThat(parsed.getUser().isPresent()).isFalse();
    assertThat(parsed.getGroup().isPresent()).isFalse();
  }
}
