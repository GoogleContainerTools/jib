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

package com.google.cloud.tools.jib.cli.jar;

import static com.google.common.truth.Truth.assertThat;

import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer;
import com.google.cloud.tools.jib.api.buildplan.FileEntry;
import com.google.cloud.tools.jib.api.buildplan.FilePermissions;
import com.google.cloud.tools.jib.api.buildplan.ImageFormat;
import com.google.cloud.tools.jib.api.buildplan.Port;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Arrays;
import org.junit.Test;

/** Tests for {@link JarInfoBuilderTest}. */
public class JarInfoBuilderTest {

  @Test
  public void testDefaults() {
    JarInfoBuilder jarInfo = JarInfoBuilder.builder().build();
    assertThat(jarInfo.getBaseImage()).isEqualTo("scratch");
    assertThat(jarInfo.getFormat()).isEqualTo(ImageFormat.Docker);
    assertThat(jarInfo.getEnvironment()).isEmpty();
    assertThat(jarInfo.getVolumes()).isEmpty();
    assertThat(jarInfo.getLabels()).isEmpty();
    assertThat(jarInfo.getExposedPorts()).isEmpty();
    assertThat(jarInfo.getUser()).isNull();
    assertThat(jarInfo.getEntrypoint()).isNull();
    assertThat(jarInfo.getProgramArguments()).isNull();
    assertThat(jarInfo.getLayers()).isEmpty();
  }

  @Test
  public void testBuilder() {
    JarInfoBuilder sampleJarInfo = createSampleJarInfo();

    assertThat(sampleJarInfo.getBaseImage()).isEqualTo("base/image");
    assertThat(sampleJarInfo.getFormat()).isEqualTo(ImageFormat.OCI);
    assertThat(sampleJarInfo.getEnvironment()).isEqualTo(ImmutableMap.of("env", "var"));
    assertThat(sampleJarInfo.getVolumes())
        .isEqualTo(ImmutableSet.of(AbsoluteUnixPath.get("/mnt/foo"), AbsoluteUnixPath.get("/bar")));
    assertThat(sampleJarInfo.getLabels())
        .isEqualTo(ImmutableMap.of("com.example.label", "labelValue"));
    assertThat(sampleJarInfo.getUser()).isEqualTo(":");
    assertThat(sampleJarInfo.getEntrypoint()).isEqualTo(Arrays.asList("java", "-jar", "jarName"));
    assertThat(sampleJarInfo.getProgramArguments())
        .isEqualTo(Arrays.asList("argument1", "argument2"));

    assertThat(sampleJarInfo.getLayers().size()).isEqualTo(2);
    assertThat(sampleJarInfo.getLayers().get(0)).isInstanceOf(FileEntriesLayer.class);
    assertThat(sampleJarInfo.getLayers().get(1)).isInstanceOf(FileEntriesLayer.class);
    assertThat(((FileEntriesLayer) sampleJarInfo.getLayers().get(0)).getEntries())
        .isEqualTo(
            Arrays.asList(
                new FileEntry(
                    Paths.get("/src/file/foo"),
                    AbsoluteUnixPath.get("/path/in/container"),
                    FilePermissions.fromOctalString("644"),
                    Instant.ofEpochSecond(1))));
    assertThat(((FileEntriesLayer) sampleJarInfo.getLayers().get(1)).getEntries())
        .isEqualTo(
            Arrays.asList(
                new FileEntry(
                    Paths.get("/src/file/bar"),
                    AbsoluteUnixPath.get("/path/in/container"),
                    FilePermissions.fromOctalString("644"),
                    Instant.ofEpochSecond(1))));
  }

  private JarInfoBuilder createSampleJarInfo() {
    FileEntriesLayer layer1 =
        FileEntriesLayer.builder()
            .addEntry(Paths.get("/src/file/foo"), AbsoluteUnixPath.get("/path/in/container"))
            .build();
    FileEntriesLayer layer2 =
        FileEntriesLayer.builder()
            .addEntry(Paths.get("/src/file/bar"), AbsoluteUnixPath.get("/path/in/container"))
            .build();

    return JarInfoBuilder.builder()
        .setBaseImage("base/image")
        .setFormat(ImageFormat.OCI)
        .setEnvironment(ImmutableMap.of("env", "var"))
        .setVolumes(ImmutableSet.of(AbsoluteUnixPath.get("/mnt/foo"), AbsoluteUnixPath.get("/bar")))
        .setLabels(ImmutableMap.of("com.example.label", "labelValue"))
        .setExposedPorts(ImmutableSet.of(Port.tcp(443)))
        .setLayers(Arrays.asList(layer1, layer2))
        .setUser(":")
        .setEntrypoint(Arrays.asList("java", "-jar", "jarName"))
        .setProgramArguments(Arrays.asList("argument1", "argument2"))
        .build();
  }
}
