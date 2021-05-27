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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import com.google.cloud.tools.jib.api.InvalidImageReferenceException;
import com.google.cloud.tools.jib.api.JibContainerBuilder;
import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.buildplan.ContainerBuildPlan;
import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer;
import com.google.cloud.tools.jib.api.buildplan.ImageFormat;
import com.google.cloud.tools.jib.api.buildplan.Platform;
import com.google.cloud.tools.jib.api.buildplan.Port;
import com.google.cloud.tools.jib.cli.CommonCliOptions;
import com.google.cloud.tools.jib.cli.CommonContainerConfigCliOptions;
import com.google.cloud.tools.jib.cli.Jar;
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

/** Tests for {@link JarFiles}. */
@RunWith(MockitoJUnitRunner.class)
public class JarFilesTest {

  @Mock private StandardExplodedProcessor mockStandardExplodedProcessor;

  @Mock private StandardPackagedProcessor mockStandardPackagedProcessor;

  @Mock private SpringBootExplodedProcessor mockSpringBootExplodedProcessor;

  @Mock private SpringBootPackagedProcessor mockSpringBootPackagedProcessor;

  @Mock private Jar mockJarCommand;

  @Mock private CommonCliOptions mockCommonCliOptions;

  @Mock private CommonContainerConfigCliOptions mockCommonContainerConfigCliOptions;

  @Mock private ConsoleLogger mockLogger;

  @Test
  public void testToJibContainer_defaultBaseImage_java8()
      throws IOException, InvalidImageReferenceException {
    when(mockStandardExplodedProcessor.getJavaVersion()).thenReturn(8);
    JibContainerBuilder containerBuilder =
        JarFiles.toJibContainerBuilder(
            mockStandardExplodedProcessor,
            mockJarCommand,
            mockCommonCliOptions,
            mockCommonContainerConfigCliOptions,
            mockLogger);
    ContainerBuildPlan buildPlan = containerBuilder.toContainerBuildPlan();

    assertThat(buildPlan.getBaseImage()).isEqualTo("adoptopenjdk:8-jre");
  }

  @Test
  public void testToJibContainer_defaultBaseImage_java9()
      throws IOException, InvalidImageReferenceException {
    when(mockStandardExplodedProcessor.getJavaVersion()).thenReturn(9);
    JibContainerBuilder containerBuilder =
        JarFiles.toJibContainerBuilder(
            mockStandardExplodedProcessor,
            mockJarCommand,
            mockCommonCliOptions,
            mockCommonContainerConfigCliOptions,
            mockLogger);
    ContainerBuildPlan buildPlan = containerBuilder.toContainerBuildPlan();

    assertThat(buildPlan.getBaseImage()).isEqualTo("adoptopenjdk:11-jre");
  }

  @Test
  public void testToJibContainerBuilder_explodedStandard_basicInfo()
      throws IOException, InvalidImageReferenceException {
    when(mockStandardExplodedProcessor.getJavaVersion()).thenReturn(8);
    FileEntriesLayer layer =
        FileEntriesLayer.builder()
            .setName("classes")
            .addEntry(
                Paths.get("path/to/tempDirectory/class1.class"),
                AbsoluteUnixPath.get("/app/explodedJar/class1.class"))
            .build();
    when(mockStandardExplodedProcessor.createLayers()).thenReturn(Arrays.asList(layer));
    when(mockStandardExplodedProcessor.computeEntrypoint(anyList()))
        .thenReturn(
            ImmutableList.of("java", "-cp", "/app/explodedJar:/app/dependencies/*", "HelloWorld"));
    when(mockCommonContainerConfigCliOptions.getFrom()).thenReturn(Optional.empty());

    JibContainerBuilder containerBuilder =
        JarFiles.toJibContainerBuilder(
            mockStandardExplodedProcessor,
            mockJarCommand,
            mockCommonCliOptions,
            mockCommonContainerConfigCliOptions,
            mockLogger);
    ContainerBuildPlan buildPlan = containerBuilder.toContainerBuildPlan();

    assertThat(buildPlan.getBaseImage()).isEqualTo("adoptopenjdk:8-jre");
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
        .containsExactly("java", "-cp", "/app/explodedJar:/app/dependencies/*", "HelloWorld")
        .inOrder();
    assertThat(buildPlan.getLayers()).hasSize(1);
    assertThat(buildPlan.getLayers().get(0).getName()).isEqualTo("classes");
    assertThat(((FileEntriesLayer) buildPlan.getLayers().get(0)).getEntries())
        .containsExactlyElementsIn(
            FileEntriesLayer.builder()
                .addEntry(
                    Paths.get("path/to/tempDirectory/class1.class"),
                    AbsoluteUnixPath.get("/app/explodedJar/class1.class"))
                .build()
                .getEntries());
  }

