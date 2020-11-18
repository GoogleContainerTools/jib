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
import static org.junit.Assert.assertThrows;

import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer;
import com.google.cloud.tools.jib.api.buildplan.FileEntry;
import com.google.cloud.tools.jib.cli.jar.JarModeProcessor.JarType;
import com.google.common.collect.ImmutableList;
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

public class JarModeProcessorTest {

  private static final String SPRING_BOOT_JAR = "jar/springboot/springboot_sample.jar";
  private static final String STANDARD_JAR_WITH_CLASS_PATH_MANIFEST =
      "jar/standard/standardJarWithClassPath.jar";
  private static final String STANDARD_JAR_WITHOUT_CLASS_PATH_MANIFEST =
      "jar/standard/standardJarWithoutClassPath.jar";
  private static final String STANDARD_JAR_WITH_ONLY_CLASSES =
      "jar/standard/standardJarWithOnlyClasses.jar";
  private static final String STANDARD_JAR_EMPTY = "jar/standard/emptyStandardJar.jar";
  private static final String STANDARD_SINGLE_DEPENDENCY_JAR = "jar/standard/singleDepJar.jar";

  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void testDetermineJarType_springBoot() throws IOException, URISyntaxException {
    Path springBootJar = Paths.get(Resources.getResource(SPRING_BOOT_JAR).toURI());
    JarType jarType = JarModeProcessor.determineJarType(springBootJar);
    assertThat(jarType).isEqualTo(JarType.SPRING_BOOT);
  }

  @Test
  public void testDetermineJarType_standard() throws IOException, URISyntaxException {
    Path standardJar = Paths.get(Resources.getResource(STANDARD_JAR_EMPTY).toURI());
    JarType jarType = JarModeProcessor.determineJarType(standardJar);
    assertThat(jarType).isEqualTo(JarType.STANDARD);
  }

  @Test
  public void testCreateLayersForExplodedStandard_emptyJar()
      throws IOException, URISyntaxException {
    Path standardJar = Paths.get(Resources.getResource(STANDARD_JAR_EMPTY).toURI());
    Path destDir = temporaryFolder.newFolder().toPath();
    List<FileEntriesLayer> layers =
        JarModeProcessor.createLayersForExplodedStandard(standardJar, destDir);

    assertThat(layers.size()).isEqualTo(2);

    FileEntriesLayer resourcesLayer = layers.get(0);
    FileEntriesLayer classesLayer = layers.get(1);

    // Validate resources layer.
    List<AbsoluteUnixPath> actualResourcesPath =
        resourcesLayer
            .getEntries()
            .stream()
            .map(FileEntry::getExtractionPath)
            .collect(Collectors.toList());
    assertThat(actualResourcesPath)
        .containsExactly(
            AbsoluteUnixPath.get("/app/explodedJar/META-INF"),
            AbsoluteUnixPath.get("/app/explodedJar/META-INF/MANIFEST.MF"));

    // Validate classes layer.
    List<AbsoluteUnixPath> actualClassesPath =
        classesLayer
            .getEntries()
            .stream()
            .map(FileEntry::getExtractionPath)
            .collect(Collectors.toList());
    assertThat(actualClassesPath)
        .containsExactly(AbsoluteUnixPath.get("/app/explodedJar/META-INF"));
  }

