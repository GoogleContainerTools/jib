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

import com.google.cloud.tools.jib.api.InvalidImageReferenceException;
import com.google.cloud.tools.jib.api.JibContainerBuilder;
import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.buildplan.ContainerBuildPlan;
import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer;
import com.google.cloud.tools.jib.api.buildplan.ImageFormat;
import com.google.cloud.tools.jib.api.buildplan.Platform;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link JarFiles}. */
@RunWith(MockitoJUnitRunner.class)
public class JarFilesTest {

  @Mock private StandardExplodedProcessor mockStandardExplodedProcessor;

  @Mock private StandardPackagedProcessor mockStandardPackagedProcessor;

  @Mock private SpringBootExplodedProcessor mockSpringBootExplodedProcessor;

  @Mock private SpringBootPackagedProcessor mockSpringBootPackagedProcessor;

  @Test
  public void testToJibContainerBuilder_explodedStandard_basicInfo()
      throws IOException, InvalidImageReferenceException {
    FileEntriesLayer layer =
        FileEntriesLayer.builder()
            .setName("classes")
            .addEntry(
                Paths.get("path/to/tempDirectory/class1.class"),
                AbsoluteUnixPath.get("/app/explodedJar/class1.class"))
            .build();
    Mockito.when(mockStandardExplodedProcessor.createLayers()).thenReturn(Arrays.asList(layer));
    Mockito.when(mockStandardExplodedProcessor.computeEntrypoint())
        .thenReturn(
            ImmutableList.of("java", "-cp", "/app/explodedJar:/app/dependencies/*", "HelloWorld"));

    JibContainerBuilder containerBuilder =
        JarFiles.toJibContainerBuilder(mockStandardExplodedProcessor);
    ContainerBuildPlan buildPlan = containerBuilder.toContainerBuildPlan();

    assertThat(buildPlan.getBaseImage()).isEqualTo("gcr.io/distroless/java");
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
        .isEqualTo(
            ImmutableList.of("java", "-cp", "/app/explodedJar:/app/dependencies/*", "HelloWorld"));
    assertThat(buildPlan.getLayers().size()).isEqualTo(1);
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
    FileEntriesLayer layer =
        FileEntriesLayer.builder()
            .setName("jar")
            .addEntry(
                Paths.get("path/to/standardJar.jar"), AbsoluteUnixPath.get("/app/standardJar.jar"))
            .build();
    Mockito.when(mockStandardPackagedProcessor.createLayers()).thenReturn(Arrays.asList(layer));
    Mockito.when(mockStandardPackagedProcessor.computeEntrypoint())
        .thenReturn(ImmutableList.of("java", "-jar", "/app/standardJar.jar"));

    JibContainerBuilder containerBuilder =
        JarFiles.toJibContainerBuilder(mockStandardPackagedProcessor);
    ContainerBuildPlan buildPlan = containerBuilder.toContainerBuildPlan();

    assertThat(buildPlan.getBaseImage()).isEqualTo("gcr.io/distroless/java");
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
        .isEqualTo(ImmutableList.of("java", "-jar", "/app/standardJar.jar"));
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
    FileEntriesLayer layer =
        FileEntriesLayer.builder()
            .setName("classes")
            .addEntry(
                Paths.get("path/to/tempDirectory/BOOT-INF/classes/class1.class"),
                AbsoluteUnixPath.get("/app/BOOT-INF/classes/class1.class"))
            .build();
    Mockito.when(mockSpringBootExplodedProcessor.createLayers()).thenReturn(Arrays.asList(layer));
    Mockito.when(mockSpringBootExplodedProcessor.computeEntrypoint())
        .thenReturn(
            ImmutableList.of("java", "-cp", "/app", "org.springframework.boot.loader.JarLauncher"));

    JibContainerBuilder containerBuilder =
        JarFiles.toJibContainerBuilder(mockSpringBootExplodedProcessor);
    ContainerBuildPlan buildPlan = containerBuilder.toContainerBuildPlan();

    assertThat(buildPlan.getBaseImage()).isEqualTo("gcr.io/distroless/java");
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
        .isEqualTo(
            ImmutableList.of("java", "-cp", "/app", "org.springframework.boot.loader.JarLauncher"));
    assertThat(buildPlan.getLayers().size()).isEqualTo(1);
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
    FileEntriesLayer layer =
        FileEntriesLayer.builder()
            .setName("jar")
            .addEntry(
                Paths.get("path/to/spring-boot.jar"), AbsoluteUnixPath.get("/app/spring-boot.jar"))
            .build();
    Mockito.when(mockSpringBootPackagedProcessor.createLayers()).thenReturn(Arrays.asList(layer));
    Mockito.when(mockSpringBootPackagedProcessor.computeEntrypoint())
        .thenReturn(ImmutableList.of("java", "-jar", "/app/spring-boot.jar"));

    JibContainerBuilder containerBuilder =
        JarFiles.toJibContainerBuilder(mockSpringBootPackagedProcessor);
    ContainerBuildPlan buildPlan = containerBuilder.toContainerBuildPlan();

    assertThat(buildPlan.getBaseImage()).isEqualTo("gcr.io/distroless/java");
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
        .isEqualTo(ImmutableList.of("java", "-jar", "/app/spring-boot.jar"));
    assertThat(buildPlan.getLayers().size()).isEqualTo(1);
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
}