  @Test
  public void testToJibContainerBuilder_packagedStandard_basicInfo()
      throws IOException, InvalidImageReferenceException {
    when(mockStandardPackagedProcessor.getJavaVersion()).thenReturn(8);
    FileEntriesLayer layer =
        FileEntriesLayer.builder()
            .setName("jar")
            .addEntry(
                Paths.get("path/to/standardJar.jar"), AbsoluteUnixPath.get("/app/standardJar.jar"))
            .build();
    when(mockStandardPackagedProcessor.createLayers()).thenReturn(Arrays.asList(layer));
    when(mockStandardPackagedProcessor.computeEntrypoint(anyList()))
        .thenReturn(ImmutableList.of("java", "-jar", "/app/standardJar.jar"));
    when(mockCommonContainerConfigCliOptions.getFrom()).thenReturn(Optional.empty());

    JibContainerBuilder containerBuilder =
        JarFiles.toJibContainerBuilder(
            mockStandardPackagedProcessor,
            mockJarCommand,
            mockCommonCliOptions,
            mockCommonContainerConfigCliOptions,
            mockLogger);
    ContainerBuildPlan buildPlan = containerBuilder.toContainerBuildPlan();

    assertThat(buildPlan.getBaseImage()).isEqualTo("adoptopenjdk:8-jre");
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
        .containsExactly("java", "-jar", "/app/standardJar.jar")
        .inOrder();
    assertThat(buildPlan.getLayers().get(0).getName()).isEqualTo("jar");
    assertThat(((FileEntriesLayer) buildPlan.getLayers().get(0)).getEntries())
        .isEqualTo(
            FileEntriesLayer.builder()
                .addEntry(
                    Paths.get("path/to/standardJar.jar"),
                    AbsoluteUnixPath.get("/app/standardJar.jar"))
                .build()
                .getEntries());
  }

  @Test
  public void testToJibContainerBuilder_explodedLayeredSpringBoot_basicInfo()
      throws IOException, InvalidImageReferenceException {
    when(mockSpringBootExplodedProcessor.getJavaVersion()).thenReturn(8);
    FileEntriesLayer layer =
        FileEntriesLayer.builder()
            .setName("classes")
            .addEntry(
                Paths.get("path/to/tempDirectory/BOOT-INF/classes/class1.class"),
                AbsoluteUnixPath.get("/app/BOOT-INF/classes/class1.class"))
            .build();
    when(mockCommonContainerConfigCliOptions.getFrom()).thenReturn(Optional.empty());
    when(mockSpringBootExplodedProcessor.createLayers()).thenReturn(Arrays.asList(layer));
    when(mockSpringBootExplodedProcessor.computeEntrypoint(anyList()))
        .thenReturn(
            ImmutableList.of("java", "-cp", "/app", "org.springframework.boot.loader.JarLauncher"));
    when(mockCommonContainerConfigCliOptions.getFrom()).thenReturn(Optional.empty());

    JibContainerBuilder containerBuilder =
        JarFiles.toJibContainerBuilder(
            mockSpringBootExplodedProcessor,
            mockJarCommand,
            mockCommonCliOptions,
            mockCommonContainerConfigCliOptions,
            mockLogger);
    ContainerBuildPlan buildPlan = containerBuilder.toContainerBuildPlan();

    assertThat(buildPlan.getBaseImage()).isEqualTo("adoptopenjdk:8-jre");
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
        .containsExactly("java", "-cp", "/app", "org.springframework.boot.loader.JarLauncher")
        .inOrder();
    assertThat(buildPlan.getLayers()).hasSize(1);
    assertThat(buildPlan.getLayers().get(0).getName()).isEqualTo("classes");
    assertThat(((FileEntriesLayer) buildPlan.getLayers().get(0)).getEntries())
        .containsExactlyElementsIn(
            FileEntriesLayer.builder()
                .addEntry(
                    Paths.get("path/to/tempDirectory/BOOT-INF/classes/class1.class"),
                    AbsoluteUnixPath.get("/app/BOOT-INF/classes/class1.class"))
                .build()
                .getEntries());
  }

  @Test
  public void testToJibContainerBuilder_packagedSpringBoot_basicInfo()
      throws IOException, InvalidImageReferenceException {
    when(mockSpringBootPackagedProcessor.getJavaVersion()).thenReturn(8);
    FileEntriesLayer layer =
        FileEntriesLayer.builder()
            .setName("jar")
            .addEntry(
                Paths.get("path/to/spring-boot.jar"), AbsoluteUnixPath.get("/app/spring-boot.jar"))
            .build();
    when(mockSpringBootPackagedProcessor.createLayers()).thenReturn(Arrays.asList(layer));
    when(mockSpringBootPackagedProcessor.computeEntrypoint(anyList()))
        .thenReturn(ImmutableList.of("java", "-jar", "/app/spring-boot.jar"));
    when(mockCommonContainerConfigCliOptions.getFrom()).thenReturn(Optional.empty());

    JibContainerBuilder containerBuilder =
        JarFiles.toJibContainerBuilder(
            mockSpringBootPackagedProcessor,
            mockJarCommand,
            mockCommonCliOptions,
            mockCommonContainerConfigCliOptions,
            mockLogger);
    ContainerBuildPlan buildPlan = containerBuilder.toContainerBuildPlan();

    assertThat(buildPlan.getBaseImage()).isEqualTo("adoptopenjdk:8-jre");
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
        .containsExactly("java", "-jar", "/app/spring-boot.jar")
        .inOrder();
    assertThat(buildPlan.getLayers()).hasSize(1);
    assertThat(buildPlan.getLayers().get(0).getName()).isEqualTo("jar");
    assertThat(((FileEntriesLayer) buildPlan.getLayers().get(0)).getEntries())
        .isEqualTo(
            FileEntriesLayer.builder()
                .addEntry(
                    Paths.get("path/to/spring-boot.jar"),
                    AbsoluteUnixPath.get("/app/spring-boot.jar"))
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
        JarFiles.toJibContainerBuilder(
            mockStandardExplodedProcessor,
            mockJarCommand,
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
}
