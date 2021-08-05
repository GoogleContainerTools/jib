/*
 * Copyright 2019 Google LLC.
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

package com.google.cloud.tools.jib.api;

import static com.google.common.truth.Truth.assertThat;

import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer;
import com.google.cloud.tools.jib.api.buildplan.FilePermissions;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.google.common.io.CharStreams;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.BiConsumer;
import java.util.zip.GZIPInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Verify that created image has explicit directory structures, default timestamps, permissions, and
 * file orderings.
 */
public class ReproducibleImageTest {

  @ClassRule public static final TemporaryFolder imageLocation = new TemporaryFolder();

  private static File imageTar;

  @BeforeClass
  public static void createImage()
      throws InvalidImageReferenceException, InterruptedException, CacheDirectoryCreationException,
          IOException, RegistryException, ExecutionException {

    Path root = imageLocation.getRoot().toPath();
    Path fileA = Files.createFile(root.resolve("fileA.txt"));
    Path fileB = Files.createFile(root.resolve("fileB.txt"));
    Path fileC = Files.createFile(root.resolve("fileC.txt"));
    Path subdir = Files.createDirectory(root.resolve("dir"));
    Path subsubdir = Files.createDirectory(subdir.resolve("subdir"));
    Files.createFile(subdir.resolve("fileD.txt"));
    Files.createFile(subsubdir.resolve("fileE.txt"));

    imageTar = new File(imageLocation.getRoot(), "image.tar");
    Containerizer containerizer =
        Containerizer.to(TarImage.at(imageTar.toPath()).named("jib-core/reproducible"));

    Jib.fromScratch()
        .setEntrypoint("echo", "Hello World")
        .addLayer(ImmutableList.of(fileA), AbsoluteUnixPath.get("/app"))
        // layer with out-of-order files
        .addLayer(ImmutableList.of(fileC, fileB), "/app")
        .addFileEntriesLayer(
            FileEntriesLayer.builder()
                .addEntryRecursive(subdir, AbsoluteUnixPath.get("/app"))
                .build())
        .containerize(containerizer);
  }

  @Test
  public void testTarballStructure() throws IOException {
    // known content should produce known results
    List<String> actual = new ArrayList<>();
    try (TarArchiveInputStream input =
        new TarArchiveInputStream(Files.newInputStream(imageTar.toPath()))) {
      TarArchiveEntry imageEntry;
      while ((imageEntry = input.getNextTarEntry()) != null) {
        actual.add(imageEntry.getName());
      }
    }

    assertThat(actual)
        .containsExactly(
            "c46572ef74f58d95e44dd36c1fbdfebd3752e8b56a794a13c11cfed35a1a6e1c.tar.gz",
            "6d2763b0f3940d324ea6b55386429e5b173899608abf7d1bff62e25dd2e4dcea.tar.gz",
            "530c1954a2b087d0b989895ea56435c9dc739a973f2d2b6cb9bb98e55bbea7ac.tar.gz",
            "config.json",
            "manifest.json")
        .inOrder();
  }

  @Test
  public void testManifest() throws IOException {
    String expectedManifest =
        "[{\"Config\":\"config.json\",\"RepoTags\":[\"jib-core/reproducible:latest\"],"
            + "\"Layers\":[\"c46572ef74f58d95e44dd36c1fbdfebd3752e8b56a794a13c11cfed35a1a6e1c.tar.gz\",\"6d2763b0f3940d324ea6b55386429e5b173899608abf7d1bff62e25dd2e4dcea.tar.gz\",\"530c1954a2b087d0b989895ea56435c9dc739a973f2d2b6cb9bb98e55bbea7ac.tar.gz\"]}]";
    String generatedManifest = extractFromTarFileAsString(imageTar, "manifest.json");
    assertThat(generatedManifest).isEqualTo(expectedManifest);
  }

  @Test
  public void testConfiguration() throws IOException {
    String expectedConfig =
        "{\"created\":\"1970-01-01T00:00:00Z\",\"architecture\":\"amd64\",\"os\":\"linux\","
            + "\"config\":{\"Env\":[],\"Entrypoint\":[\"echo\",\"Hello World\"],\"ExposedPorts\":{},\"Labels\":{},\"Volumes\":{}},"
            + "\"history\":[{\"created\":\"1970-01-01T00:00:00Z\",\"author\":\"Jib\",\"created_by\":\"jib-core:null\",\"comment\":\"\"},{\"created\":\"1970-01-01T00:00:00Z\",\"author\":\"Jib\",\"created_by\":\"jib-core:null\",\"comment\":\"\"},{\"created\":\"1970-01-01T00:00:00Z\",\"author\":\"Jib\",\"created_by\":\"jib-core:null\",\"comment\":\"\"}],"
            + "\"rootfs\":{\"type\":\"layers\",\"diff_ids\":[\"sha256:18e4f44e6d1835bd968339b166057bd17ab7d4cbb56dc7262a5cafea7cf8d405\",\"sha256:13369c34f073f2b9c1fa6431e23d925f1a8eac65b1726c8cc8fcc2596c69b414\",\"sha256:4f92c507112d7880ca0f504ef8272b7fdee107263270125036a260a741565923\"]}}";
    String generatedConfig = extractFromTarFileAsString(imageTar, "config.json");
    assertThat(generatedConfig).isEqualTo(expectedConfig);
  }