  @Test
  public void testCreateLayersForExplodedStandard_withClassPathInManifest()
      throws IOException, URISyntaxException {
    Path standardJar =
        Paths.get(Resources.getResource(STANDARD_JAR_WITH_CLASS_PATH_MANIFEST).toURI());
    Path destDir = temporaryFolder.newFolder().toPath();
    List<FileEntriesLayer> layers =
        JarModeProcessor.createLayersForExplodedStandard(standardJar, destDir);

    assertThat(layers.size()).isEqualTo(4);

    FileEntriesLayer nonSnapshotLayer = layers.get(0);
    FileEntriesLayer snapshotLayer = layers.get(1);
    FileEntriesLayer resourcesLayer = layers.get(2);
    FileEntriesLayer classesLayer = layers.get(3);

    // Validate dependencies layer.
    assertThat(nonSnapshotLayer.getName()).isEqualTo("dependencies");
    assertThat(
            nonSnapshotLayer
                .getEntries()
                .stream()
                .map(FileEntry::getExtractionPath)
                .collect(Collectors.toList()))
        .isEqualTo(
            ImmutableList.of(
                AbsoluteUnixPath.get("/app/dependencies/dependency1"),
                AbsoluteUnixPath.get("/app/dependencies/dependency2"),
                AbsoluteUnixPath.get("/app/dependencies/dependency4")));
    assertThat(snapshotLayer.getName()).isEqualTo("snapshot dependencies");
    assertThat(
            snapshotLayer
                .getEntries()
                .stream()
                .map(FileEntry::getExtractionPath)
                .collect(Collectors.toList()))
        .isEqualTo(
            ImmutableList.of(AbsoluteUnixPath.get("/app/dependencies/dependency3-SNAPSHOT-1.jar")));

    // Validate resources layer.
    assertThat(resourcesLayer.getName()).isEqualTo("resources");
    List<AbsoluteUnixPath> actualResourcesPaths =
        resourcesLayer
            .getEntries()
            .stream()
            .map(FileEntry::getExtractionPath)
            .collect(Collectors.toList());
    assertThat(actualResourcesPaths)
        .containsExactly(
            AbsoluteUnixPath.get("/app/explodedJar/META-INF/"),
            AbsoluteUnixPath.get("/app/explodedJar/META-INF/MANIFEST.MF"),
            AbsoluteUnixPath.get("/app/explodedJar/directory1"),
            AbsoluteUnixPath.get("/app/explodedJar/directory1/resource1.txt"),
            AbsoluteUnixPath.get("/app/explodedJar/directory2"),
            AbsoluteUnixPath.get("/app/explodedJar/directory2/directory3"),
            AbsoluteUnixPath.get("/app/explodedJar/directory2/directory3/resource2.sql"),
            AbsoluteUnixPath.get("/app/explodedJar/directory4"),
            AbsoluteUnixPath.get("/app/explodedJar/directory4/resource3.txt"),
            AbsoluteUnixPath.get("/app/explodedJar/resource4.sql"));

    // Validate classes layer.
    assertThat(classesLayer.getName()).isEqualTo("classes");
    List<AbsoluteUnixPath> actualClassesPaths =
        classesLayer
            .getEntries()
            .stream()
            .map(FileEntry::getExtractionPath)
            .collect(Collectors.toList());
    assertThat(actualClassesPaths)
        .containsExactly(
            AbsoluteUnixPath.get("/app/explodedJar/META-INF"),
            AbsoluteUnixPath.get("/app/explodedJar/class5.class"),
            AbsoluteUnixPath.get("/app/explodedJar/directory1"),
            AbsoluteUnixPath.get("/app/explodedJar/directory1/class1.class"),
            AbsoluteUnixPath.get("/app/explodedJar/directory1/class2.class"),
            AbsoluteUnixPath.get("/app/explodedJar/directory2"),
            AbsoluteUnixPath.get("/app/explodedJar/directory2/class4.class"),
            AbsoluteUnixPath.get("/app/explodedJar/directory2/directory3"),
            AbsoluteUnixPath.get("/app/explodedJar/directory2/directory3/class3.class"),
            AbsoluteUnixPath.get("/app/explodedJar/directory4"));
  }

  @Test
  public void testCreateLayersForExplodedStandard_withoutClassPathInManifest()
      throws IOException, URISyntaxException {
    Path standardJar =
        Paths.get(Resources.getResource(STANDARD_JAR_WITHOUT_CLASS_PATH_MANIFEST).toURI());
    Path destDir = temporaryFolder.newFolder().toPath();
    List<FileEntriesLayer> layers =
        JarModeProcessor.createLayersForExplodedStandard(standardJar, destDir);

    assertThat(layers.size()).isEqualTo(2);

    FileEntriesLayer resourcesLayer = layers.get(0);
    FileEntriesLayer classesLayer = layers.get(1);

    // Validate resources layer.
    assertThat(resourcesLayer.getName()).isEqualTo("resources");
    List<AbsoluteUnixPath> actualResourcesPaths =
        resourcesLayer
            .getEntries()
            .stream()
            .map(FileEntry::getExtractionPath)
            .collect(Collectors.toList());
    assertThat(actualResourcesPaths)
        .containsExactly(
            AbsoluteUnixPath.get("/app/explodedJar/META-INF/"),
            AbsoluteUnixPath.get("/app/explodedJar/META-INF/MANIFEST.MF"),
            AbsoluteUnixPath.get("/app/explodedJar/directory1"),
            AbsoluteUnixPath.get("/app/explodedJar/directory1/resource1.txt"),
            AbsoluteUnixPath.get("/app/explodedJar/directory2"),
            AbsoluteUnixPath.get("/app/explodedJar/directory2/directory3"),
            AbsoluteUnixPath.get("/app/explodedJar/directory2/directory3/resource2.sql"),
            AbsoluteUnixPath.get("/app/explodedJar/directory4"),
            AbsoluteUnixPath.get("/app/explodedJar/directory4/resource3.txt"),
            AbsoluteUnixPath.get("/app/explodedJar/resource4.sql"));

    // Validate classes layer.
    assertThat(classesLayer.getName()).isEqualTo("classes");
    List<AbsoluteUnixPath> actualClassesPaths =
        classesLayer
            .getEntries()
            .stream()
            .map(FileEntry::getExtractionPath)
            .collect(Collectors.toList());
    assertThat(actualClassesPaths)
        .containsExactly(
            AbsoluteUnixPath.get("/app/explodedJar/META-INF"),
            AbsoluteUnixPath.get("/app/explodedJar/class5.class"),
            AbsoluteUnixPath.get("/app/explodedJar/directory1"),
            AbsoluteUnixPath.get("/app/explodedJar/directory1/class1.class"),
            AbsoluteUnixPath.get("/app/explodedJar/directory1/class2.class"),
            AbsoluteUnixPath.get("/app/explodedJar/directory2"),
            AbsoluteUnixPath.get("/app/explodedJar/directory2/class4.class"),
            AbsoluteUnixPath.get("/app/explodedJar/directory2/directory3"),
            AbsoluteUnixPath.get("/app/explodedJar/directory2/directory3/class3.class"),
            AbsoluteUnixPath.get("/app/explodedJar/directory4"));
  }

