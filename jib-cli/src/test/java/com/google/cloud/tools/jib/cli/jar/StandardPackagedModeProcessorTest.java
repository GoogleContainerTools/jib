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
import org.junit.Test;

/** Tests for {@link StandardPackagedModeProcessor}. */
public class StandardPackagedModeProcessorTest {

  private static final String STANDARD_JAR_EMPTY = "jar/standard/emptyStandardJar.jar";
  private static final String STANDARD_SINGLE_DEPENDENCY_JAR = "jar/standard/singleDepJar.jar";
  private static final String STANDARD_JAR_WITH_CLASS_PATH_MANIFEST =
      "jar/standard/standardJarWithClassPath.jar";

  @Test
  public void testCreateLayersForPackagedStandard_emptyJar()
      throws IOException, URISyntaxException {
    Path standardJar = Paths.get(Resources.getResource(STANDARD_JAR_EMPTY).toURI());
    StandardPackagedModeProcessor standardPackagedModeProcessor =
        new StandardPackagedModeProcessor();
    List<FileEntriesLayer> layers = standardPackagedModeProcessor.createLayers(standardJar);

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
    StandardPackagedModeProcessor standardPackagedModeProcessor =
        new StandardPackagedModeProcessor();
    List<FileEntriesLayer> layers = standardPackagedModeProcessor.createLayers(standardJar);

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
    StandardPackagedModeProcessor standardPackagedModeProcessor =
        new StandardPackagedModeProcessor();
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> standardPackagedModeProcessor.createLayers(standardJar));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(
            "Dependency required by the JAR (as specified in `Class-Path` in the JAR manifest) doesn't exist: "
                + standardJar.getParent().resolve("dependency.jar"));
  }

  @Test
  public void testComputeEntrypointForPackagedStandard_noMainClass() throws URISyntaxException {
    Path standardJar = Paths.get(Resources.getResource(STANDARD_JAR_EMPTY).toURI());
    StandardPackagedModeProcessor standardPackagedModeProcessor =
        new StandardPackagedModeProcessor();
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> standardPackagedModeProcessor.computeEntrypoint(standardJar));

    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(
            "`Main-Class:` attribute for an application main class not defined in the input JAR's manifest "
                + "(`META-INF/MANIFEST.MF` in the JAR).");
  }

  @Test
  public void testComputeEntrypointForPackagedStandard_withMainClass()
      throws IOException, URISyntaxException {
    Path standardJar =
        Paths.get(Resources.getResource(STANDARD_JAR_WITH_CLASS_PATH_MANIFEST).toURI());
    StandardPackagedModeProcessor standardPackagedModeProcessor =
        new StandardPackagedModeProcessor();
    ImmutableList<String> actualEntrypoint =
        standardPackagedModeProcessor.computeEntrypoint(standardJar);

    assertThat(actualEntrypoint)
        .isEqualTo(ImmutableList.of("java", "-jar", "/app/standardJarWithClassPath.jar"));
  }
}