  @Test
  public void testImageLayout() throws IOException {
    Set<String> paths = new HashSet<>();
    layerEntriesDo(
        (layerName, layerEntry) -> {
          if (layerEntry.isFile()) {
            paths.add(layerEntry.getName());
          }
        });
    assertThat(paths)
        .containsExactly(
            "app/fileA.txt",
            "app/fileB.txt",
            "app/fileC.txt",
            "app/fileD.txt",
            "app/subdir/fileE.txt");
  }

  @Test
  public void testAllFileAndDirectories() throws IOException {
    layerEntriesDo(
        (layerName, layerEntry) ->
            assertThat(layerEntry.isFile() || layerEntry.isDirectory()).isTrue());
  }

  @Test
  public void testTimestampsEpochPlus1s() throws IOException {
    layerEntriesDo(
        (layerName, layerEntry) -> {
          Instant modificationTime = layerEntry.getLastModifiedDate().toInstant();
          assertThat(modificationTime).isEqualTo(Instant.ofEpochSecond(1));
        });
  }

  @Test
  public void testPermissions() throws IOException {
    assertThat(FilePermissions.DEFAULT_FILE_PERMISSIONS.getPermissionBits()).isEqualTo(0644);
    assertThat(FilePermissions.DEFAULT_FOLDER_PERMISSIONS.getPermissionBits()).isEqualTo(0755);
    layerEntriesDo(
        (layerName, layerEntry) -> {
          if (layerEntry.isFile()) {
            assertThat(layerEntry.getMode() & 0777).isEqualTo(0644);
          } else if (layerEntry.isDirectory()) {
            assertThat(layerEntry.getMode() & 0777).isEqualTo(0755);
          }
        });
  }

  @Test
  public void testNoImplicitParentDirectories() throws IOException {
    Set<String> directories = new HashSet<>();
    layerEntriesDo(
        (layerName, layerEntry) -> {
          String entryPath = layerEntry.getName();
          if (layerEntry.isDirectory()) {
            assertThat(entryPath.endsWith("/")).isTrue();
            entryPath = entryPath.substring(0, entryPath.length() - 1);
          }

          int lastSlashPosition = entryPath.lastIndexOf('/');
          String parent = entryPath.substring(0, Math.max(0, lastSlashPosition));
          if (!parent.isEmpty()) {
            assertThat(directories.contains(parent)).isTrue();
          }
          if (layerEntry.isDirectory()) {
            directories.add(entryPath);
          }
        });
  }

  @Test
  public void testFileOrdering() throws IOException {
    Multimap<String, String> layerPaths = ArrayListMultimap.create();
    layerEntriesDo((layerName, layerEntry) -> layerPaths.put(layerName, layerEntry.getName()));
    for (Collection<String> paths : layerPaths.asMap().values()) {
      List<String> sorted = new ArrayList<>(paths);
      // ReproducibleLayerBuilder sorts by TarArchiveEntry::getName()
      Collections.sort(sorted);
      assertThat(paths).containsExactlyElementsIn(sorted).inOrder();
    }
  }

  private void layerEntriesDo(BiConsumer<String, TarArchiveEntry> layerConsumer)
      throws IOException {

    try (TarArchiveInputStream input =
        new TarArchiveInputStream(Files.newInputStream(imageTar.toPath()))) {
      TarArchiveEntry imageEntry;
      while ((imageEntry = input.getNextTarEntry()) != null) {
        String imageEntryName = imageEntry.getName();
        // assume all .tar.gz files are layers
        if (imageEntry.isFile() && imageEntryName.endsWith(".tar.gz")) {
          @SuppressWarnings("resource") // must not close sub-streams
          TarArchiveInputStream layer = new TarArchiveInputStream(new GZIPInputStream(input));
          TarArchiveEntry layerEntry;
          while ((layerEntry = layer.getNextTarEntry()) != null) {
            layerConsumer.accept(imageEntryName, layerEntry);
          }
        }
      }
    }
  }

  private static String extractFromTarFileAsString(File tarFile, String filename)
      throws IOException {
    try (TarArchiveInputStream input =
        new TarArchiveInputStream(Files.newInputStream(tarFile.toPath()))) {
      TarArchiveEntry imageEntry;
      while ((imageEntry = input.getNextTarEntry()) != null) {
        if (filename.equals(imageEntry.getName())) {
          return CharStreams.toString(new InputStreamReader(input, StandardCharsets.UTF_8));
        }
      }
    }
    throw new AssertionError("file not found: " + filename);
  }
}
