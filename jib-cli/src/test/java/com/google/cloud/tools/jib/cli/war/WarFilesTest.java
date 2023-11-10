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
import com.google.cloud.tools.jib.api.buildplan.Port;
import com.google.cloud.tools.jib.cli.CommonCliOptions;
import com.google.cloud.tools.jib.cli.CommonContainerConfigCliOptions;
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
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link WarFiles}. */
@RunWith(MockitoJUnitRunner.class)
public class WarFilesTest {

  @Mock private StandardWarExplodedProcessor mockStandardWarExplodedProcessor;
  @Mock private CommonCliOptions mockCommonCliOptions;
  @Mock private CommonContainerConfigCliOptions mockCommonContainerConfigCliOptions;
  @Mock private ConsoleLogger mockLogger;

  @Test
  public void testToJibContainerBuilder_explodedStandard_basicInfo()
      throws IOException, InvalidImageReferenceException {
    FileEntriesLayer layer =
        FileEntriesLayer.builder()
            .setName("classes")
            .addEntry(
                Paths.get("path/to/tempDirectory/WEB-INF/classes/class1.class"),
                AbsoluteUnixPath.get("/my/app/WEB-INF/classes/class1.class"))
            .build();
    when(mockStandardWarExplodedProcessor.createLayers()).thenReturn(Arrays.asList(layer));
    when(mockCommonContainerConfigCliOptions.isJettyBaseimage()).thenReturn(true);

    JibContainerBuilder containerBuilder =
        WarFiles.toJibContainerBuilder(
            mockStandardWarExplodedProcessor,
            mockCommonCliOptions,
            mockCommonContainerConfigCliOptions,
            mockLogger);
    ContainerBuildPlan buildPlan = containerBuilder.toContainerBuildPlan();

    assertThat(buildPlan.getBaseImage()).isEqualTo("jetty");
    assertThat(buildPlan.getEntrypoint())
        .containsExactly("java", "-jar", "/usr/local/jetty/start.jar", "--module=http,ee10-deploy")
        .inOrder();
    assertThat(buildPlan.getLayers()).hasSize(1);
    assertThat(buildPlan.getLayers().get(0).getName()).isEqualTo("classes");
    assertThat(((FileEntriesLayer) buildPlan.getLayers().get(0)).getEntries())
        .containsExactlyElementsIn(
            FileEntriesLayer.builder()
                .addEntry(
                    Paths.get("path/to/tempDirectory/WEB-INF/classes/class1.class"),
                    AbsoluteUnixPath.get("/my/app/WEB-INF/classes/class1.class"))
                .build()
                .getEntries());
  }

  @Test
  public void testToJibContainerBuilder_optionalParameters()
      throws IOException, InvalidImageReferenceException {
    when(mockCommonContainerConfigCliOptions.getFrom()).thenReturn(Optional.of("base-image"));
    when(mockCommonContainerConfigCliOptions.getExposedPorts())
        .thenReturn(ImmutableSet.of(Port.udp(123)));
    when(mockCommonContainerConfigCliOptions.getVolumes())
        .thenReturn(
            ImmutableSet.of(AbsoluteUnixPath.get("/volume1"), AbsoluteUnixPath.get("/volume2")));
    when(mockCommonContainerConfigCliOptions.getEnvironment())
        .thenReturn(ImmutableMap.of("key1", "value1"));
    when(mockCommonContainerConfigCliOptions.getLabels())
        .thenReturn(ImmutableMap.of("label", "mylabel"));
    when(mockCommonContainerConfigCliOptions.getUser()).thenReturn(Optional.of("customUser"));
    when(mockCommonContainerConfigCliOptions.getFormat()).thenReturn(Optional.of(ImageFormat.OCI));
    when(mockCommonContainerConfigCliOptions.getProgramArguments())
        .thenReturn(ImmutableList.of("arg1"));
    when(mockCommonContainerConfigCliOptions.getEntrypoint())
        .thenReturn(ImmutableList.of("custom", "entrypoint"));
    when(mockCommonContainerConfigCliOptions.getCreationTime())
        .thenReturn(Optional.of(Instant.ofEpochSecond(5)));

    JibContainerBuilder containerBuilder =
        WarFiles.toJibContainerBuilder(
            mockStandardWarExplodedProcessor,
            mockCommonCliOptions,
            mockCommonContainerConfigCliOptions,
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
    JibContainerBuilder containerBuilder =
        WarFiles.toJibContainerBuilder(
            mockStandardWarExplodedProcessor,
            mockCommonCliOptions,
            mockCommonContainerConfigCliOptions,
            mockLogger);
    ContainerBuildPlan buildPlan = containerBuilder.toContainerBuildPlan();

    assertThat(mockCommonContainerConfigCliOptions.isJettyBaseimage()).isFalse();
    assertThat(buildPlan.getEntrypoint()).isNull();
  }

  @Test
  public void testToJibContainerBuilder_noProgramArgumentsSpecified()
      throws IOException, InvalidImageReferenceException {
    JibContainerBuilder containerBuilder =
        WarFiles.toJibContainerBuilder(
            mockStandardWarExplodedProcessor,
            mockCommonCliOptions,
            mockCommonContainerConfigCliOptions,
            mockLogger);
    ContainerBuildPlan buildPlan = containerBuilder.toContainerBuildPlan();

    assertThat(buildPlan.getCmd()).isNull();
  }
}
