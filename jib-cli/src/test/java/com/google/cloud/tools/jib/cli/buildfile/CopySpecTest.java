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
import static com.google.common.truth.Truth8.assertThat;
import static org.junit.Assert.assertThrows;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** Tests for {@link CopySpec}. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CopySpecTest {

  private static final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

  @Test
  void testCopySpec_full() throws JsonProcessingException {
    String data =
        "src: target/classes\n"
            + "dest: /app/classes\n"
            + "includes:\n"
            + "  - '**/*.in'\n"
            + "excludes:\n"
            + "  - '**/*.ex'\n"
            + "properties:\n" // only trivial test of file properties
            + "  timestamp: 1\n";

    CopySpec parsed = mapper.readValue(data, CopySpec.class);
    assertThat(parsed.getSrc()).isEqualTo(Paths.get("target/classes"));
    assertThat(parsed.getDest()).isEqualTo(AbsoluteUnixPath.get("/app/classes"));
    assertThat(parsed.getIncludes()).containsExactly("**/*.in");
    assertThat(parsed.getExcludes()).containsExactly("**/*.ex");
    assertThat(parsed.getProperties().get().getTimestamp().get())
        .isEqualTo(Instant.ofEpochMilli(1));
  }

  public static Stream<Arguments> requiredChecksParams() {
    return Stream.of(
        Arguments.of(
            new Object[] {"dest: /app/classes\n", "Missing required creator property 'src'"}),
        Arguments.of(
            new Object[] {"src: target/classes\n", "Missing required creator property 'dest'"}));
  }

  @ParameterizedTest
  @MethodSource("requiredChecksParams")
  void testCopySpec_required(String data, String errorMessage) {
    Exception exception =
        assertThrows(JsonProcessingException.class, () -> mapper.readValue(data, CopySpec.class));
    assertThat(exception).hasMessageThat().startsWith(errorMessage);
  }

  @Test
  void testCopySpec_destEndsWithSlash() throws JsonProcessingException {
    String data = "src: target/classes\n" + "dest: /app/classes/";

    CopySpec parsed = mapper.readValue(data, CopySpec.class);
    assertThat(parsed.getDest()).isEqualTo(AbsoluteUnixPath.get("/app/classes"));
    assertThat(parsed.isDestEndsWithSlash()).isTrue();
  }

  @Test
  void testCopySpec_destDoesNotEndWithSlash() throws JsonProcessingException {
    String data = "src: target/classes\n" + "dest: /app/classes";

    CopySpec parsed = mapper.readValue(data, CopySpec.class);
    assertThat(parsed.getDest()).isEqualTo(AbsoluteUnixPath.get("/app/classes"));
    assertThat(parsed.isDestEndsWithSlash()).isFalse();
  }

  public static Stream<Arguments> nullChecksParams() {
    return Stream.of(
        Arguments.of(
            new Object[] {"src: null\ndest: /app/classes\n", "Property 'src' cannot be null"}),
        Arguments.of(
            new Object[] {
              "src: ''\ndest: /app/classes\n", "Property 'src' cannot be an empty string"
            }),
        Arguments.of(
            new Object[] {"src: target/classes\ndest: null\n", "Property 'dest' cannot be null"}),
        Arguments.of(
            new Object[] {
              "src: target/classes\ndest: ''\n", "Property 'dest' cannot be an empty string"
            }));
  }

  @ParameterizedTest
  @MethodSource("nullChecksParams")
  void testCopySpec_nullEmptyCheck(String data, String errorMessage) {
    Exception exception =
        assertThrows(JsonProcessingException.class, () -> mapper.readValue(data, CopySpec.class));
    assertThat(exception).hasMessageThat().contains(errorMessage);
  }

  @Test
  void testCopySpec_nullCollections() throws JsonProcessingException {
    String data = "src: target/classes\n" + "dest: /app/classes\n";

    CopySpec parsed = mapper.readValue(data, CopySpec.class);
    assertThat(parsed.getIncludes()).isEmpty();
    assertThat(parsed.getExcludes()).isEmpty();
  }

  @ParameterizedTest
  @CsvSource(value = {"includes", "excludes"})
  void testCopySpec_noNullEntries(String fieldName) {
    String data =
        "src: target/classes\n" + "dest: /app/classes\n" + fieldName + ": ['first', null]";

    Exception exception =
        assertThrows(JsonProcessingException.class, () -> mapper.readValue(data, CopySpec.class));
    assertThat(exception)
        .hasCauseThat()
        .hasMessageThat()
        .isEqualTo("Property '" + fieldName + "' cannot contain null entries");
  }

  @ParameterizedTest
  @CsvSource(value = {"includes", "excludes"})
  void testCopySpec_noEmptyEntries(String fieldName) {
    String data = "src: target/classes\n" + "dest: /app/classes\n" + fieldName + ": ['first', ' ']";

    Exception exception =
        assertThrows(JsonProcessingException.class, () -> mapper.readValue(data, CopySpec.class));
    assertThat(exception)
        .hasCauseThat()
        .hasMessageThat()
        .isEqualTo("Property '" + fieldName + "' cannot contain empty strings");
  }

  @ParameterizedTest
  @CsvSource(value = {"includes", "excludes"})
  void testCopySpec_emptyOkay(String fieldName) throws JsonProcessingException {
    String data = "src: target/classes\n" + "dest: /app/classes\n" + fieldName + ": []";

    assertThat(mapper.readValue(data, CopySpec.class)).isNotNull();
  }

  @ParameterizedTest
  @CsvSource(value = {"includes", "excludes"})
  void testCopySpec_nullOkay(String fieldName) throws JsonProcessingException {
    String data = "src: target/classes\n" + "dest: /app/classes\n" + fieldName + ": null";

    assertThat(mapper.readValue(data, CopySpec.class)).isNotNull();
  }
}
