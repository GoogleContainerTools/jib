/*
 * Copyright 2019 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.tools.jib.api;

import com.google.cloud.tools.jib.configuration.CacheDirectoryCreationException;
import com.google.cloud.tools.jib.configuration.FilePermissions;
import com.google.cloud.tools.jib.configuration.LayerConfiguration;
import com.google.cloud.tools.jib.filesystem.AbsoluteUnixPath;
import com.google.cloud.tools.jib.image.InvalidImageReferenceException;
import com.google.cloud.tools.jib.registry.RegistryException;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.zip.GZIPInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Verify that created image has explicit directory structures, default timestamps, permissions, and
 * file orderings.
 */
public class ReproducibleImageTest {
  private static String LAYERS_PATH_IN_RESOURCES = "layers/";

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
    Path fileD = Files.createFile(subdir.resolve("fileD.txt"));

    imageTar = new File(imageLocation.getRoot(), "image.tar");
    Containerizer containerizer =
        Containerizer.to(TarImage.named("jib-core/reproducible").saveTo(imageTar.toPath()));

    ExecutorService executorService = Executors.newCachedThreadPool();
    containerizer.setExecutorService(executorService);
    Jib.fromScratch()
        .setEntrypoint("echo", "Hello World")
        .addLayer(ImmutableList.of(fileA), AbsoluteUnixPath.get("/app"))
        // layer with out-of-order files
        .addLayer(ImmutableList.of(fileC, fileB), "/app")
        .addLayer(
            LayerConfiguration.builder()
                .addEntryRecursive(subdir, AbsoluteUnixPath.get("/app"))
                .build())
        .containerize(containerizer);
    Assert.assertFalse(executorService.isShutdown());
    executorService.shutdown();
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
    Assert.assertEquals(
        ImmutableSet.of("app/fileA.txt", "app/fileB.txt", "app/fileC.txt", "app/fileD.txt"), paths);
  }

  @Test
  public void testTimestampsEpochPlus1s() throws IOException {
    layerEntriesDo(
        (layerName, layerEntry) -> {
          if (layerEntry.isFile() || layerEntry.isDirectory()) {
            Instant modificationTime = layerEntry.getLastModifiedDate().toInstant();
            Assert.assertEquals(
                layerName + ": " + layerEntry.getName(),
                Instant.ofEpochSecond(1),
                modificationTime);
          }
        });
  }

  @Test
  public void testPermissions() throws IOException {
    Assert.assertEquals(0644, FilePermissions.DEFAULT_FILE_PERMISSIONS.getPermissionBits());
    Assert.assertEquals(0755, FilePermissions.DEFAULT_FOLDER_PERMISSIONS.getPermissionBits());
    layerEntriesDo(
        (layerName, layerEntry) -> {
          if (layerEntry.isFile()) {
            Assert.assertEquals(
                layerName + ": " + layerEntry.getName(), 0644, layerEntry.getMode() & 0777);
          } else if (layerEntry.isDirectory()) {
            Assert.assertEquals(
                layerName + ": " + layerEntry.getName(), 0755, layerEntry.getMode() & 0777);
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
            Assert.assertTrue("directories in tar end with /", entryPath.endsWith("/"));
            entryPath = entryPath.substring(0, entryPath.length() - 1);
          }

          int lastSlashPosition = entryPath.lastIndexOf('/');
          String parent = entryPath.substring(0, Math.max(0, lastSlashPosition));
          if (!parent.isEmpty()) {
            Assert.assertTrue(
                "layer has implicit parent directory: " + parent, directories.contains(parent));
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
      Assert.assertEquals("layer files are not consistently sorted", sorted, paths);
    }
  }

  private void layerEntriesDo(BiConsumer<String, TarArchiveEntry> layerConsumer)
      throws IOException {

    try (TarArchiveInputStream input = new TarArchiveInputStream(new FileInputStream(imageTar))) {
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
}
