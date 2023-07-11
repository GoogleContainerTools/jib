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
import com.google.cloud.tools.jib.api.Ports;
import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.buildplan.ImageFormat;
import com.google.common.collect.ImmutableList;
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

/** Tests for {@link BuildFileSpec}. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BuildFileSpecTest {

  private static final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

  @Test
  void testBuildFileSpec_full() throws JsonProcessingException {
    String data =
        "apiVersion: v1alpha1\n"
            + "kind: BuildFile\n"
            + "from:\n" // trivial base image spec
            + "  image: gcr.io/example/jib\n"
            + "creationTime: 1\n"
            + "format: OCI\n"
            + "environment:\n"
            + "  env_key: env_value\n"
            + "labels:\n"
            + "  label_key: label_value\n"
            + "volumes:\n"
            + "   - /my/volume\n"
            + "exposedPorts:\n"
            + "  - 8080\n"
            + "user: username\n"
            + "workingDirectory: /workspace\n"
            + "entrypoint:\n"
            + "  - java\n"
            + "  - -jar\n"
            + "cmd:\n"
            + "  - myjar.jar\n"
            + "layers:\n" // trivial layers
            + "  entries:\n"
            + "    - name: some layer\n"
            + "      archive: /something.tgz\n";

    BuildFileSpec parsed = mapper.readValue(data, BuildFileSpec.class);
    assertThat(parsed.getApiVersion()).isEqualTo("v1alpha1");
    assertThat(parsed.getKind()).isEqualTo("BuildFile");
    assertThat(parsed.getFrom().get().getImage()).isEqualTo("gcr.io/example/jib");
    assertThat(parsed.getCreationTime().get()).isEqualTo(Instant.ofEpochMilli(1));
    assertThat(parsed.getFormat().get()).isEqualTo(ImageFormat.OCI);
    assertThat(parsed.getEnvironment()).containsExactly("env_key", "env_value");
    assertThat(parsed.getLabels()).containsExactly("label_key", "label_value");
    assertThat(parsed.getVolumes()).containsExactly(AbsoluteUnixPath.get("/my/volume"));
    assertThat(parsed.getExposedPorts()).isEqualTo(Ports.parse(ImmutableList.of("8080")));
    assertThat(parsed.getUser().get()).isEqualTo("username");
    assertThat(parsed.getWorkingDirectory().get()).isEqualTo(AbsoluteUnixPath.get("/workspace"));
    assertThat(parsed.getEntrypoint().get()).containsExactly("java", "-jar").inOrder();
    assertThat(parsed.getCmd().get()).containsExactly("myjar.jar");
    assertThat(((ArchiveLayerSpec) parsed.getLayers().get().getEntries().get(0)).getName())
        .isEqualTo("some layer");
    assertThat(((ArchiveLayerSpec) parsed.getLayers().get().getEntries().get(0)).getArchive())
        .isEqualTo(Paths.get("/something.tgz"));
  }

  @Test
  void testBuildFileSpec_apiVersionRequired() {
    String data = "kind: BuildFile\n";

    Exception exception =
        assertThrows(
            JsonProcessingException.class, () -> mapper.readValue(data, BuildFileSpec.class));
    assertThat(exception)
        .hasMessageThat()
        .startsWith("Missing required creator property 'apiVersion'");
  }

  @Test
  void testBuildFileSpec_apiVersionNotNull() {
    String data = "apiVersion: null\n" + "kind: BuildFile\n";

    Exception exception =
        assertThrows(
            JsonProcessingException.class, () -> mapper.readValue(data, BuildFileSpec.class));
    assertThat(exception).hasMessageThat().contains("Property 'apiVersion' cannot be null");
  }

  @Test
  void testBuildFileSpec_apiVersionNotEmpty() {
    String data = "apiVersion: ''\n" + "kind: BuildFile\n";

    Exception exception =
        assertThrows(
            JsonProcessingException.class, () -> mapper.readValue(data, BuildFileSpec.class));
    assertThat(exception)
        .hasMessageThat()
        .contains("Property 'apiVersion' cannot be an empty string");
  }

  @Test
  void testBuildFileSpec_kindRequired() {
    String data = "apiVersion: v1alpha1\n";

    Exception exception =
        assertThrows(
            JsonProcessingException.class, () -> mapper.readValue(data, BuildFileSpec.class));
    assertThat(exception).hasMessageThat().startsWith("Missing required creator property 'kind'");
  }

  @Test
  void testBuildFileSpec_kindMustBeBuildFile() {
    String data = "apiVersion: v1alpha1\n" + "kind: NotBuildFile\n";

    Exception exception =
        assertThrows(
            JsonProcessingException.class, () -> mapper.readValue(data, BuildFileSpec.class));
    assertThat(exception)
        .hasMessageThat()
        .contains("Property 'kind' must be 'BuildFile' but is 'NotBuildFile'");
  }

  @Test
  void testBuildFileSpec_kindNotNull() {
    String data = "apiVersion: v1alpha1\n" + "kind: null\n";

    Exception exception =
        assertThrows(
            JsonProcessingException.class, () -> mapper.readValue(data, BuildFileSpec.class));
    assertThat(exception).hasMessageThat().contains("Property 'kind' cannot be null");
  }

  @Test
  void testBuildFileSpec_nullCollections() throws JsonProcessingException {
    String data = "apiVersion: v1alpha1\n" + "kind: BuildFile\n";

    BuildFileSpec parsed = mapper.readValue(data, BuildFileSpec.class);
    assertThat(parsed.getEnvironment()).isEmpty();
    assertThat(parsed.getLabels()).isEmpty();
    assertThat(parsed.getVolumes()).isEmpty();
    assertThat(parsed.getExposedPorts()).isEmpty();
    // entrypoint and cmd CAN be not present
    assertThat(parsed.getEntrypoint()).isEmpty();
    assertThat(parsed.getCmd()).isEmpty();
  }

  @ParameterizedTest
  @CsvSource(value = {"volumes", "exposedPorts", "entrypoint", "cmd"})
  void testBuildFileSpec_noNullEntries(String fieldName) {
    String data = "apiVersion: v1alpha1\n" + "kind: BuildFile\n" + fieldName + ": ['first', null]";

    Exception exception =
        assertThrows(
            JsonProcessingException.class, () -> mapper.readValue(data, BuildFileSpec.class));
    assertThat(exception)
        .hasMessageThat()
        .contains("Property '" + fieldName + "' cannot contain null entries");
  }

  @ParameterizedTest
  @CsvSource(value = {"volumes", "exposedPorts", "entrypoint", "cmd"})
  void testBuildFileSpec_noEmptyEntries(String fieldName) {
    String data = "apiVersion: v1alpha1\n" + "kind: BuildFile\n" + fieldName + ": ['first', ' ']";

    Exception exception =
        assertThrows(
            JsonProcessingException.class, () -> mapper.readValue(data, BuildFileSpec.class));
    assertThat(exception)
        .hasMessageThat()
        .contains("Property '" + fieldName + "' cannot contain empty strings");
  }

  @ParameterizedTest
  @CsvSource(value = {"volumes", "exposedPorts", "entrypoint", "cmd"})
  void testBuildFileSpec_emptyListOkay(String fieldName) throws JsonProcessingException {
    String data = "apiVersion: v1alpha1\n" + "kind: BuildFile\n" + fieldName + ": []";

    assertThat(mapper.readValue(data, BuildFileSpec.class)).isNotNull();
  }

  @ParameterizedTest
  @CsvSource(
      value = {
        "volumes",
        "exposedPorts",
        "entrypoint",
        "cmd",
        "creationTime",
        "format",
        "user",
        "workingDirectory",
        "environment",
        "labels"
      })
  void testBuildFileSpec_nullOkay(String fieldName) throws JsonProcessingException {
    String data = "apiVersion: v1alpha1\n" + "kind: BuildFile\n" + fieldName + ": null";

    assertThat(mapper.readValue(data, BuildFileSpec.class)).isNotNull();
  }

  @ParameterizedTest
  @CsvSource(value = {"creationTime", "format", "user", "workingDirectory"})
  void testBuildFileSpec_noEmptyValues(String fieldName) {
    String data = "apiVersion: v1alpha1\n" + "kind: BuildFile\n" + fieldName + ": ' '";
    Exception exception =
        assertThrows(
            JsonProcessingException.class, () -> mapper.readValue(data, BuildFileSpec.class));
    assertThat(exception)
        .hasMessageThat()
        .contains("Property '" + fieldName + "' cannot be an empty string");
  }

  @SuppressWarnings("unused")
  private static Stream<Arguments> invalidMapEntries() {
    return Stream.of(
        Arguments.of(new Object[] {"environment", "  key: null", "' cannot contain null values"}),
        Arguments.of(
            new Object[] {"environment", "  key: ' '", "' cannot contain empty string values"}),
        Arguments.of(
            new Object[] {"environment", "  ' ': value", "' cannot contain empty string keys"}),
        Arguments.of(new Object[] {"labels", "  key: null", "' cannot contain null values"}),
        Arguments.of(new Object[] {"labels", "  key: ' '", "' cannot contain empty string values"}),
        Arguments.of(
            new Object[] {"labels", "  ' ': value", "' cannot contain empty string keys"}));
  }

  @ParameterizedTest
  @MethodSource("invalidMapEntries")
  void testBuildFileSpec_invalidMapEntries(String fieldName, String input, String errorMessage) {
    String data = "apiVersion: v1alpha1\n" + "kind: BuildFile\n" + fieldName + ":\n" + input;

    Exception exception =
        assertThrows(
            JsonProcessingException.class, () -> mapper.readValue(data, BuildFileSpec.class));
    assertThat(exception).hasMessageThat().contains("Property '" + fieldName + errorMessage);
  }

  // A quirk of our parser is that "null" keys are parsed as strings and not null, this test just
  // formalizes that behavior.
  @ParameterizedTest
  @CsvSource(value = {"environment", "labels"})
  void testBuildFileSpec_yamlNullKeysPass(String fieldName) throws JsonProcessingException {
    String data =
        "apiVersion: v1alpha1\n" + "kind: BuildFile\n" + fieldName + ":\n" + "  null: value";

    assertThat(mapper.readValue(data, BuildFileSpec.class)).isNotNull();
  }

  @ParameterizedTest
  @CsvSource({"environment", "labels"})
  void testBuildFileSpec_emptyMapOkay(String fieldName) throws JsonProcessingException {
    String data = "apiVersion: v1alpha1\n" + "kind: BuildFile\n" + fieldName + ": {}";

    assertThat(mapper.readValue(data, BuildFileSpec.class)).isNotNull();
  }
}
