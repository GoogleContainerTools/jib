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
import java.util.Arrays;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.RestoreSystemProperties;
import org.junit.rules.TemporaryFolder;

/** Tests for {@link GlobalConfig}. */
public class GlobalConfigTest {

  @Rule public final RestoreSystemProperties systemPropertyRestorer = new RestoreSystemProperties();
  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  private Path configDir;

  @Before
  public void setUp() {
    configDir = temporaryFolder.getRoot().toPath();
  }

  @Test
  public void testReadConfig_default() throws IOException, InvalidGlobalConfigException {
    GlobalConfig globalConfig = GlobalConfig.readConfig(configDir);

    assertThat(globalConfig.isDisableUpdateCheck()).isFalse();
    assertThat(globalConfig.getRegistryMirrors()).isEmpty();
  }

  @Test
  public void testReadConfig_newConfigCreated() throws IOException, InvalidGlobalConfigException {
    GlobalConfig.readConfig(configDir);
    String configJson =
        new String(Files.readAllBytes(configDir.resolve("config.json")), StandardCharsets.UTF_8);
    assertThat(configJson).isEqualTo("{\"disableUpdateCheck\":false,\"registryMirrors\":[]}");
  }

  @Test
  public void testReadConfig_emptyJson() throws IOException, InvalidGlobalConfigException {
    Files.write(configDir.resolve("config.json"), "{}".getBytes(StandardCharsets.UTF_8));
    GlobalConfig globalConfig = GlobalConfig.readConfig(configDir);

    assertThat(globalConfig.isDisableUpdateCheck()).isFalse();
    assertThat(globalConfig.getRegistryMirrors()).isEmpty();
  }

  @Test
  public void testReadConfig() throws IOException, InvalidGlobalConfigException {
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
            Arrays.asList("mirror.gcr.io", "localhost:5000"),
            "another.registry",
            Arrays.asList("another.mirror"));
  }

  @Test
  public void testReadConfig_systemProperties() throws IOException, InvalidGlobalConfigException {
    Files.write(
        configDir.resolve("config.json"),
        "{\"disableUpdateCheck\":false}".getBytes(StandardCharsets.UTF_8));

    GlobalConfig globalConfig = GlobalConfig.readConfig(configDir);
    System.setProperty(PropertyNames.DISABLE_UPDATE_CHECKS, "true");
    assertThat(globalConfig.isDisableUpdateCheck()).isTrue();
  }

  @Test
  public void testReadConfig_emptyFile() throws IOException {
    temporaryFolder.newFile("config.json");
    IOException exception =
        assertThrows(IOException.class, () -> GlobalConfig.readConfig(configDir));
    assertThat(exception)
        .hasMessageThat()
        .startsWith(
            "Failed to open or parse global Jib config file; see "
                + "https://github.com/GoogleContainerTools/jib/tree/master/jib-maven-plugin#global-jib-configuration "
                + "to fix or you may need to fix or delete");
    assertThat(exception).hasMessageThat().endsWith(File.separator + "config.json");
  }

  @Test
  public void testReadConfig_corrupted() throws IOException {
    Files.write(
        configDir.resolve("config.json"), "corrupt config".getBytes(StandardCharsets.UTF_8));
    IOException exception =
        assertThrows(IOException.class, () -> GlobalConfig.readConfig(configDir));
    assertThat(exception)
        .hasMessageThat()
        .startsWith(
            "Failed to open or parse global Jib config file; see "
                + "https://github.com/GoogleContainerTools/jib/tree/master/jib-maven-plugin#global-jib-configuration "
                + "to fix or you may need to fix or delete");
    assertThat(exception).hasMessageThat().endsWith(File.separator + "config.json");
  }
}
