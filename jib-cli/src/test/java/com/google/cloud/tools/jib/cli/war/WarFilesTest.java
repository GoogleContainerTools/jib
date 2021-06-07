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

package com.google.cloud.tools.jib.cli.war;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

import com.google.cloud.tools.jib.api.InvalidImageReferenceException;
import com.google.cloud.tools.jib.api.JibContainerBuilder;
import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.buildplan.ContainerBuildPlan;
import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer;
import com.google.cloud.tools.jib.api.buildplan.ImageFormat;
import com.google.cloud.tools.jib.api.buildplan.Platform;
import com.google.cloud.tools.jib.api.buildplan.Port;
import com.google.cloud.tools.jib.cli.CommonArtifactCommandOptions;
import com.google.cloud.tools.jib.cli.CommonCliOptions;
import com.google.cloud.tools.jib.plugins.common.logging.ConsoleLogger;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link WarFiles}. */
@RunWith(MockitoJUnitRunner.class)
public class WarFilesTest {

  @Mock private StandardWarExplodedProcessor mockStandardWarExplodedProcessor;
  @Mock private CommonCliOptions mockCommonCliOptions;
  @Mock private CommonArtifactCommandOptions mockCommonArtifactCommandOptions;
  @Mock private ConsoleLogger mockLogger;

  @Test
  public void testToJibContainerBuilder_explodedStandard_basicInfo()
      throws IOException, InvalidImageReferenceException {
    FileEntriesLayer layer =
        FileEntriesLayer.builder()
            .setName("classes")
            .addEntry(
                Paths.get("path/to/tempDirectory/class1.class"),
                AbsoluteUnixPath.get("/my/app/class1.class"))
            .build();
    when(mockStandardWarExplodedProcessor.createLayers()).thenReturn(Arrays.asList(layer));
    when(mockStandardWarExplodedProcessor.computeEntrypoint(ArgumentMatchers.anyList()))
        .thenReturn(ImmutableList.of("java", "-jar", "/usr/local/jetty/start.jar"));

    JibContainerBuilder containerBuilder =
        WarFiles.toJibContainerBuilder(
            mockStandardWarExplodedProcessor,
            mockCommonCliOptions,
            mockCommonArtifactCommandOptions,
            mockLogger);
    ContainerBuildPlan buildPlan = containerBuilder.toContainerBuildPlan();

    assertThat(buildPlan.getBaseImage()).isEqualTo("jetty");
    assertThat(buildPlan.getPlatforms()).isEqualTo(ImmutableSet.of(new Platform("amd64", "linux")));
    assertThat(buildPlan.getCreationTime()).isEqualTo(Instant.EPOCH);
    assertThat(buildPlan.getFormat()).isEqualTo(ImageFormat.Docker);
    assertThat(buildPlan.getEnvironment()).isEmpty();
    assertThat(buildPlan.getLabels()).isEmpty();
    assertThat(buildPlan.getVolumes()).isEmpty();
    assertThat(buildPlan.getExposedPorts()).isEmpty();
    assertThat(buildPlan.getUser()).isNull();
    assertThat(buildPlan.getWorkingDirectory()).isNull();
    assertThat(buildPlan.getEntrypoint())
        .isEqualTo(ImmutableList.of("java", "-jar", "/usr/local/jetty/start.jar"));
    assertThat(buildPlan.getLayers().size()).isEqualTo(1);
    assertThat(buildPlan.getLayers().get(0).getName()).isEqualTo("classes");
    assertThat(((FileEntriesLayer) buildPlan.getLayers().get(0)).getEntries())
        .containsExactlyElementsIn(
            FileEntriesLayer.builder()
                .addEntry(
                    Paths.get("path/to/tempDirectory/class1.class"),
                    AbsoluteUnixPath.get("/my/app/class1.class"))
                .build()
                .getEntries());
  }

