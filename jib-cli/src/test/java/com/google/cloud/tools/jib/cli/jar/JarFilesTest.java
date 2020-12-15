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
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class JarFilesTest {
  private static final String SIMPLE_STANDARD_JAR = "jar/standard/basicStandardJar.jar";
  private static final String SIMPLE_SPRING_BOOT_LAYERED =
      "jar/springboot/springboot_simple_layered.jar";
  private static final String SIMPLE_SPRING_BOOT_NON_LAYERED =
      "jar/springboot/springboot_simple_notLayered.jar";
  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void testToJibContainerBuilder_explodedStandard_basicInfo()
      throws IOException, URISyntaxException, InvalidImageReferenceException {
    Path standardJar = Paths.get(Resources.getResource(SIMPLE_STANDARD_JAR).toURI());
    Path temporaryParentDirectory = temporaryFolder.getRoot().toPath();
    JibContainerBuilder containerBuilder =
        JarFiles.toJibContainerBuilder(
            standardJar, temporaryParentDirectory, ProcessingMode.exploded);
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
    assertThat(buildPlan.getLayers().size()).isEqualTo(3);
    assertThat(buildPlan.getLayers().get(0).getName()).isEqualTo("dependencies");
    assertThat(((FileEntriesLayer) buildPlan.getLayers().get(0)).getEntries())
        .isEqualTo(
            FileEntriesLayer.builder()
                .addEntry(
                    standardJar.getParent().resolve("dependency1"),
                    AbsoluteUnixPath.get("/app/dependencies/dependency1"))
                .build()
                .getEntries());
    assertThat(buildPlan.getLayers().get(1).getName()).isEqualTo("resources");
    assertThat(((FileEntriesLayer) buildPlan.getLayers().get(1)).getEntries())
        .containsExactlyElementsIn(
            FileEntriesLayer.builder()
                .addEntry(
                    temporaryParentDirectory.resolve("META-INF/MANIFEST.MF"),
                    AbsoluteUnixPath.get("/app/explodedJar/META-INF/MANIFEST.MF"))
                .addEntry(
                    temporaryParentDirectory.resolve("resource1.txt"),
                    AbsoluteUnixPath.get("/app/explodedJar/resource1.txt"))
                .build()
                .getEntries());
    assertThat(buildPlan.getLayers().get(2).getName()).isEqualTo("classes");
    assertThat(((FileEntriesLayer) buildPlan.getLayers().get(2)).getEntries())
        .containsExactlyElementsIn(
            FileEntriesLayer.builder()
                .addEntry(
                    temporaryParentDirectory.resolve("class1.class"),
                    AbsoluteUnixPath.get("/app/explodedJar/class1.class"))
                .addEntry(
                    temporaryParentDirectory.resolve("directory1/class2.class"),
                    AbsoluteUnixPath.get("/app/explodedJar/directory1/class2.class"))
                .build()
                .getEntries());
  }

  @Test
  public void testToJibContainerBuilder_packagedStandard_basicInfo()
      throws IOException, URISyntaxException, InvalidImageReferenceException {
    Path standardJar = Paths.get(Resources.getResource(SIMPLE_STANDARD_JAR).toURI());
    Path temporaryParentDirectory = temporaryFolder.getRoot().toPath();
    JibContainerBuilder containerBuilder =
        JarFiles.toJibContainerBuilder(
            standardJar, temporaryParentDirectory, ProcessingMode.packaged);
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
        .isEqualTo(ImmutableList.of("java", "-jar", "/app/basicStandardJar.jar"));
    assertThat(buildPlan.getLayers().size()).isEqualTo(2);
    assertThat(buildPlan.getLayers().get(0).getName()).isEqualTo("dependencies");
    assertThat(((FileEntriesLayer) buildPlan.getLayers().get(0)).getEntries())
        .isEqualTo(
            FileEntriesLayer.builder()
                .addEntry(
                    standardJar.getParent().resolve("dependency1"),
                    AbsoluteUnixPath.get("/app/dependency1"))
                .build()
                .getEntries());
    assertThat(buildPlan.getLayers().get(1).getName()).isEqualTo("jar");
    assertThat(((FileEntriesLayer) buildPlan.getLayers().get(1)).getEntries())
        .isEqualTo(
            FileEntriesLayer.builder()
                .addEntry(standardJar, AbsoluteUnixPath.get("/app/basicStandardJar.jar"))
                .build()
                .getEntries());
  }

  @Test
  public void testToJibContainerBuilder_explodedLayeredSpringBoot_basicInfo()
      throws IOException, URISyntaxException, InvalidImageReferenceException {
    Path springBootJar = Paths.get(Resources.getResource(SIMPLE_SPRING_BOOT_LAYERED).toURI());
    Path temporaryParentDirectory = temporaryFolder.getRoot().toPath();
    JibContainerBuilder containerBuilder =
        JarFiles.toJibContainerBuilder(
            springBootJar, temporaryParentDirectory, ProcessingMode.exploded);
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

    assertThat(buildPlan.getLayers().size()).isEqualTo(4);

    assertThat(buildPlan.getLayers().get(0).getName()).isEqualTo("dependencies");
    assertThat(((FileEntriesLayer) buildPlan.getLayers().get(0)).getEntries())
        .isEqualTo(
            FileEntriesLayer.builder()
                .addEntry(
                    temporaryParentDirectory.resolve("BOOT-INF/lib/dependency1.jar"),
                    AbsoluteUnixPath.get("/app/BOOT-INF/lib/dependency1.jar"))
                .build()
                .getEntries());

    assertThat(buildPlan.getLayers().get(1).getName()).isEqualTo("spring-boot-loader");
    assertThat(((FileEntriesLayer) buildPlan.getLayers().get(1)).getEntries())
        .containsExactlyElementsIn(
            FileEntriesLayer.builder()
                .addEntry(
                    temporaryParentDirectory.resolve("org/launcher.class"),
                    AbsoluteUnixPath.get("/app/org/launcher.class"))
                .addEntry(
                    temporaryParentDirectory.resolve("org/orgDirectory/data1.class"),
                    AbsoluteUnixPath.get("/app/org/orgDirectory/data1.class"))
                .build()
                .getEntries());

    assertThat(buildPlan.getLayers().get(2).getName()).isEqualTo("snapshot-dependencies");
    assertThat(((FileEntriesLayer) buildPlan.getLayers().get(2)).getEntries())
        .isEqualTo(
            FileEntriesLayer.builder()
                .addEntry(
                    temporaryParentDirectory.resolve("BOOT-INF/lib/dependency3-SNAPSHOT.jar"),
                    AbsoluteUnixPath.get("/app/BOOT-INF/lib/dependency3-SNAPSHOT.jar"))
                .build()
                .getEntries());

    assertThat(buildPlan.getLayers().get(3).getName()).isEqualTo("application");
    assertThat(((FileEntriesLayer) buildPlan.getLayers().get(3)).getEntries())
        .containsExactlyElementsIn(
            FileEntriesLayer.builder()
                .addEntry(
                    temporaryParentDirectory.resolve("META-INF/MANIFEST.MF"),
                    AbsoluteUnixPath.get("/app/META-INF/MANIFEST.MF"))
                .addEntry(
                    temporaryParentDirectory.resolve("BOOT-INF/classes/class1.class"),
                    AbsoluteUnixPath.get("/app/BOOT-INF/classes/class1.class"))
                .addEntry(
                    temporaryParentDirectory.resolve(
                        "BOOT-INF/classes/classDirectory/class2.class"),
                    AbsoluteUnixPath.get("/app/BOOT-INF/classes/classDirectory/class2.class"))
                .build()
                .getEntries());
  }

  @Test
  public void testToJibContainerBuilder_explodedNonLayeredSpringBoot_basicInfo()
      throws IOException, URISyntaxException, InvalidImageReferenceException {
    Path springBootJar = Paths.get(Resources.getResource(SIMPLE_SPRING_BOOT_NON_LAYERED).toURI());
    Path temporaryParentDirectory = temporaryFolder.getRoot().toPath();
    JibContainerBuilder containerBuilder =
        JarFiles.toJibContainerBuilder(
            springBootJar, temporaryParentDirectory, ProcessingMode.exploded);
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
    assertThat(buildPlan.getLayers().size()).isEqualTo(5);

    assertThat(buildPlan.getLayers().get(0).getName()).isEqualTo("dependencies");
    assertThat(((FileEntriesLayer) buildPlan.getLayers().get(0)).getEntries())
        .isEqualTo(
            FileEntriesLayer.builder()
                .addEntry(
                    temporaryParentDirectory.resolve("BOOT-INF/lib/dependency1.jar"),
                    AbsoluteUnixPath.get("/app/BOOT-INF/lib/dependency1.jar"))
                .build()
                .getEntries());

    assertThat(buildPlan.getLayers().get(1).getName()).isEqualTo("spring-boot-loader");
    assertThat(((FileEntriesLayer) buildPlan.getLayers().get(1)).getEntries())
        .containsExactlyElementsIn(
            FileEntriesLayer.builder()
                .addEntry(
                    temporaryParentDirectory.resolve("org/launcher.class"),
                    AbsoluteUnixPath.get("/app/org/launcher.class"))
                .addEntry(
                    temporaryParentDirectory.resolve("org/orgDirectory/data1.class"),
                    AbsoluteUnixPath.get("/app/org/orgDirectory/data1.class"))
                .build()
                .getEntries());

    assertThat(buildPlan.getLayers().get(2).getName()).isEqualTo("snapshot dependencies");
    assertThat(((FileEntriesLayer) buildPlan.getLayers().get(2)).getEntries())
        .isEqualTo(
            FileEntriesLayer.builder()
                .addEntry(
                    temporaryParentDirectory.resolve("BOOT-INF/lib/dependency3-SNAPSHOT.jar"),
                    AbsoluteUnixPath.get("/app/BOOT-INF/lib/dependency3-SNAPSHOT.jar"))
                .build()
                .getEntries());

    assertThat(buildPlan.getLayers().get(3).getName()).isEqualTo("resources");
    assertThat(((FileEntriesLayer) buildPlan.getLayers().get(3)).getEntries())
        .isEqualTo(
            FileEntriesLayer.builder()
                .addEntry(
                    temporaryParentDirectory.resolve("META-INF/MANIFEST.MF"),
                    AbsoluteUnixPath.get("/app/META-INF/MANIFEST.MF"))
                .build()
                .getEntries());

    assertThat(buildPlan.getLayers().get(4).getName()).isEqualTo("classes");
    assertThat(((FileEntriesLayer) buildPlan.getLayers().get(4)).getEntries())
        .containsExactlyElementsIn(
            FileEntriesLayer.builder()
                .addEntry(
                    temporaryParentDirectory.resolve("BOOT-INF/classes/class1.class"),
                    AbsoluteUnixPath.get("/app/BOOT-INF/classes/class1.class"))
                .addEntry(
                    temporaryParentDirectory.resolve(
                        "BOOT-INF/classes/classDirectory/class2.class"),
                    AbsoluteUnixPath.get("/app/BOOT-INF/classes/classDirectory/class2.class"))
                .build()
                .getEntries());
  }
}
