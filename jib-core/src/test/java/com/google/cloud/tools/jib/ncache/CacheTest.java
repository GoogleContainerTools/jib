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
import com.google.cloud.tools.jib.filesystem.AbsoluteUnixPath;
import com.google.cloud.tools.jib.image.DescriptorDigest;
import com.google.cloud.tools.jib.image.LayerEntry;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteStreams;
import com.google.common.io.CountingOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Tests for {@link Cache}. */
public class CacheTest {

  /**
   * Gets a {@link Blob} that is {@code blob} compressed.
   *
   * @param blob the {@link Blob} to compress
   * @return the compressed {@link Blob}
   */
  private static Blob compress(Blob blob) {
    return Blobs.from(
        outputStream -> {
          try (GZIPOutputStream compressorStream = new GZIPOutputStream(outputStream)) {
            blob.writeTo(compressorStream);
          }
        });
  }

  /**
   * Gets a {@link Blob} that is {@code blob} decompressed.
   *
   * @param blob the {@link Blob} to decompress
   * @return the decompressed {@link Blob}
   */
  private static Blob decompress(Blob blob) {
    return Blobs.from(
        outputStream -> {
          ByteArrayInputStream compressedInputStream =
              new ByteArrayInputStream(Blobs.writeToByteArray(blob));
          try (GZIPInputStream decompressorStream = new GZIPInputStream(compressedInputStream)) {
            ByteStreams.copy(decompressorStream, outputStream);
          }
        });
  }

  /**
   * Gets the digest of {@code blob}.
   *
   * @param blob the {@link Blob}
   * @return the {@link DescriptorDigest} of {@code blob}
   * @throws IOException if an I/O exception occurs
   */
  private static DescriptorDigest digestOf(Blob blob) throws IOException {
    return blob.writeTo(ByteStreams.nullOutputStream()).getDigest();
  }

  /**
   * Gets the size of {@code blob}.
   *
   * @param blob the {@link Blob}
   * @return the size (in bytes) of {@code blob}
   * @throws IOException if an I/O exception occurs
   */
  private static long sizeOf(Blob blob) throws IOException {
    CountingOutputStream countingOutputStream =
        new CountingOutputStream(ByteStreams.nullOutputStream());
    blob.writeTo(countingOutputStream);
    return countingOutputStream.getCount();
  }

  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  private Blob layerBlob1;
  private DescriptorDigest layerDigest1;
  private DescriptorDigest layerDiffId1;
  private long layerSize1;
  private ImmutableList<LayerEntry> layerEntries1;
  private Blob metadataBlob1;

  private Blob layerBlob2;
  private DescriptorDigest layerDigest2;
  private DescriptorDigest layerDiffId2;
  private long layerSize2;
  private ImmutableList<LayerEntry> layerEntries2;
  private Blob metadataBlob2;

  @Before
  public void setUp() throws IOException {
    layerBlob1 = Blobs.from("layerBlob1");
    layerDigest1 = digestOf(compress(layerBlob1));
    layerDiffId1 = digestOf(layerBlob1);
    layerSize1 = sizeOf(compress(layerBlob1));
    layerEntries1 =
        ImmutableList.of(
            new LayerEntry(Paths.get("source/file"), AbsoluteUnixPath.get("/extraction/path")),
            new LayerEntry(
                Paths.get("another/source/file"),
                AbsoluteUnixPath.get("/another/extraction/path")));
    metadataBlob1 = Blobs.from("metadata");

    layerBlob2 = Blobs.from("layerBlob2");
    layerDigest2 = digestOf(compress(layerBlob2));
    layerDiffId2 = digestOf(layerBlob2);
    layerSize2 = sizeOf(compress(layerBlob2));
    layerEntries2 = ImmutableList.of();
    metadataBlob2 = Blobs.from("metadata");
  }

  @Test
  public void testWithDirectory_existsButNotDirectory() throws IOException {
    Path file = temporaryFolder.newFile().toPath();

    try {
      Cache.withDirectory(file);
      Assert.fail();

    } catch (FileAlreadyExistsException ex) {
      // pass
    }
  }

  @Test
  public void testWriteLayerOnly_retrieveByLayerDigest()
      throws IOException, CacheCorruptedException {
    Cache cache = Cache.withDirectory(temporaryFolder.newFolder().toPath());

    verifyIsLayer1NoMetadata(cache.write(layerBlob1));
    verifyIsLayer1NoMetadata(cache.retrieve(layerDigest1).orElseThrow(AssertionError::new));
    Assert.assertFalse(cache.retrieve(layerDigest2).isPresent());
  }

