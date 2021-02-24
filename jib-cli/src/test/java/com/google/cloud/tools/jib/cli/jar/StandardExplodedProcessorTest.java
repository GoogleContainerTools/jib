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

/** Tests for {@link StandardExplodedProcessor}. */
public class StandardExplodedProcessorTest {

  private static final String STANDARD_JAR_WITHOUT_CLASS_PATH_MANIFEST =
      "jar/standard/standardJarWithoutClassPath.jar";
  private static final String STANDARD_JAR_WITH_CLASS_PATH_MANIFEST =
      "jar/standard/standardJarWithClassPath.jar";
  private static final String STANDARD_JAR_WITH_ONLY_CLASSES =
      "jar/standard/standardJarWithOnlyClasses.jar";
  private static final String STANDARD_JAR_EMPTY = "jar/standard/emptyStandardJar.jar";
  private static final String STANDARD_SINGLE_DEPENDENCY_JAR = "jar/standard/singleDepJar.jar";
  private static final Integer JAR_JAVA_VERSION = 0; // any value

  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void testCreateLayers_emptyJar() throws IOException, URISyntaxException {
    Path standardJar = Paths.get(Resources.getResource(STANDARD_JAR_EMPTY).toURI());
    Path destDir = temporaryFolder.newFolder().toPath();
    StandardExplodedProcessor standardExplodedModeProcessor =
        new StandardExplodedProcessor(standardJar, destDir, JAR_JAVA_VERSION);

    List<FileEntriesLayer> layers = standardExplodedModeProcessor.createLayers();

    assertThat(layers.size()).isEqualTo(1);

    FileEntriesLayer resourcesLayer = layers.get(0);

    assertThat(resourcesLayer.getEntries().size()).isEqualTo(1);
    assertThat(resourcesLayer.getEntries().get(0).getExtractionPath())
        .isEqualTo(AbsoluteUnixPath.get("/app/explodedJar/META-INF/MANIFEST.MF"));
  }

