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
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link BuildFileSpec}. */
@RunWith(JUnitParamsRunner.class)
public class BuildFileSpecTest {

  private static final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

  @Test
  public void testBuildFileSpec_full() throws JsonProcessingException {
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
  public void testBuildFileSpec_apiVersionRequired() {
    String data = "kind: BuildFile\n";

    Exception exception =
        assertThrows(
            JsonProcessingException.class, () -> mapper.readValue(data, BuildFileSpec.class));
    assertThat(exception)
        .hasMessageThat()
        .startsWith("Missing required creator property 'apiVersion'");
  }

  @Test
  public void testBuildFileSpec_apiVersionNotNull() {
    String data = "apiVersion: null\n" + "kind: BuildFile\n";

    Exception exception =
        assertThrows(
            JsonProcessingException.class, () -> mapper.readValue(data, BuildFileSpec.class));
    assertThat(exception).hasMessageThat().contains("Property 'apiVersion' cannot be null");
  }

  @Test
  public void testBuildFileSpec_apiVersionNotEmpty() {
    String data = "apiVersion: ''\n" + "kind: BuildFile\n";

    Exception exception =
        assertThrows(
            JsonProcessingException.class, () -> mapper.readValue(data, BuildFileSpec.class));
    assertThat(exception)
        .hasMessageThat()
        .contains("Property 'apiVersion' cannot be an empty string");
  }

  @Test
  public void testBuildFileSpec_kindRequired() {
    String data = "apiVersion: v1alpha1\n";

    Exception exception =
        assertThrows(
            JsonProcessingException.class, () -> mapper.readValue(data, BuildFileSpec.class));
    assertThat(exception).hasMessageThat().startsWith("Missing required creator property 'kind'");
  }

  @Test
  public void testBuildFileSpec_kindMustBeBuildFile() {
    String data = "apiVersion: v1alpha1\n" + "kind: NotBuildFile\n";

    Exception exception =
        assertThrows(
            JsonProcessingException.class, () -> mapper.readValue(data, BuildFileSpec.class));
    assertThat(exception)
        .hasMessageThat()
        .contains("Property 'kind' must be 'BuildFile' but is 'NotBuildFile'");
  }

  @Test
  public void testBuildFileSpec_kindNotNull() {
    String data = "apiVersion: v1alpha1\n" + "kind: null\n";

    Exception exception =
        assertThrows(
            JsonProcessingException.class, () -> mapper.readValue(data, BuildFileSpec.class));
    assertThat(exception).hasMessageThat().contains("Property 'kind' cannot be null");
  }

  @Test
  public void testBuildFileSpec_nullCollections() throws JsonProcessingException {
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

  @Test
  @Parameters(value = {"volumes", "exposedPorts", "entrypoint", "cmd"})
  public void testBuildFileSpec_noNullEntries(String fieldName) {
    String data = "apiVersion: v1alpha1\n" + "kind: BuildFile\n" + fieldName + ": ['first', null]";

    Exception exception =
        assertThrows(
            JsonProcessingException.class, () -> mapper.readValue(data, BuildFileSpec.class));
    assertThat(exception)
        .hasMessageThat()
        .contains("Property '" + fieldName + "' cannot contain null entries");
  }

  @Test
  @Parameters(value = {"volumes", "exposedPorts", "entrypoint", "cmd"})
  public void testBuildFileSpec_noEmptyEntries(String fieldName) {
    String data = "apiVersion: v1alpha1\n" + "kind: BuildFile\n" + fieldName + ": ['first', ' ']";

    Exception exception =
        assertThrows(
            JsonProcessingException.class, () -> mapper.readValue(data, BuildFileSpec.class));
    assertThat(exception)
        .hasMessageThat()
        .contains("Property '" + fieldName + "' cannot contain empty strings");
  }

  @Test
  @Parameters(value = {"volumes", "exposedPorts", "entrypoint", "cmd"})
  public void testBuildFileSpec_emptyListOkay(String fieldName) throws JsonProcessingException {
    String data = "apiVersion: v1alpha1\n" + "kind: BuildFile\n" + fieldName + ": []";

    assertThat(mapper.readValue(data, BuildFileSpec.class)).isNotNull();
  }

  @Test
  @Parameters(
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
  public void testBuildFileSpec_nullOkay(String fieldName) throws JsonProcessingException {
    String data = "apiVersion: v1alpha1\n" + "kind: BuildFile\n" + fieldName + ": null";

    assertThat(mapper.readValue(data, BuildFileSpec.class)).isNotNull();
  }

  @Test
  @Parameters(value = {"creationTime", "format", "user", "workingDirectory"})
  public void testBuildFileSpec_noEmptyValues(String fieldName) {
    String data = "apiVersion: v1alpha1\n" + "kind: BuildFile\n" + fieldName + ": ' '";
    Exception exception =
        assertThrows(
            JsonProcessingException.class, () -> mapper.readValue(data, BuildFileSpec.class));
    assertThat(exception)
        .hasMessageThat()
        .contains("Property '" + fieldName + "' cannot be an empty string");
  }

  @SuppressWarnings("unused")
  private static String[][] invalidMapEntries() {
    return new String[][] {
      {"environment", "  key: null", "' cannot contain null values"},
      {"environment", "  key: ' '", "' cannot contain empty string values"},
      {"environment", "  ' ': value", "' cannot contain empty string keys"},
      {"labels", "  key: null", "' cannot contain null values"},
      {"labels", "  key: ' '", "' cannot contain empty string values"},
      {"labels", "  ' ': value", "' cannot contain empty string keys"},
    };
  }

  @Test
  @Parameters(method = "invalidMapEntries")
  public void testBuildFileSpec_invalidMapEntries(
      String fieldName, String input, String errorMessage) {
    String data = "apiVersion: v1alpha1\n" + "kind: BuildFile\n" + fieldName + ":\n" + input;

    Exception exception =
        assertThrows(
            JsonProcessingException.class, () -> mapper.readValue(data, BuildFileSpec.class));
    assertThat(exception).hasMessageThat().contains("Property '" + fieldName + errorMessage);
  }

  // A quirk of our parser is that "null" keys are parsed as strings and not null, this test just
  // formalizes that behavior.
  @Test
  @Parameters(value = {"environment", "labels"})
  public void testBuildFileSpec_yamlNullKeysPass(String fieldName) throws JsonProcessingException {
    String data =
        "apiVersion: v1alpha1\n" + "kind: BuildFile\n" + fieldName + ":\n" + "  null: value";

    assertThat(mapper.readValue(data, BuildFileSpec.class)).isNotNull();
  }

  @Test
  @Parameters(value = {"environment", "labels"})
  public void testBuildFileSpec_emptyMapOkay(String fieldName) throws JsonProcessingException {
    String data = "apiVersion: v1alpha1\n" + "kind: BuildFile\n" + fieldName + ": {}";

    assertThat(mapper.readValue(data, BuildFileSpec.class)).isNotNull();
  }
}
