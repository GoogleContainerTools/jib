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
import java.nio.file.Path;
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

  @Mock private StandardExplodedModeProcessor mockStandardExplodedModeProcessor;

  @Mock private StandardPackagedModeProcessor mockStandardPackagedModeProcessor;

  @Mock private SpringBootExplodedModeProcessor mockSpringBootExplodedModeProcessor;

  @Mock private SpringBootPackagedModeProcessor mockSpringBootPackagedModeProcessor;

  @Test
  public void testToJibContainerBuilder_explodedStandard_basicInfo()
      throws IOException, InvalidImageReferenceException {
    Path standardJar = Paths.get("path/to/standardJar.jar");
    Path temporaryParentDirectory = Paths.get("path/to/tempDirectory");
    mockStandardExplodedModeProcessor.setTempDirectoryPath(temporaryParentDirectory);
    mockStandardExplodedModeProcessor.setJarPath(standardJar);
    FileEntriesLayer layer =
        FileEntriesLayer.builder()
            .setName("classes")
            .addEntry(
                temporaryParentDirectory.resolve("class1.class"),
                AbsoluteUnixPath.get("/app/explodedJar/class1.class"))
            .build();
    Mockito.when(mockStandardExplodedModeProcessor.createLayers()).thenReturn(Arrays.asList(layer));
    Mockito.when(mockStandardExplodedModeProcessor.computeEntrypoint())
        .thenReturn(
            ImmutableList.of("java", "-cp", "/app/explodedJar:/app/dependencies/*", "HelloWorld"));

    JibContainerBuilder containerBuilder =
        JarFiles.toJibContainerBuilder(mockStandardExplodedModeProcessor);
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
    Path standardJar = Paths.get("path/to/standardJar.jar");
    FileEntriesLayer layer =
        FileEntriesLayer.builder()
            .setName("jar")
            .addEntry(standardJar, AbsoluteUnixPath.get("/app/standardJar.jar"))
            .build();
    mockStandardPackagedModeProcessor.setJarPath(standardJar);
    Mockito.when(mockStandardPackagedModeProcessor.createLayers()).thenReturn(Arrays.asList(layer));
    Mockito.when(mockStandardPackagedModeProcessor.computeEntrypoint())
        .thenReturn(ImmutableList.of("java", "-jar", "/app/standardJar.jar"));

    JibContainerBuilder containerBuilder =
        JarFiles.toJibContainerBuilder(mockStandardPackagedModeProcessor);
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
    Path springBootJar = Paths.get("path/to/spring-boot.jar");
    Path temporaryParentDirectory = Paths.get("path/to/tempDirectory");
    mockSpringBootExplodedModeProcessor.setTempDirectoryPath(temporaryParentDirectory);
    mockSpringBootExplodedModeProcessor.setJarPath(springBootJar);
    FileEntriesLayer layer =
        FileEntriesLayer.builder()
            .setName("classes")
            .addEntry(
                temporaryParentDirectory.resolve("BOOT-INF/classes/class1.class"),
                AbsoluteUnixPath.get("/app/BOOT-INF/classes/class1.class"))
            .build();
    Mockito.when(mockSpringBootExplodedModeProcessor.createLayers())
        .thenReturn(Arrays.asList(layer));
    Mockito.when(mockSpringBootExplodedModeProcessor.computeEntrypoint())
        .thenReturn(
            ImmutableList.of("java", "-cp", "/app", "org.springframework.boot.loader.JarLauncher"));

    JibContainerBuilder containerBuilder =
        JarFiles.toJibContainerBuilder(mockSpringBootExplodedModeProcessor);
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
    Path springBootJar = Paths.get("path/to/spring-boot.jar");
    FileEntriesLayer layer =
        FileEntriesLayer.builder()
            .setName("jar")
            .addEntry(springBootJar, AbsoluteUnixPath.get("/app/spring-boot.jar"))
            .build();
    Mockito.when(mockSpringBootPackagedModeProcessor.createLayers())
        .thenReturn(Arrays.asList(layer));
    Mockito.when(mockSpringBootPackagedModeProcessor.computeEntrypoint())
        .thenReturn(ImmutableList.of("java", "-jar", "/app/spring-boot.jar"));
    mockSpringBootPackagedModeProcessor.setJarPath(springBootJar);

    JibContainerBuilder containerBuilder =
        JarFiles.toJibContainerBuilder(mockSpringBootPackagedModeProcessor);
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