  @Test
  public void testCreateLayers_withClassPathInManifest() throws IOException, URISyntaxException {
    Path standardJar =
        Paths.get(Resources.getResource(STANDARD_JAR_WITH_CLASS_PATH_MANIFEST).toURI());
    Path destDir = temporaryFolder.newFolder().toPath();
    StandardExplodedProcessor standardExplodedModeProcessor =
        new StandardExplodedProcessor(standardJar, destDir, JAR_JAVA_VERSION);

    List<FileEntriesLayer> layers = standardExplodedModeProcessor.createLayers();

    assertThat(layers.size()).isEqualTo(4);

    FileEntriesLayer nonSnapshotLayer = layers.get(0);
    FileEntriesLayer snapshotLayer = layers.get(1);
    FileEntriesLayer resourcesLayer = layers.get(2);
    FileEntriesLayer classesLayer = layers.get(3);

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
                AbsoluteUnixPath.get("/app/dependencies/dependency1"),
                AbsoluteUnixPath.get("/app/dependencies/dependency2"),
                AbsoluteUnixPath.get("/app/dependencies/dependency4")));
    assertThat(snapshotLayer.getName()).isEqualTo("snapshot dependencies");
    assertThat(snapshotLayer.getEntries().size()).isEqualTo(1);
    assertThat(snapshotLayer.getEntries().get(0).getExtractionPath())
        .isEqualTo(AbsoluteUnixPath.get("/app/dependencies/dependency3-SNAPSHOT-1.jar"));

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
            AbsoluteUnixPath.get("/app/explodedJar/META-INF/MANIFEST.MF"),
            AbsoluteUnixPath.get("/app/explodedJar/directory1/resource1.txt"),
            AbsoluteUnixPath.get("/app/explodedJar/directory2/directory3/resource2.sql"),
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
            AbsoluteUnixPath.get("/app/explodedJar/class5.class"),
            AbsoluteUnixPath.get("/app/explodedJar/directory1/class1.class"),
            AbsoluteUnixPath.get("/app/explodedJar/directory1/class2.class"),
            AbsoluteUnixPath.get("/app/explodedJar/directory2/class4.class"),
            AbsoluteUnixPath.get("/app/explodedJar/directory2/directory3/class3.class"));
  }

  @Test
  public void testCreateLayers_withoutClassPathInManifest() throws IOException, URISyntaxException {
    Path standardJar =
        Paths.get(Resources.getResource(STANDARD_JAR_WITHOUT_CLASS_PATH_MANIFEST).toURI());
    Path destDir = temporaryFolder.newFolder().toPath();
    StandardExplodedProcessor standardExplodedModeProcessor =
        new StandardExplodedProcessor(standardJar, destDir, JAR_JAVA_VERSION);

    List<FileEntriesLayer> layers = standardExplodedModeProcessor.createLayers();

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
            AbsoluteUnixPath.get("/app/explodedJar/META-INF/MANIFEST.MF"),
            AbsoluteUnixPath.get("/app/explodedJar/directory1/resource1.txt"),
            AbsoluteUnixPath.get("/app/explodedJar/directory2/directory3/resource2.sql"),
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
            AbsoluteUnixPath.get("/app/explodedJar/class5.class"),
            AbsoluteUnixPath.get("/app/explodedJar/directory1/class1.class"),
            AbsoluteUnixPath.get("/app/explodedJar/directory1/class2.class"),
            AbsoluteUnixPath.get("/app/explodedJar/directory2/class4.class"),
            AbsoluteUnixPath.get("/app/explodedJar/directory2/directory3/class3.class"));
  }

  @Test
  public void testCreateLayers_withoutClassPathInManifest_containsOnlyClasses()
      throws IOException, URISyntaxException {
    Path standardJar = Paths.get(Resources.getResource(STANDARD_JAR_WITH_ONLY_CLASSES).toURI());
    Path destDir = temporaryFolder.newFolder().toPath();
    StandardExplodedProcessor standardExplodedModeProcessor =
        new StandardExplodedProcessor(standardJar, destDir, JAR_JAVA_VERSION);

    List<FileEntriesLayer> layers = standardExplodedModeProcessor.createLayers();

    assertThat(layers.size()).isEqualTo(2);

    FileEntriesLayer resourcesLayer = layers.get(0);
    FileEntriesLayer classesLayer = layers.get(1);

    // Validate resources layer.
    assertThat(resourcesLayer.getEntries().size()).isEqualTo(1);
    assertThat(resourcesLayer.getEntries().get(0).getExtractionPath())
        .isEqualTo(AbsoluteUnixPath.get("/app/explodedJar/META-INF/MANIFEST.MF"));

    // Validate classes layer.
    List<AbsoluteUnixPath> actualClassesPath =
        classesLayer
            .getEntries()
            .stream()
            .map(FileEntry::getExtractionPath)
            .collect(Collectors.toList());
    assertThat(actualClassesPath)
        .containsExactly(
            AbsoluteUnixPath.get("/app/explodedJar/class1.class"),
            AbsoluteUnixPath.get("/app/explodedJar/class2.class"));
  }

  @Test
  public void testCreateLayers_dependencyDoesNotExist() throws URISyntaxException {
    Path standardJar = Paths.get(Resources.getResource(STANDARD_SINGLE_DEPENDENCY_JAR).toURI());
    Path destDir = temporaryFolder.getRoot().toPath();
    StandardExplodedProcessor standardExplodedModeProcessor =
        new StandardExplodedProcessor(standardJar, destDir, JAR_JAVA_VERSION);

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class, () -> standardExplodedModeProcessor.createLayers());

    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(
            "Dependency required by the JAR (as specified in `Class-Path` in the JAR manifest) doesn't exist: "
                + standardJar.getParent().resolve("dependency.jar"));
  }

  @Test
  public void testComputeEntrypoint_noMainClass() throws URISyntaxException {
    Path standardJar = Paths.get(Resources.getResource(STANDARD_JAR_EMPTY).toURI());
    StandardExplodedProcessor standardExplodedModeProcessor =
        new StandardExplodedProcessor(standardJar, Paths.get("ignore"), JAR_JAVA_VERSION);

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> standardExplodedModeProcessor.computeEntrypoint(ImmutableList.of()));

    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(
            "`Main-Class:` attribute for an application main class not defined in the input JAR's manifest "
                + "(`META-INF/MANIFEST.MF` in the JAR).");
  }

  @Test
  public void testComputeEntrypoint_withMainClass() throws IOException, URISyntaxException {
    Path standardJar =
        Paths.get(Resources.getResource(STANDARD_JAR_WITH_CLASS_PATH_MANIFEST).toURI());
    StandardExplodedProcessor standardExplodedModeProcessor =
        new StandardExplodedProcessor(standardJar, Paths.get("ignore"), JAR_JAVA_VERSION);

    ImmutableList<String> actualEntrypoint =
        standardExplodedModeProcessor.computeEntrypoint(ImmutableList.of());

    assertThat(actualEntrypoint)
        .isEqualTo(
            ImmutableList.of("java", "-cp", "/app/explodedJar:/app/dependencies/*", "HelloWorld"));
  }

  @Test
  public void testComputeEntrypoint_withMainClass_jvmFlags()
      throws IOException, URISyntaxException {
    Path standardJar =
        Paths.get(Resources.getResource(STANDARD_JAR_WITH_CLASS_PATH_MANIFEST).toURI());
    StandardExplodedProcessor standardExplodedModeProcessor =
        new StandardExplodedProcessor(standardJar, Paths.get("ignore"), JAR_JAVA_VERSION);

    ImmutableList<String> actualEntrypoint =
        standardExplodedModeProcessor.computeEntrypoint(ImmutableList.of("-jvm-flag"));

    assertThat(actualEntrypoint)
        .isEqualTo(
            ImmutableList.of(
                "java", "-jvm-flag", "-cp", "/app/explodedJar:/app/dependencies/*", "HelloWorld"));
  }

  @Test
  public void testGetJarJavaVersion() {
    StandardExplodedProcessor standardExplodedProcessor =
        new StandardExplodedProcessor(Paths.get("ignore"), Paths.get("ignore"), 8);
    assertThat(standardExplodedProcessor.getJarJavaVersion()).isEqualTo(8);
  }
}
