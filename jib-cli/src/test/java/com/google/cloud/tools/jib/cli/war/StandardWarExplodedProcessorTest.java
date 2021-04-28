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

import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer;
import com.google.cloud.tools.jib.api.buildplan.FileEntry;
import com.google.cloud.tools.jib.filesystem.DirectoryWalker;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteStreams;
import com.google.common.io.Resources;
import com.google.common.truth.Correspondence;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.StringJoiner;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class StandardWarExplodedProcessorTest {

  private static final Integer JAR_JAVA_VERSION = 0; // any value
  private static final AbsoluteUnixPath APP_ROOT = AbsoluteUnixPath.get("/my/app");
  private static final Correspondence<FileEntry, String> EXTRACTION_PATH_OF =
      Correspondence.transforming(
          entry -> entry.getExtractionPath().toString(), "has extractionPath of");

  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void testCreateLayers_allLayers_correctExtractionPaths()
      throws IOException, URISyntaxException {

    // Prepare war file for test
    Path tempDirectory = temporaryFolder.getRoot().toPath();
    Path warContents = Paths.get(Resources.getResource("war/standard/allLayers").toURI());
    Path standardWar = zipUpDirectory(warContents, tempDirectory.resolve("standardWar.war"));

    Path explodedWarDestination = temporaryFolder.newFolder("exploded-war").toPath();
    StandardWarExplodedProcessor processor =
        new StandardWarExplodedProcessor(
            standardWar, explodedWarDestination, JAR_JAVA_VERSION, APP_ROOT);
    List<FileEntriesLayer> layers = processor.createLayers();
    FileEntriesLayer nonSnapshotLayer = layers.get(0);
    FileEntriesLayer snapshotLayer = layers.get(1);
    FileEntriesLayer resourcesLayer = layers.get(2);
    FileEntriesLayer classesLayer = layers.get(3);

    assertThat(nonSnapshotLayer.getEntries())
        .comparingElementsUsing(EXTRACTION_PATH_OF)
        .containsExactly("/my/app/WEB-INF/lib/dependency-1.0.0.jar");
    assertThat(snapshotLayer.getEntries())
        .comparingElementsUsing(EXTRACTION_PATH_OF)
        .containsExactly("/my/app/WEB-INF/lib/dependencyX-1.0.0-SNAPSHOT.jar");
    assertThat(resourcesLayer.getEntries())
        .comparingElementsUsing(EXTRACTION_PATH_OF)
        .containsExactly(
            "/my/app/META-INF/context.xml",
            "/my/app/Test.jsp",
            "/my/app/WEB-INF/web.xml",
            "/my/app/WEB-INF/classes/package/test.properties");
    assertThat(classesLayer.getEntries())
        .comparingElementsUsing(EXTRACTION_PATH_OF)
        .containsExactly(
            "/my/app/WEB-INF/classes/MyClass2.class",
            "/my/app/WEB-INF/classes/package/MyClass.class");
  }

  @Test
  public void testCreateLayers_webInfLibDoesNotExist_correctExtractionPaths()
      throws IOException, URISyntaxException {
    // Prepare war file for test
    Path tempDirectory = temporaryFolder.getRoot().toPath();
    Path warContents = Paths.get(Resources.getResource("war/standard/noWebInfLib").toURI());
    Path standardWar = zipUpDirectory(warContents, tempDirectory.resolve("noDependenciesWar.war"));

    Path explodedWarDestination = temporaryFolder.newFolder("exploded-war").toPath();
    StandardWarExplodedProcessor processor =
        new StandardWarExplodedProcessor(
            standardWar, explodedWarDestination, JAR_JAVA_VERSION, APP_ROOT);
    List<FileEntriesLayer> layers = processor.createLayers();

    assertThat(layers.size()).isEqualTo(2);
    FileEntriesLayer resourcesLayer = layers.get(0);
    FileEntriesLayer classesLayer = layers.get(1);

    assertThat(resourcesLayer.getEntries())
        .comparingElementsUsing(EXTRACTION_PATH_OF)
        .containsExactly("/my/app/META-INF/context.xml");
    assertThat(classesLayer.getEntries())
        .comparingElementsUsing(EXTRACTION_PATH_OF)
        .containsExactly(
            "/my/app/WEB-INF/classes/MyClass2.class",
            "/my/app/WEB-INF/classes/package/MyClass.class");
  }

  @Test
  public void testCreateLayers_webInfClassesDoesNotExist_correctExtractionPaths()
      throws IOException, URISyntaxException {
    // Prepare war file for test
    Path tempDirectory = temporaryFolder.getRoot().toPath();
    Path warContents = Paths.get(Resources.getResource("war/standard/noWebInfClasses").toURI());
    Path standardWar = zipUpDirectory(warContents, tempDirectory.resolve("noClassesWar.war"));

    Path explodedWarDestination = temporaryFolder.newFolder("exploded-war").toPath();
    StandardWarExplodedProcessor processor =
        new StandardWarExplodedProcessor(
            standardWar, explodedWarDestination, JAR_JAVA_VERSION, APP_ROOT);
    List<FileEntriesLayer> layers = processor.createLayers();

    assertThat(layers.size()).isEqualTo(3);

    FileEntriesLayer nonSnapshotLayer = layers.get(0);
    FileEntriesLayer snapshotLayer = layers.get(1);
    FileEntriesLayer resourcesLayer = layers.get(2);

    assertThat(nonSnapshotLayer.getEntries())
        .comparingElementsUsing(EXTRACTION_PATH_OF)
        .containsExactly("/my/app/WEB-INF/lib/dependency-1.0.0.jar");
    assertThat(snapshotLayer.getEntries())
        .comparingElementsUsing(EXTRACTION_PATH_OF)
        .containsExactly("/my/app/WEB-INF/lib/dependencyX-1.0.0-SNAPSHOT.jar");
    assertThat(resourcesLayer.getEntries())
        .comparingElementsUsing(EXTRACTION_PATH_OF)
        .containsExactly("/my/app/META-INF/context.xml");
  }

  @Test
  public void testComputeEntrypoint() {
    StandardWarExplodedProcessor processor =
        new StandardWarExplodedProcessor(
            Paths.get("ignore"), Paths.get("ignore"), JAR_JAVA_VERSION, APP_ROOT);
    ImmutableList<String> entrypoint = processor.computeEntrypoint(ImmutableList.of("ignored"));
    assertThat(entrypoint).containsExactly("java", "-jar", "/usr/local/jetty/start.jar").inOrder();
  }

  private static Path zipUpDirectory(Path sourceRoot, Path targetZip) throws IOException {
    try (ZipOutputStream zipOut = new ZipOutputStream(Files.newOutputStream(targetZip))) {
      for (Path source : new DirectoryWalker(sourceRoot).filterRoot().walk()) {

        StringJoiner pathJoiner = new StringJoiner("/", "", "");
        sourceRoot.relativize(source).forEach(element -> pathJoiner.add(element.toString()));
        String zipEntryPath =
            Files.isDirectory(source) ? pathJoiner.toString() + '/' : pathJoiner.toString();

        ZipEntry entry = new ZipEntry(zipEntryPath);
        zipOut.putNextEntry(entry);
        if (!Files.isDirectory(source)) {
          try (InputStream in = Files.newInputStream(source)) {
            ByteStreams.copy(in, zipOut);
          }
        }
        zipOut.closeEntry();
      }
    }
    return targetZip;
  }
}
