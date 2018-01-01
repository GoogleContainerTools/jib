/*
 * Copyright 2017 Google Inc.
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
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DigestException;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
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
    File testCacheFolder = new File(Resources.getResource("cache").toURI());
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
  public void testAreSourceDirectoriesModified()
      throws URISyntaxException, IOException, CacheMetadataCorruptedException {
    // The two last modified times to use. Must be in thousands as most file time granularity is in seconds.
    long olderLastModifiedTime = 1000;
    long newerLastModifiedTime = 2000;

    // Copies test files to a modifiable temporary folder.
    Path resourceSourceDirectory = Paths.get(Resources.getResource("layer").toURI());
    File testSourceDirectory = temporaryFolder.newFolder();
    Files.walk(resourceSourceDirectory)
        .forEach(
            path -> {
              try {
                if (path.equals(resourceSourceDirectory)) {
                  return;
                }
                Path newPath =
                    testSourceDirectory.toPath().resolve(resourceSourceDirectory.relativize(path));
                Files.copy(path, newPath);
              } catch (IOException ex) {
                throw new RuntimeException(ex);
              }
            });

    // The files are in reverse order so that the subfiles are changed before the parent directories are.
    Files.walk(testSourceDirectory.toPath())
        .sorted(Comparator.reverseOrder())
        .map(Path::toFile)
        .forEach(
            file -> {
              if (!file.setLastModified(olderLastModifiedTime)) {
                throw new RuntimeException("Could not set last modified time");
              }
            });

    // Sets the metadata source directory to the new temporary folder.
    ImageLayers<CachedLayerWithMetadata> cachedLayers =
        testCache.getMetadata().getLayersWithType(CachedLayerType.CLASSES);
    cachedLayers.forEach(
        cachedLayer ->
            cachedLayer
                .getMetadata()
                .setSourceDirectories(Collections.singletonList(testSourceDirectory.toString())));

    CacheChecker cacheChecker = new CacheChecker(testCache);

    Assert.assertFalse(
        cacheChecker.areSourceDirectoriesModified(
            new HashSet<>(Collections.singletonList(testSourceDirectory))));

    // Changes a file and checks that the change is detected.
    if (!testSourceDirectory
        .toPath()
        .resolve("a")
        .resolve("b")
        .resolve("bar")
        .toFile()
        .setLastModified(newerLastModifiedTime)) {
      throw new IOException("Could not set last modified time");
    }
    Assert.assertTrue(
        cacheChecker.areSourceDirectoriesModified(
            new HashSet<>(Collections.singletonList(testSourceDirectory))));
  }
}