  @Test
  public void testToJibContainerBuilder_optionalParameters()
      throws IOException, InvalidImageReferenceException {
    when(mockCommonArtifactCommandOptions.getFrom()).thenReturn(Optional.of("base-image"));
    when(mockCommonArtifactCommandOptions.getExposedPorts())
        .thenReturn(ImmutableSet.of(Port.udp(123)));
    when(mockCommonArtifactCommandOptions.getVolumes())
        .thenReturn(
            ImmutableSet.of(AbsoluteUnixPath.get("/volume1"), AbsoluteUnixPath.get("/volume2")));
    when(mockCommonArtifactCommandOptions.getEnvironment())
        .thenReturn(ImmutableMap.of("key1", "value1"));
    when(mockCommonArtifactCommandOptions.getLabels())
        .thenReturn(ImmutableMap.of("label", "mylabel"));
    when(mockCommonArtifactCommandOptions.getUser()).thenReturn(Optional.of("customUser"));
    when(mockCommonArtifactCommandOptions.getFormat()).thenReturn(Optional.of(ImageFormat.OCI));
    when(mockCommonArtifactCommandOptions.getProgramArguments())
        .thenReturn(ImmutableList.of("arg1"));
    when(mockCommonArtifactCommandOptions.getEntrypoint())
        .thenReturn(ImmutableList.of("custom", "entrypoint"));
    when(mockCommonArtifactCommandOptions.getCreationTime())
        .thenReturn(Optional.of(Instant.ofEpochSecond(5)));

    JibContainerBuilder containerBuilder =
        WarFiles.toJibContainerBuilder(
            mockStandardWarExplodedProcessor,
            mockCommonCliOptions,
            mockCommonArtifactCommandOptions,
            mockLogger);
    ContainerBuildPlan buildPlan = containerBuilder.toContainerBuildPlan();

    assertThat(buildPlan.getBaseImage()).isEqualTo("base-image");
    assertThat(buildPlan.getExposedPorts()).isEqualTo(ImmutableSet.of(Port.udp(123)));
    assertThat(buildPlan.getVolumes())
        .isEqualTo(
            ImmutableSet.of(AbsoluteUnixPath.get("/volume1"), AbsoluteUnixPath.get("/volume2")));
    assertThat(buildPlan.getEnvironment()).isEqualTo(ImmutableMap.of("key1", "value1"));
    assertThat(buildPlan.getLabels()).isEqualTo(ImmutableMap.of("label", "mylabel"));
    assertThat(buildPlan.getUser()).isEqualTo("customUser");
    assertThat(buildPlan.getFormat()).isEqualTo(ImageFormat.OCI);
    assertThat(buildPlan.getCmd()).isEqualTo(ImmutableList.of("arg1"));
    assertThat(buildPlan.getEntrypoint()).isEqualTo(ImmutableList.of("custom", "entrypoint"));
    assertThat(buildPlan.getCreationTime()).isEqualTo(Instant.ofEpochSecond(5));
  }

  @Test
  public void testToJibContainerBuilder_nonJettyBaseImageSpecifiedAndNoEntrypoint()
      throws IOException, InvalidImageReferenceException {
    when(mockCommonArtifactCommandOptions.getFrom()).thenReturn(Optional.of("base-image"));

    JibContainerBuilder containerBuilder =
        WarFiles.toJibContainerBuilder(
            mockStandardWarExplodedProcessor,
            mockCommonCliOptions,
            mockCommonArtifactCommandOptions,
            mockLogger);
    ContainerBuildPlan buildPlan = containerBuilder.toContainerBuildPlan();

    assertThat(buildPlan.getBaseImage()).isEqualTo("base-image");
    assertThat(buildPlan.getEntrypoint()).isNull();
  }

  @Test
  public void testToJibContainerBuilder_jettyBaseImageSpecified_usesDefaultEntrypoint()
      throws IOException, InvalidImageReferenceException {
    when(mockCommonArtifactCommandOptions.getFrom()).thenReturn(Optional.of("jetty"));
    when(mockStandardWarExplodedProcessor.computeEntrypoint(ArgumentMatchers.anyList()))
        .thenReturn(ImmutableList.of("java", "-jar", "/usr/local/jetty/start.jar"));

    JibContainerBuilder containerBuilder =
        WarFiles.toJibContainerBuilder(
            mockStandardWarExplodedProcessor,
            mockCommonCliOptions,
            mockCommonArtifactCommandOptions,
            mockLogger);
    ContainerBuildPlan buildPlan = containerBuilder.toContainerBuildPlan();

    assertThat(buildPlan.getBaseImage()).isEqualTo("jetty");
    assertThat(buildPlan.getEntrypoint())
        .containsExactly("java", "-jar", "/usr/local/jetty/start.jar")
        .inOrder();
  }
}
