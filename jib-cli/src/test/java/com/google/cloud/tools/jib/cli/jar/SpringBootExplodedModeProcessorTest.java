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
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Tests for {@link SpringBootExplodedModeProcessor}. */
public class SpringBootExplodedModeProcessorTest {

  private static final String SPRING_BOOT_LAYERED = "jar/spring-boot/springboot_layered.jar";
  private static final String SPRING_BOOT_LAYERED_WITH_EMPTY_LAYER =
      "jar/spring-boot/springboot_layered_singleEmptyLayer.jar";
  private static final String SPRING_BOOT_LAYERED_WITH_ALL_EMPTY_LAYERS_LISTED =
      "jar/spring-boot/springboot_layered_allEmptyLayers.jar";
  private static final String SPRING_BOOT_NOT_LAYERED = "jar/spring-boot/springboot_notLayered.jar";

  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void testCreateLayers_layered_allListed() throws IOException, URISyntaxException {
    // BOOT-INF/layers.idx for this springboot jar as shown below:
    // - "dependencies":
    //   - "BOOT-INF/lib/dependency1.jar"
    //   - "BOOT-INF/lib/dependency2.jar"
    // - "spring-boot-loader":
    //   - "org/"
    // - "snapshot-dependencies":
    //   - "BOOT-INF/lib/dependency-SNAPSHOT-3.jar"
    // - "application":
    //   - "BOOT-INF/classes/"
    //   - "META-INF/"
    Path springBootJar = Paths.get(Resources.getResource(SPRING_BOOT_LAYERED).toURI());
    Path destDir = temporaryFolder.newFolder().toPath();
    SpringBootExplodedModeProcessor springBootExplodedModeProcessor =
        new SpringBootExplodedModeProcessor();
    springBootExplodedModeProcessor.setTempDirectoryPath(destDir);
    springBootExplodedModeProcessor.setJarPath(springBootJar);
    List<FileEntriesLayer> layers = springBootExplodedModeProcessor.createLayers();

    assertThat(layers.size()).isEqualTo(4);

    FileEntriesLayer nonSnapshotLayer = layers.get(0);
    FileEntriesLayer loaderLayer = layers.get(1);
    FileEntriesLayer snapshotLayer = layers.get(2);
    FileEntriesLayer applicationLayer = layers.get(3);

    // Validate dependencies layers.
    assertThat(nonSnapshotLayer.getName()).isEqualTo("dependencies");
    assertThat(
            nonSnapshotLayer
                .getEntries()
                .stream()
                .map(FileEntry::getExtractionPath)
                .collect(Collectors.toList()))
        .containsExactly(
            AbsoluteUnixPath.get("/app/BOOT-INF/lib/dependency1.jar"),
            AbsoluteUnixPath.get("/app/BOOT-INF/lib/dependency2.jar"));
    assertThat(loaderLayer.getName()).isEqualTo("spring-boot-loader");
    assertThat(
            loaderLayer
                .getEntries()
                .stream()
                .map(FileEntry::getExtractionPath)
                .collect(Collectors.toList()))
        .containsExactly(
            AbsoluteUnixPath.get("/app/org/springframework/boot/loader/data/data1.class"),
            AbsoluteUnixPath.get("/app/org/springframework/boot/loader/launcher1.class"));

    assertThat(snapshotLayer.getName()).isEqualTo("snapshot-dependencies");
    assertThat(
            snapshotLayer
                .getEntries()
                .stream()
                .map(FileEntry::getExtractionPath)
                .collect(Collectors.toList()))
        .containsExactly(AbsoluteUnixPath.get("/app/BOOT-INF/lib/dependency3-SNAPSHOT.jar"));

    assertThat(applicationLayer.getName()).isEqualTo("application");
    assertThat(
            applicationLayer
                .getEntries()
                .stream()
                .map(FileEntry::getExtractionPath)
                .collect(Collectors.toList()))
        .containsExactly(
            AbsoluteUnixPath.get("/app/BOOT-INF/classes/class1.class"),
            AbsoluteUnixPath.get("/app/BOOT-INF/classes/classDirectory/class2.class"),
            AbsoluteUnixPath.get("/app/META-INF/MANIFEST.MF"));
  }

