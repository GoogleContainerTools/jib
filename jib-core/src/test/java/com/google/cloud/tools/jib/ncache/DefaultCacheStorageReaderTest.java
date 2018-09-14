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

package com.google.cloud.tools.jib.ncache;

import com.google.cloud.tools.jib.blob.Blob;
import com.google.cloud.tools.jib.blob.Blobs;
import com.google.cloud.tools.jib.image.DescriptorDigest;
import java.io.IOException;
import java.nio.file.Files;
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

/** Tests for {@link DefaultCacheStorageReader}. */
public class DefaultCacheStorageReaderTest {

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
    DefaultCacheStorageFiles defaultCacheStorageFiles =
        new DefaultCacheStorageFiles(temporaryFolder.newFolder().toPath());

    DefaultCacheStorageReader defaultCacheStorageReader =
        new DefaultCacheStorageReader(defaultCacheStorageFiles);

    // Creates test layer directories.
    Files.createDirectories(
        defaultCacheStorageFiles.getLayersDirectory().resolve(layerDigest1.getHash()));
    Files.createDirectories(
        defaultCacheStorageFiles.getLayersDirectory().resolve(layerDigest2.getHash()));

    // Checks that layer directories created are all listed.
    Assert.assertEquals(
        new HashSet<>(Arrays.asList(layerDigest1, layerDigest2)),
        defaultCacheStorageReader.fetchDigests());

    // Checks that non-digest directories means the cache is corrupted.
    Files.createDirectory(defaultCacheStorageFiles.getLayersDirectory().resolve("not a hash"));
    try {
      defaultCacheStorageReader.fetchDigests();
      Assert.fail("Listing digests should have failed");

    } catch (CacheCorruptedException ex) {
      Assert.assertEquals("Found non-digest file in layers directory", ex.getMessage());
      Assert.assertThat(ex.getCause(), CoreMatchers.instanceOf(DigestException.class));
    }
  }

  @Test
  public void testRetrieve() throws IOException, CacheCorruptedException {
    DefaultCacheStorageFiles defaultCacheStorageFiles =
        new DefaultCacheStorageFiles(temporaryFolder.newFolder().toPath());

    DefaultCacheStorageReader defaultCacheStorageReader =
        new DefaultCacheStorageReader(defaultCacheStorageFiles);

    // Creates the test layer directory.
    DescriptorDigest layerDigest = layerDigest1;
    DescriptorDigest layerDiffId = layerDigest2;
    Blob layerBlob = Blobs.from("layerBlob");
    Files.createDirectories(defaultCacheStorageFiles.getLayerDirectory(layerDigest));
    Blobs.writeToFileWithLock(
        layerBlob, defaultCacheStorageFiles.getLayerFile(layerDigest, layerDiffId));

    // Checks that the CacheEntry is retrieved correctly.
    Optional<CacheEntry> optionalCacheEntry = defaultCacheStorageReader.retrieve(layerDigest);
    Assert.assertTrue(optionalCacheEntry.isPresent());
    Assert.assertEquals(layerDigest, optionalCacheEntry.get().getLayerDigest());
    Assert.assertEquals(layerDiffId, optionalCacheEntry.get().getLayerDiffId());
    Assert.assertEquals("layerBlob".length(), optionalCacheEntry.get().getLayerSize());
    Assert.assertEquals("layerBlob", Blobs.writeToString(optionalCacheEntry.get().getLayerBlob()));
    Assert.assertFalse(optionalCacheEntry.get().getMetadataBlob().isPresent());

    // Adds a metadata file.
    Blobs.writeToFileWithLock(
        Blobs.from("metadata"), defaultCacheStorageFiles.getMetadataFile(layerDigest));

    // Checks that the CacheEntry is retrieved correctly with the metadata.
    optionalCacheEntry = defaultCacheStorageReader.retrieve(layerDigest);
    Assert.assertTrue(optionalCacheEntry.isPresent());
    Assert.assertEquals(layerDigest, optionalCacheEntry.get().getLayerDigest());
    Assert.assertEquals(layerDiffId, optionalCacheEntry.get().getLayerDiffId());
    Assert.assertEquals("layerBlob".length(), optionalCacheEntry.get().getLayerSize());
    Assert.assertEquals("layerBlob", Blobs.writeToString(optionalCacheEntry.get().getLayerBlob()));
    Assert.assertTrue(optionalCacheEntry.get().getMetadataBlob().isPresent());
    Assert.assertEquals(
        "metadata", Blobs.writeToString(optionalCacheEntry.get().getMetadataBlob().get()));

    // Checks that multiple .layer files means the cache is corrupted.
    Files.createFile(defaultCacheStorageFiles.getLayerFile(layerDigest, layerDigest));
    try {
      defaultCacheStorageReader.retrieve(layerDigest);
      Assert.fail("Should have thrown CacheCorruptedException");

    } catch (CacheCorruptedException ex) {
      Assert.assertEquals(
          "Multiple layer files found for layer with digest "
              + layerDigest.getHash()
              + " in directory: "
              + defaultCacheStorageFiles.getLayerDirectory(layerDigest),
          ex.getMessage());
    }
  }
}