  @Test
  public void testCreateLayersForExplodedStandard_withoutClassPathInManifest_containsOnlyClasses()
      throws IOException, URISyntaxException {
    Path standardJar = Paths.get(Resources.getResource(STANDARD_JAR_WITH_ONLY_CLASSES).toURI());
    Path destDir = temporaryFolder.newFolder().toPath();
    List<FileEntriesLayer> layers =
        JarModeProcessor.createLayersForExplodedStandard(standardJar, destDir);

    assertThat(layers.size()).isEqualTo(2);

    FileEntriesLayer resourcesLayer = layers.get(0);
    FileEntriesLayer classesLayer = layers.get(1);

    // Validate resources layer.
    List<AbsoluteUnixPath> actualResourcesPath =
        resourcesLayer
            .getEntries()
            .stream()
            .map(FileEntry::getExtractionPath)
            .collect(Collectors.toList());
    assertThat(actualResourcesPath)
        .containsExactly(
            AbsoluteUnixPath.get("/app/explodedJar/META-INF"),
            AbsoluteUnixPath.get("/app/explodedJar/META-INF/MANIFEST.MF"));

    // Validate classes layer.
    List<AbsoluteUnixPath> actualClassesPath =
        classesLayer
            .getEntries()
            .stream()
            .map(FileEntry::getExtractionPath)
            .collect(Collectors.toList());
    assertThat(actualClassesPath)
        .containsExactly(
            AbsoluteUnixPath.get("/app/explodedJar/META-INF"),
            AbsoluteUnixPath.get("/app/explodedJar/class1.class"),
            AbsoluteUnixPath.get("/app/explodedJar/class2.class"));
  }