  @Test
  public void testCreateLayers_layered_singleEmptyLayerListed()
      throws IOException, URISyntaxException {
    // BOOT-INF/layers.idx for this springboot jar as shown below:
    // - "dependencies":
    //   - "BOOT-INF/lib/dependency1.jar"
    //   - "BOOT-INF/lib/dependency2.jar"
    // - "spring-boot-loader":
    //   - "org/"
    // - "snapshot-dependencies":
    // - "application":
    //   - "BOOT-INF/classes/"
    //   - "META-INF/"
    Path springBootJar =
        Paths.get(Resources.getResource(SPRING_BOOT_LAYERED_WITH_EMPTY_LAYER).toURI());
    Path destDir = temporaryFolder.newFolder().toPath();
    SpringBootExplodedModeProcessor springBootExplodedModeProcessor =
        new SpringBootExplodedModeProcessor();
    springBootExplodedModeProcessor.setTempDirectoryPath(destDir);
    springBootExplodedModeProcessor.setJarPath(springBootJar);
    List<FileEntriesLayer> layers = springBootExplodedModeProcessor.createLayers();

    assertThat(layers.size()).isEqualTo(3);

    FileEntriesLayer nonSnapshotLayer = layers.get(0);
    FileEntriesLayer loaderLayer = layers.get(1);
    FileEntriesLayer applicationLayer = layers.get(2);

    // Validate dependencies layers.
    assertThat(nonSnapshotLayer.getName()).isEqualTo("dependencies");
    assertThat(
            nonSnapshotLayer
                .getEntries()
                .stream()
                .map(FileEntry::getExtractionPath)
                .collect(Collectors.toList()))
        .containsExactly(
            AbsoluteUnixPath.get("/app/BOOT-INF/lib/dependency1.jar"),
            AbsoluteUnixPath.get("/app/BOOT-INF/lib/dependency2.jar"));
    assertThat(loaderLayer.getName()).isEqualTo("spring-boot-loader");
    assertThat(
            loaderLayer
                .getEntries()
                .stream()
                .map(FileEntry::getExtractionPath)
                .collect(Collectors.toList()))
        .containsExactly(
            AbsoluteUnixPath.get("/app/org/springframework/boot/loader/data/data1.class"),
            AbsoluteUnixPath.get("/app/org/springframework/boot/loader/launcher1.class"));

    assertThat(applicationLayer.getName()).isEqualTo("application");
    assertThat(
            applicationLayer
                .getEntries()
                .stream()
                .map(FileEntry::getExtractionPath)
                .collect(Collectors.toList()))
        .containsExactly(
            AbsoluteUnixPath.get("/app/BOOT-INF/classes/class1.class"),
            AbsoluteUnixPath.get("/app/BOOT-INF/classes/classDirectory/class2.class"),
            AbsoluteUnixPath.get("/app/META-INF/MANIFEST.MF"));
  }

  @Test
  public void testCreateLayers_layered_allEmptyLayersListed()
      throws IOException, URISyntaxException {
    // BOOT-INF/layers.idx for this springboot jar as shown below:
    // - "dependencies":
    // - "spring-boot-loader":
    // - "snapshot-dependencies":
    // - "application":
    Path springBootJar =
        Paths.get(Resources.getResource(SPRING_BOOT_LAYERED_WITH_ALL_EMPTY_LAYERS_LISTED).toURI());
    Path destDir = temporaryFolder.newFolder().toPath();
    SpringBootExplodedModeProcessor springBootExplodedModeProcessor =
        new SpringBootExplodedModeProcessor();
    springBootExplodedModeProcessor.setTempDirectoryPath(destDir);
    springBootExplodedModeProcessor.setJarPath(springBootJar);
    List<FileEntriesLayer> layers = springBootExplodedModeProcessor.createLayers();

    assertThat(layers.size()).isEqualTo(0);
  }

  @Test
  public void testCreateLayers_nonLayered() throws IOException, URISyntaxException {
    Path springBootJar = Paths.get(Resources.getResource(SPRING_BOOT_NOT_LAYERED).toURI());

    Path destDir = temporaryFolder.newFolder().toPath();
    SpringBootExplodedModeProcessor springBootExplodedModeProcessor =
        new SpringBootExplodedModeProcessor();
    springBootExplodedModeProcessor.setTempDirectoryPath(destDir);
    springBootExplodedModeProcessor.setJarPath(springBootJar);
    List<FileEntriesLayer> layers = springBootExplodedModeProcessor.createLayers();

    assertThat(layers.size()).isEqualTo(5);

    FileEntriesLayer nonSnapshotLayer = layers.get(0);
    FileEntriesLayer loaderLayer = layers.get(1);
    FileEntriesLayer snapshotLayer = layers.get(2);
    FileEntriesLayer resourcesLayer = layers.get(3);
    FileEntriesLayer classesLayer = layers.get(4);

    assertThat(nonSnapshotLayer.getName()).isEqualTo("dependencies");
    assertThat(
            nonSnapshotLayer
                .getEntries()
                .stream()
                .map(FileEntry::getExtractionPath)
                .collect(Collectors.toList()))
        .containsExactly(
            AbsoluteUnixPath.get("/app/BOOT-INF/lib/dependency1.jar"),
            AbsoluteUnixPath.get("/app/BOOT-INF/lib/dependency2.jar"));
    assertThat(loaderLayer.getName()).isEqualTo("spring-boot-loader");
    assertThat(
            loaderLayer
                .getEntries()
                .stream()
                .map(FileEntry::getExtractionPath)
                .collect(Collectors.toList()))
        .containsExactly(
            AbsoluteUnixPath.get("/app/org/springframework/boot/loader/data/data1.class"),
            AbsoluteUnixPath.get("/app/org/springframework/boot/loader/launcher1.class"));

    assertThat(snapshotLayer.getName()).isEqualTo("snapshot dependencies");
    assertThat(snapshotLayer.getEntries().get(0).getExtractionPath())
        .isEqualTo(AbsoluteUnixPath.get("/app/BOOT-INF/lib/dependency3-SNAPSHOT.jar"));

    assertThat(resourcesLayer.getName()).isEqualTo("resources");
    assertThat(resourcesLayer.getEntries().get(0).getExtractionPath())
        .isEqualTo(AbsoluteUnixPath.get("/app/META-INF/MANIFEST.MF"));

    assertThat(classesLayer.getName()).isEqualTo("classes");
    assertThat(
            classesLayer
                .getEntries()
                .stream()
                .map(FileEntry::getExtractionPath)
                .collect(Collectors.toList()))
        .containsExactly(
            AbsoluteUnixPath.get("/app/BOOT-INF/classes/class1.class"),
            AbsoluteUnixPath.get("/app/BOOT-INF/classes/classDirectory/class2.class"));
  }
}
