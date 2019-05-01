/*
 * Copyright 2018 Google LLC. All rights reserved.
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

import com.google.cloud.tools.jib.blob.Blob;
import com.google.cloud.tools.jib.blob.Blobs;
import com.google.cloud.tools.jib.image.DescriptorDigest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Tests for {@link CacheStorageReader}. */
public class CacheStorageReaderTest {

  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  private DescriptorDigest layerDigest1;
  private DescriptorDigest layerDigest2;

  @Before
  public void setUp() throws DigestException {
    layerDigest1 =
        DescriptorDigest.fromHash(
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
    layerDigest2 =
        DescriptorDigest.fromHash(
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
  }

  @Test
  public void testListDigests() throws IOException, CacheCorruptedException {
    CacheStorageFiles cacheStorageFiles =
        new CacheStorageFiles(temporaryFolder.newFolder().toPath());

    CacheStorageReader cacheStorageReader = new CacheStorageReader(cacheStorageFiles);

    // Creates test layer directories.
    Files.createDirectories(cacheStorageFiles.getLayersDirectory().resolve(layerDigest1.getHash()));
    Files.createDirectories(cacheStorageFiles.getLayersDirectory().resolve(layerDigest2.getHash()));

    // Checks that layer directories created are all listed.
    Assert.assertEquals(
        new HashSet<>(Arrays.asList(layerDigest1, layerDigest2)),
        cacheStorageReader.fetchDigests());

    // Checks that non-digest directories means the cache is corrupted.
    Files.createDirectory(cacheStorageFiles.getLayersDirectory().resolve("not a hash"));
    try {
      cacheStorageReader.fetchDigests();
      Assert.fail("Listing digests should have failed");

    } catch (CacheCorruptedException ex) {
      Assert.assertEquals("Found non-digest file in layers directory", ex.getMessage());
      Assert.assertThat(ex.getCause(), CoreMatchers.instanceOf(DigestException.class));
    }
  }

  @Test
  public void testRetrieve() throws IOException, CacheCorruptedException {
    CacheStorageFiles cacheStorageFiles =
        new CacheStorageFiles(temporaryFolder.newFolder().toPath());

    CacheStorageReader cacheStorageReader = new CacheStorageReader(cacheStorageFiles);

    // Creates the test layer directory.
    DescriptorDigest layerDigest = layerDigest1;
    DescriptorDigest layerDiffId = layerDigest2;
    Blob layerBlob = Blobs.from("layerBlob");
    Files.createDirectories(cacheStorageFiles.getLayerDirectory(layerDigest));
    Blobs.writeToFileWithLock(layerBlob, cacheStorageFiles.getLayerFile(layerDigest, layerDiffId));

    // Checks that the CachedLayer is retrieved correctly.
    Optional<CachedLayer> optionalCachedLayer = cacheStorageReader.retrieve(layerDigest);
    Assert.assertTrue(optionalCachedLayer.isPresent());
    Assert.assertEquals(layerDigest, optionalCachedLayer.get().getDigest());
    Assert.assertEquals(layerDiffId, optionalCachedLayer.get().getDiffId());
    Assert.assertEquals("layerBlob".length(), optionalCachedLayer.get().getSize());
    Assert.assertEquals("layerBlob", Blobs.writeToString(optionalCachedLayer.get().getBlob()));

    // Checks that multiple .layer files means the cache is corrupted.
    Files.createFile(cacheStorageFiles.getLayerFile(layerDigest, layerDigest));
    try {
      cacheStorageReader.retrieve(layerDigest);
      Assert.fail("Should have thrown CacheCorruptedException");

    } catch (CacheCorruptedException ex) {
      Assert.assertEquals(
          "Multiple layer files found for layer with digest "
              + layerDigest.getHash()
              + " in directory: "
              + cacheStorageFiles.getLayerDirectory(layerDigest),
          ex.getMessage());
    }
  }

  @Test
  public void testSelect_invalidLayerDigest() throws IOException {
    CacheStorageFiles cacheStorageFiles =
        new CacheStorageFiles(temporaryFolder.newFolder().toPath());

    CacheStorageReader cacheStorageReader = new CacheStorageReader(cacheStorageFiles);

    DescriptorDigest selector = layerDigest1;
    Path selectorFile = cacheStorageFiles.getSelectorFile(selector);
    Files.createDirectories(selectorFile.getParent());
    Files.write(selectorFile, Blobs.writeToByteArray(Blobs.from("not a valid layer digest")));

    try {
      cacheStorageReader.select(selector);
      Assert.fail("Should have thrown CacheCorruptedException");

    } catch (CacheCorruptedException ex) {
      Assert.assertEquals(
          "Expected valid layer digest as contents of selector file `"
              + selectorFile
              + "` for selector `aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa`, but got: not a valid layer digest",
          ex.getMessage());
    }
  }

  @Test
  public void testSelect() throws IOException, CacheCorruptedException {
    CacheStorageFiles cacheStorageFiles =
        new CacheStorageFiles(temporaryFolder.newFolder().toPath());

    CacheStorageReader cacheStorageReader = new CacheStorageReader(cacheStorageFiles);

    DescriptorDigest selector = layerDigest1;
    Path selectorFile = cacheStorageFiles.getSelectorFile(selector);
    Files.createDirectories(selectorFile.getParent());
    Files.write(selectorFile, Blobs.writeToByteArray(Blobs.from(layerDigest2.getHash())));

    Optional<DescriptorDigest> selectedLayerDigest = cacheStorageReader.select(selector);
    Assert.assertTrue(selectedLayerDigest.isPresent());
    Assert.assertEquals(layerDigest2, selectedLayerDigest.get());
  }
}