  @Test
  public void testCreateLayersForExplodedStandard_dependencyDoesNotExist()
      throws URISyntaxException {
    Path standardJar = Paths.get(Resources.getResource(STANDARD_SINGLE_DEPENDENCY_JAR).toURI());
    Path destDir = temporaryFolder.getRoot().toPath();
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> JarModeProcessor.createLayersForExplodedStandard(standardJar, destDir));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(
            "Dependency required by the JAR (as specified in `Class-Path` in the JAR manifest) doesn't exist: "
                + standardJar.getParent().resolve("dependency.jar"));
  }

  @Test
  public void testComputeEntrypointForExplodedStandard_noMainClass() throws URISyntaxException {
    Path standardJar = Paths.get(Resources.getResource(STANDARD_JAR_EMPTY).toURI());
    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class,
            () -> JarModeProcessor.computeEntrypointForExplodedStandard(standardJar));

    assertThat(ex)
        .hasMessageThat()
        .isEqualTo(
            "`Main-Class:` attribute for an application main class not defined in the input JAR's manifest (`META-INF/MANIFEST.MF` in the JAR).");
  }

  @Test
  public void testComputeEntrypointForExplodedStandard_withMainClass()
      throws IOException, URISyntaxException {
    Path standardJar =
        Paths.get(Resources.getResource(STANDARD_JAR_WITH_CLASS_PATH_MANIFEST).toURI());
    ImmutableList<String> actualEntrypoint =
        JarModeProcessor.computeEntrypointForExplodedStandard(standardJar);

    assertThat(actualEntrypoint)
        .isEqualTo(
            ImmutableList.of("java", "-cp", "/app/explodedJar:/app/dependencies/*", "HelloWorld"));
  }

  @Test
  public void testCreateLayersForPackagedStandard_emptyJar()
      throws IOException, URISyntaxException {
    Path standardJar = Paths.get(Resources.getResource(STANDARD_JAR_EMPTY).toURI());
    List<FileEntriesLayer> layers = JarModeProcessor.createLayersForPackagedStandard(standardJar);

    assertThat(layers.size()).isEqualTo(1);

    FileEntriesLayer jarLayer = layers.get(0);
    assertThat(jarLayer.getName()).isEqualTo("jar");
    assertThat(jarLayer.getEntries().size()).isEqualTo(1);
    assertThat(jarLayer.getEntries().get(0).getExtractionPath())
        .isEqualTo(AbsoluteUnixPath.get("/app/emptyStandardJar.jar"));
  }

  @Test
  public void testCreateLayersForPackagedStandard_withClassPathInManifest()
      throws IOException, URISyntaxException {
    Path standardJar =
        Paths.get(Resources.getResource(STANDARD_JAR_WITH_CLASS_PATH_MANIFEST).toURI());
    List<FileEntriesLayer> layers = JarModeProcessor.createLayersForPackagedStandard(standardJar);

    assertThat(layers.size()).isEqualTo(3);

    FileEntriesLayer nonSnapshotLayer = layers.get(0);
    FileEntriesLayer snapshotLayer = layers.get(1);
    FileEntriesLayer jarLayer = layers.get(2);

    // Validate dependencies layers.
    assertThat(nonSnapshotLayer.getName()).isEqualTo("dependencies");
    assertThat(
            nonSnapshotLayer
                .getEntries()
                .stream()
                .map(FileEntry::getExtractionPath)
                .collect(Collectors.toList()))
        .isEqualTo(
            ImmutableList.of(
                AbsoluteUnixPath.get("/app/dependency1"),
                AbsoluteUnixPath.get("/app/dependency2"),
                AbsoluteUnixPath.get("/app/directory/dependency4")));
    assertThat(snapshotLayer.getName()).isEqualTo("snapshot dependencies");
    assertThat(
            snapshotLayer
                .getEntries()
                .stream()
                .map(FileEntry::getExtractionPath)
                .collect(Collectors.toList()))
        .isEqualTo(ImmutableList.of(AbsoluteUnixPath.get("/app/dependency3-SNAPSHOT-1.jar")));

    // Validate jar layer.
    assertThat(jarLayer.getName()).isEqualTo("jar");
    assertThat(jarLayer.getEntries().size()).isEqualTo(1);
    assertThat(jarLayer.getEntries().get(0).getExtractionPath())
        .isEqualTo(AbsoluteUnixPath.get("/app/standardJarWithClassPath.jar"));
  }

  @Test
  public void testCreateLayersForPackagedStandard_dependencyDoesNotExist()
      throws URISyntaxException {
    Path standardJar = Paths.get(Resources.getResource(STANDARD_SINGLE_DEPENDENCY_JAR).toURI());
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> JarModeProcessor.createLayersForPackagedStandard(standardJar));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(
            "Dependency required by the JAR (as specified in `Class-Path` in the JAR manifest) doesn't exist: "
                + standardJar.getParent().resolve("dependency.jar"));
  }

  @Test
  public void testComputeEntrypointForPackagedStandard_noMainClass() throws URISyntaxException {
    Path standardJar = Paths.get(Resources.getResource(STANDARD_JAR_EMPTY).toURI());
    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class,
            () -> JarModeProcessor.computeEntrypointForPackagedStandard(standardJar));

    assertThat(ex)
        .hasMessageThat()
        .isEqualTo(
            "`Main-Class:` attribute for an application main class not defined in the input JAR's manifest (`META-INF/MANIFEST.MF` in the JAR).");
  }

  @Test
  public void testComputeEntrypointForPackagedStandard_withMainClass()
      throws IOException, URISyntaxException {
    Path standardJar =
        Paths.get(Resources.getResource(STANDARD_JAR_WITH_CLASS_PATH_MANIFEST).toURI());
    ImmutableList<String> actualEntrypoint =
        JarModeProcessor.computeEntrypointForPackagedStandard(standardJar);

    assertThat(actualEntrypoint)
        .isEqualTo(ImmutableList.of("java", "-jar", "/app/standardJarWithClassPath.jar"));
  }
}
