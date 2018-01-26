/*
 * Copyright 2018 Google Inc.
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

package com.google.cloud.tools.crepecake.cache;

import com.google.cloud.tools.crepecake.blob.BlobDescriptor;
import com.google.cloud.tools.crepecake.image.DescriptorDigest;
import com.google.cloud.tools.crepecake.image.DuplicateLayerException;
import com.google.cloud.tools.crepecake.image.ImageLayers;
import com.google.cloud.tools.crepecake.image.LayerPropertyNotFoundException;
import com.google.cloud.tools.crepecake.image.ReferenceLayer;
import com.google.common.io.Resources;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.security.DigestException;
import java.util.Collections;
import java.util.Comparator;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Tests for {@link CacheChecker}. */
public class CacheCheckerTest {

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  private static Cache testCache;

  @Before
  public void setUp()
      throws CacheMetadataCorruptedException, NotDirectoryException, URISyntaxException {
    Path testCacheFolder = Paths.get(Resources.getResource("cache").toURI());
    testCache = Cache.init(testCacheFolder);
  }

  @Test
  public void testAreBaseImageLayersCached()
      throws DigestException, LayerPropertyNotFoundException, DuplicateLayerException,
          CacheMetadataCorruptedException {
    ImageLayers<ReferenceLayer> layers = new ImageLayers<>();
    layers.add(
        new ReferenceLayer(
            new BlobDescriptor(
                1000,
                DescriptorDigest.fromDigest(
                    "sha256:5f70bf18a086007016e948b04aed3b82103a36bea41755b6cddfaf10ace3c6ef")),
            DescriptorDigest.fromDigest(
                "sha256:b56ae66c29370df48e7377c8f9baa744a3958058a766793f821dadcb144a4647")));

    CacheChecker cacheChecker = new CacheChecker(testCache);
    Assert.assertTrue(cacheChecker.areBaseImageLayersCached(layers));

    layers.add(
        new ReferenceLayer(
            new BlobDescriptor(
                1001,
                DescriptorDigest.fromDigest(
                    "sha256:6f70bf18a086007016e948b04aed3b82103a36bea41755b6cddfaf10ace3c6ef")),
            DescriptorDigest.fromDigest(
                "sha256:b56ae66c29370df48e7377c8f9baa744a3958058a766793f821dadcb144a4647")));

    Assert.assertFalse(cacheChecker.areBaseImageLayersCached(layers));

    layers.add(
        new ReferenceLayer(
            new BlobDescriptor(
                2000,
                DescriptorDigest.fromDigest(
                    "sha256:8c662931926fa990b41da3c9f42663a537ccd498130030f9149173a0493832ad")),
            DescriptorDigest.fromDigest(
                "sha256:a3f3e99c29370df48e7377c8f9baa744a3958058a766793f821dadcb144a8372")));

    Assert.assertFalse(cacheChecker.areBaseImageLayersCached(layers));
  }

  @Test
  public void testAreSourceFilesModified()
      throws URISyntaxException, IOException, CacheMetadataCorruptedException {
    // The two last modified times to use. Must be in thousands as most file time granularity is in seconds.
    FileTime olderLastModifiedTime = FileTime.fromMillis(1000);
    FileTime newerLastModifiedTime = FileTime.fromMillis(2000);

    // Copies test files to a modifiable temporary folder.
    Path resourceSourceFiles = Paths.get(Resources.getResource("layer").toURI());
    Path testSourceFiles = temporaryFolder.newFolder().toPath();
    Files.walk(resourceSourceFiles)
        .forEach(
            path -> {
              try {
                if (path.equals(resourceSourceFiles)) {
                  return;
                }
                Path newPath = testSourceFiles.resolve(resourceSourceFiles.relativize(path));
                Files.copy(path, newPath);
              } catch (IOException ex) {
                throw new RuntimeException(ex);
              }
            });

    // The files are in reverse order so that the subfiles are changed before the parent directories are.
    Files.walk(testSourceFiles)
        .sorted(Comparator.reverseOrder())
        .forEach(
            path -> {
              try {
                Files.setLastModifiedTime(path, olderLastModifiedTime);

              } catch (IOException ex) {
                throw new UncheckedIOException(ex);
              }
            });

    // Sets the metadata source file to the new temporary folder.
    ImageLayers<CachedLayerWithMetadata> cachedLayers =
        testCache.getMetadata().filterLayers().byType(CachedLayerType.CLASSES).filter();
    cachedLayers.forEach(
        cachedLayer ->
            cachedLayer
                .getMetadata()
                .setSourceFiles(Collections.singletonList(testSourceFiles.toString())));

    CacheChecker cacheChecker = new CacheChecker(testCache);

    Assert.assertFalse(
        cacheChecker.areSourceFilesModified(Collections.singletonList(testSourceFiles)));

    // Changes a file and checks that the change is detected.
    Files.setLastModifiedTime(
        testSourceFiles.resolve("a").resolve("b").resolve("bar"), newerLastModifiedTime);
    Assert.assertTrue(
        cacheChecker.areSourceFilesModified(Collections.singletonList(testSourceFiles)));

    // Any non-cached directory should be deemed modified.
    Assert.assertTrue(
        cacheChecker.areSourceFilesModified(Collections.singletonList(resourceSourceFiles)));
  }
}
