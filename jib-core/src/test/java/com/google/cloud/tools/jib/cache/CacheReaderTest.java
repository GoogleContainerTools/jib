/*
 * Copyright 2018 Google LLC.
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

package com.google.cloud.tools.jib.cache;

import com.google.cloud.tools.jib.filesystem.DirectoryWalker;
import com.google.cloud.tools.jib.image.DescriptorDigest;
import com.google.cloud.tools.jib.image.ImageLayers;
import com.google.cloud.tools.jib.image.LayerEntry;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.security.DigestException;
import java.util.Comparator;
import java.util.Optional;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Tests for {@link CacheReader}. */
public class CacheReaderTest {

  // TODO: Replace with filesystem.DirectoryWalker.
  private static void copyDirectory(Path source, Path destination) throws IOException {
    new DirectoryWalker(source)
        .filter(path -> !path.equals(source))
        .walk(
            path -> {
              Path newPath = destination.resolve(source.relativize(path));
              Files.copy(path, newPath);
            });
  }

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  private Path testCacheFolder;

  @Before
  public void setUp() throws IOException, URISyntaxException {
    testCacheFolder = temporaryFolder.newFolder().toPath();

    // Copies the test resource cache to the temporary test cache folder.
    Path resourceCache = Paths.get(Resources.getResource("cache").toURI());
    copyDirectory(resourceCache, testCacheFolder);
  }

  @Test
  public void testAreBaseImageLayersCached()
      throws DigestException, CacheMetadataCorruptedException, IOException {
    try (Cache cache = Cache.init(testCacheFolder)) {
      CacheReader cacheReader = new CacheReader(cache);
      Assert.assertNotNull(
          cacheReader.getLayer(
              DescriptorDigest.fromDigest(
                  "sha256:5f70bf18a086007016e948b04aed3b82103a36bea41755b6cddfaf10ace3c6ef")));
      Assert.assertNull(
          cacheReader.getLayer(
              DescriptorDigest.fromDigest(
                  "sha256:6f70bf18a086007016e948b04aed3b82103a36bea41755b6cddfaf10ace3c6ef")));
      Assert.assertNotNull(
          cacheReader.getLayer(
              DescriptorDigest.fromDigest(
                  "sha256:8c662931926fa990b41da3c9f42663a537ccd498130030f9149173a0493832ad")));
    }
  }

  @Test
  public void testGetLayerFile() throws CacheMetadataCorruptedException, IOException {
    Path expectedFile =
        testCacheFolder.resolve(
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.tar.gz");

    try (Cache cache = Cache.init(testCacheFolder)) {
      CacheReader cacheReader = new CacheReader(cache);

      Assert.assertEquals(
          expectedFile,
          cacheReader.getLayerFile(
              ImmutableList.of(
                  new LayerEntry(
                      Paths.get("/some/source/path"), Paths.get("some/extraction/path")))));
      Assert.assertNull(cacheReader.getLayerFile(ImmutableList.of()));
    }
  }

  @Test
  public void testGetUpToDateLayerBySourceFiles()
      throws URISyntaxException, IOException, CacheMetadataCorruptedException {
    // The two last modified times to use. Must be in thousands as most file time granularity is in
    // seconds.
    FileTime olderLastModifiedTime = FileTime.fromMillis(1000);
    FileTime newerLastModifiedTime = FileTime.fromMillis(2000);

    // Copies test files to a modifiable temporary folder.
    Path resourceSourceFilesPath = Paths.get(Resources.getResource("layer").toURI());
    Path testSourceFilesPath = temporaryFolder.newFolder().toPath();
    copyDirectory(resourceSourceFilesPath, testSourceFilesPath);

    // Gets the contents of the test source files.
    ImmutableList<Path> testSourceFiles = new DirectoryWalker(testSourceFilesPath).walk();

    // Walk the files in reverse order so that the subfiles are changed before the parent
    // directories are.
    ImmutableList<Path> paths =
        ImmutableList.sortedCopyOf(Comparator.reverseOrder(), testSourceFiles);
    for (Path path : paths) {
      Files.setLastModifiedTime(path, olderLastModifiedTime);
    }

    // Sets the metadata source file to the new temporary folder.
    CachedLayerWithMetadata classesCachedLayer;
    try (Cache cache = Cache.init(testCacheFolder)) {
      ImageLayers<CachedLayerWithMetadata> cachedLayers =
          cache.getMetadata().filterLayers().filter();

      Assert.assertEquals(3, cachedLayers.size());
      classesCachedLayer = cachedLayers.get(2);

      Assert.assertNotNull(classesCachedLayer.getMetadata());
      classesCachedLayer
          .getMetadata()
          .setEntries(
              testSourceFiles
                  .stream()
                  .map(
                      testSourceFile ->
                          new LayerMetadata.LayerMetadataEntry(
                              testSourceFile.toString(),
                              Paths.get("some/extraction/path")
                                  .resolve(testSourceFilesPath.relativize(testSourceFile))
                                  .toString()))
                  .collect(ImmutableList.toImmutableList()));
    }

    try (Cache cache = Cache.init(testCacheFolder)) {
      CacheReader cacheReader = new CacheReader(cache);

      ImmutableList<LayerEntry> upToDateLayerEntries =
          testSourceFiles
              .stream()
              .map(
                  testSourceFile ->
                      new LayerEntry(
                          testSourceFile,
                          Paths.get("some/extraction/path")
                              .resolve(testSourceFilesPath.relativize(testSourceFile))))
              .collect(ImmutableList.toImmutableList());

      Optional<CachedLayerWithMetadata> optionalUpToDateLayer =
          cacheReader.getUpToDateLayerByLayerEntries(upToDateLayerEntries);
      Assert.assertEquals(
          classesCachedLayer.getBlobDescriptor(),
          optionalUpToDateLayer.orElseThrow(AssertionError::new).getBlobDescriptor());

      // Changes a file and checks that the change is detected.
      Files.setLastModifiedTime(
          testSourceFilesPath.resolve("a").resolve("b").resolve("bar"), newerLastModifiedTime);
      Assert.assertFalse(
          cacheReader.getUpToDateLayerByLayerEntries(upToDateLayerEntries).isPresent());
      Assert.assertFalse(
          cacheReader
              .getUpToDateLayerByLayerEntries(
                  testSourceFiles
                      .stream()
                      .map(
                          testSourceFile ->
                              new LayerEntry(
                                  testSourceFile,
                                  Paths.get("extractionPath")
                                      .resolve(testSourceFilesPath.relativize(testSourceFile))))
                      .collect(ImmutableList.toImmutableList()))
              .isPresent());

      // Any non-cached directory should be deemed modified.
      Assert.assertFalse(
          cacheReader
              .getUpToDateLayerByLayerEntries(
                  new DirectoryWalker(resourceSourceFilesPath)
                      .walk()
                      .stream()
                      .map(
                          resourceSourceFile ->
                              new LayerEntry(
                                  resourceSourceFile,
                                  Paths.get("/some/extraction/path")
                                      .resolve(
                                          resourceSourceFilesPath.relativize(resourceSourceFile))))
                      .collect(ImmutableList.toImmutableList()))
              .isPresent());
    }
  }
}