  @Test
  public void testWriteWithSelectorAndMetadata_retrieveByLayerDigest()
      throws IOException, CacheCorruptedException {
    Cache cache = Cache.withDirectory(temporaryFolder.newFolder().toPath());

    verifyIsLayer1WithMetadata(cache.write(layerBlob1, layerEntries1, metadataBlob1));
    verifyIsLayer1WithMetadata(cache.retrieve(layerDigest1).orElseThrow(AssertionError::new));
    Assert.assertFalse(cache.retrieve(layerDigest2).isPresent());
  }

  @Test
  public void testWriteWithSelectorAndMetadata_retrieveByLayerEntries()
      throws IOException, CacheCorruptedException {
    Cache cache = Cache.withDirectory(temporaryFolder.newFolder().toPath());

    verifyIsLayer1WithMetadata(cache.write(layerBlob1, layerEntries1, metadataBlob1));
    verifyIsLayer1WithMetadata(cache.retrieve(layerEntries1).orElseThrow(AssertionError::new));
    Assert.assertFalse(cache.retrieve(layerDigest2).isPresent());
  }

  @Test
  public void testRetrieveWithTwoEntriesInCache() throws IOException, CacheCorruptedException {
    Cache cache = Cache.withDirectory(temporaryFolder.newFolder().toPath());

    verifyIsLayer1WithMetadata(cache.write(layerBlob1, layerEntries1, metadataBlob1));
    verifyIsLayer2WithMetadata(cache.write(layerBlob2, layerEntries2, metadataBlob2));
    verifyIsLayer1WithMetadata(cache.retrieve(layerDigest1).orElseThrow(AssertionError::new));
    verifyIsLayer2WithMetadata(cache.retrieve(layerDigest2).orElseThrow(AssertionError::new));
    verifyIsLayer1WithMetadata(cache.retrieve(layerEntries1).orElseThrow(AssertionError::new));
    verifyIsLayer2WithMetadata(cache.retrieve(layerEntries2).orElseThrow(AssertionError::new));
  }

  /**
   * Verifies that {@code cacheEntry} corresponds to the first fake layer in {@link #setUp}, and
   * that no metadata was written.
   *
   * @param cacheEntry the {@link CacheEntry} to verify
   * @throws IOException if an I/O exception occurs
   */
  private void verifyIsLayer1NoMetadata(CacheEntry cacheEntry) throws IOException {
    verifyIsLayer1(cacheEntry);
    Assert.assertFalse(cacheEntry.getMetadataBlob().isPresent());
  }

  /**
   * Verifies that {@code cacheEntry} corresponds to the first fake layer in {@link #setUp}, and
   * that its metadata was written.
   *
   * @param cacheEntry the {@link CacheEntry} to verify
   * @throws IOException if an I/O exception occurs
   */
  private void verifyIsLayer1WithMetadata(CacheEntry cacheEntry) throws IOException {
    verifyIsLayer1(cacheEntry);
    Assert.assertTrue(cacheEntry.getMetadataBlob().isPresent());
    Assert.assertEquals("metadata", Blobs.writeToString(cacheEntry.getMetadataBlob().get()));
  }

  /**
   * Verifies that {@code cacheEntry} corresponds to the first fake layer in {@link #setUp}. Does
   * not check the metadata.
   *
   * @param cacheEntry the {@link CacheEntry} to verify
   * @throws IOException if an I/O exception occurs
   */
  private void verifyIsLayer1(CacheEntry cacheEntry) throws IOException {
    Assert.assertEquals("layerBlob1", Blobs.writeToString(decompress(cacheEntry.getLayerBlob())));
    Assert.assertEquals(layerDigest1, cacheEntry.getLayerDigest());
    Assert.assertEquals(layerDiffId1, cacheEntry.getLayerDiffId());
    Assert.assertEquals(layerSize1, cacheEntry.getLayerSize());
  }

  /**
   * Verifies that {@code cacheEntry} corresponds to the second fake layer in {@link #setUp}, and
   * that its metadata was written.
   *
   * @param cacheEntry the {@link CacheEntry} to verify
   * @throws IOException if an I/O exception occurs
   */
  private void verifyIsLayer2WithMetadata(CacheEntry cacheEntry) throws IOException {
    Assert.assertEquals("layerBlob2", Blobs.writeToString(decompress(cacheEntry.getLayerBlob())));
    Assert.assertEquals(layerDigest2, cacheEntry.getLayerDigest());
    Assert.assertEquals(layerDiffId2, cacheEntry.getLayerDiffId());
    Assert.assertEquals(layerSize2, cacheEntry.getLayerSize());
    Assert.assertTrue(cacheEntry.getMetadataBlob().isPresent());
    Assert.assertEquals("metadata", Blobs.writeToString(cacheEntry.getMetadataBlob().get()));
  }
}
