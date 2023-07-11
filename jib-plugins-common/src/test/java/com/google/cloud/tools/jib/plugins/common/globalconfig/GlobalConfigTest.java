/*
 * Copyright 2021 Google LLC.
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

package com.google.cloud.tools.jib.plugins.common.globalconfig;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.cloud.tools.jib.plugins.common.PropertyNames;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.properties.SystemProperties;

/** Tests for {@link GlobalConfig}. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(SystemStubsExtension.class)
class GlobalConfigTest {

  @SystemStub
  @SuppressWarnings("unused")
  private SystemProperties restoreSystemProperties;

  @TempDir public Path temporaryFolder;

  private Path configDir;

  @BeforeEach
  void setUpBeforeEach() {
    configDir = temporaryFolder;
  }

  @Test
  void testReadConfig_default() throws IOException, InvalidGlobalConfigException {
    GlobalConfig globalConfig = GlobalConfig.readConfig(configDir);

    assertThat(globalConfig.isDisableUpdateCheck()).isFalse();
    assertThat(globalConfig.getRegistryMirrors()).isEmpty();
  }

  @Test
  void testReadConfig_newConfigCreated() throws IOException, InvalidGlobalConfigException {
    GlobalConfig.readConfig(configDir);
    String configJson =
        new String(Files.readAllBytes(configDir.resolve("config.json")), StandardCharsets.UTF_8);
    assertThat(configJson).isEqualTo("{\"disableUpdateCheck\":false,\"registryMirrors\":[]}");
  }

  @Test
  void testReadConfig_emptyJson() throws IOException, InvalidGlobalConfigException {
    Files.write(configDir.resolve("config.json"), "{}".getBytes(StandardCharsets.UTF_8));
    GlobalConfig globalConfig = GlobalConfig.readConfig(configDir);

    assertThat(globalConfig.isDisableUpdateCheck()).isFalse();
    assertThat(globalConfig.getRegistryMirrors()).isEmpty();
  }

  @Test
  void testReadConfig() throws IOException, InvalidGlobalConfigException {
    String json =
        "{\"disableUpdateCheck\":true, \"registryMirrors\":["
            + "{ \"registry\": \"registry-1.docker.io\","
            + "  \"mirrors\": [\"mirror.gcr.io\", \"localhost:5000\"] },"
            + "{ \"registry\": \"another.registry\", \"mirrors\": [\"another.mirror\"] }"
            + "]}";
    Files.write(configDir.resolve("config.json"), json.getBytes(StandardCharsets.UTF_8));

    GlobalConfig globalConfig = GlobalConfig.readConfig(configDir);
    assertThat(globalConfig.isDisableUpdateCheck()).isTrue();
    assertThat(globalConfig.getRegistryMirrors())
        .containsExactly(
            "registry-1.docker.io",
            "mirror.gcr.io",
            "registry-1.docker.io",
            "localhost:5000",
            "another.registry",
            "another.mirror");
  }

  @Test
  void testReadConfig_systemProperties() throws IOException, InvalidGlobalConfigException {
    Files.write(
        configDir.resolve("config.json"),
        "{\"disableUpdateCheck\":false}".getBytes(StandardCharsets.UTF_8));

    GlobalConfig globalConfig = GlobalConfig.readConfig(configDir);
    System.setProperty(PropertyNames.DISABLE_UPDATE_CHECKS, "true");
    assertThat(globalConfig.isDisableUpdateCheck()).isTrue();
  }

  @Test
  void testReadConfig_emptyFile() throws IOException {
    new File(temporaryFolder.toFile(), "config.json").createNewFile();

    IOException exception =
        assertThrows(IOException.class, () -> GlobalConfig.readConfig(configDir));
    assertThat(exception)
        .hasMessageThat()
        .startsWith(
            "Failed to create, open, or parse global Jib config file; see "
                + "https://github.com/GoogleContainerTools/jib/blob/master/docs/faq.md#where-is-the-global-jib-configuration-file-and-how-i-can-configure-it "
                + "to fix or you may need to delete");
    assertThat(exception).hasMessageThat().endsWith(File.separator + "config.json");
  }

  @Test
  void testReadConfig_corrupted() throws IOException {
    Files.write(
        configDir.resolve("config.json"), "corrupt config".getBytes(StandardCharsets.UTF_8));
    IOException exception =
        assertThrows(IOException.class, () -> GlobalConfig.readConfig(configDir));
    assertThat(exception)
        .hasMessageThat()
        .startsWith(
            "Failed to create, open, or parse global Jib config file; see "
                + "https://github.com/GoogleContainerTools/jib/blob/master/docs/faq.md#where-is-the-global-jib-configuration-file-and-how-i-can-configure-it "
                + "to fix or you may need to delete ");
    assertThat(exception).hasMessageThat().endsWith(File.separator + "config.json");
  }

  @Test
  void testReadConfig_missingRegistry() throws IOException {
    String json = "{\"registryMirrors\":[{\"mirrors\":[\"mirror.gcr.io\"]}]}";
    Files.write(configDir.resolve("config.json"), json.getBytes(StandardCharsets.UTF_8));
    InvalidGlobalConfigException exception =
        assertThrows(InvalidGlobalConfigException.class, () -> GlobalConfig.readConfig(configDir));
    assertThat(exception)
        .hasMessageThat()
        .startsWith(
            "'registryMirrors.registry' property is missing; see "
                + "https://github.com/GoogleContainerTools/jib/blob/master/docs/faq.md#where-is-the-global-jib-configuration-file-and-how-i-can-configure-it "
                + "to fix or you may need to delete ");
  }

  @Test
  void testReadConfig_missingMirrors() throws IOException {
    String json = "{\"registryMirrors\":[{\"registry\": \"registry\"}]}";
    Files.write(configDir.resolve("config.json"), json.getBytes(StandardCharsets.UTF_8));
    InvalidGlobalConfigException exception =
        assertThrows(InvalidGlobalConfigException.class, () -> GlobalConfig.readConfig(configDir));
    assertThat(exception)
        .hasMessageThat()
        .startsWith(
            "'registryMirrors.mirrors' property is missing; see "
                + "https://github.com/GoogleContainerTools/jib/blob/master/docs/faq.md#where-is-the-global-jib-configuration-file-and-how-i-can-configure-it "
                + "to fix or you may need to delete");
  }
}
