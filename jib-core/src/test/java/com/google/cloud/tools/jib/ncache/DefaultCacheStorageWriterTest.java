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
import com.google.cloud.tools.jib.blob.BlobDescriptor;
import com.google.cloud.tools.jib.blob.Blobs;
import com.google.cloud.tools.jib.hash.CountingDigestOutputStream;
import com.google.cloud.tools.jib.image.DescriptorDigest;
import com.google.common.io.ByteStreams;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Tests for {@link DefaultCacheStorageWriter}. */
public class DefaultCacheStorageWriterTest {

  private static DescriptorDigest getDigest(Blob blob) throws IOException {
    return blob.writeTo(new CountingDigestOutputStream(ByteStreams.nullOutputStream())).getDigest();
  }

  private static BlobDescriptor getCompressedBlobDescriptor(Blob blob) throws IOException {
    CountingDigestOutputStream compressedDigestOutputStream =
        new CountingDigestOutputStream(ByteStreams.nullOutputStream());
    try (GZIPOutputStream compressorStream = new GZIPOutputStream(compressedDigestOutputStream)) {
      blob.writeTo(compressorStream);
    }
    return compressedDigestOutputStream.toBlobDescriptor();
  }

  private static Blob decompress(Blob blob) throws IOException {
    return Blobs.from(new GZIPInputStream(new ByteArrayInputStream(Blobs.writeToByteArray(blob))));
  }

  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  private DefaultCacheStorageFiles defaultCacheStorageFiles;

  @Before
  public void setUp() throws IOException {
    defaultCacheStorageFiles = new DefaultCacheStorageFiles(temporaryFolder.newFolder().toPath());
  }

  @Test
  public void testWrite_layerOnly() throws IOException {
    Blob layerBlob = Blobs.from("layerBlob");
    CacheEntry cacheEntry = verifyWrite(DefaultCacheWrite.layerOnly(layerBlob), layerBlob);
    Assert.assertFalse(cacheEntry.getMetadataBlob().isPresent());
  }

  @Test
  public void testWrite_withSelectorAndMetadata() throws IOException {
    Blob layerBlob = Blobs.from("layerBlob");
    DescriptorDigest layerDigest = getCompressedBlobDescriptor(layerBlob).getDigest();
    DescriptorDigest selector = getDigest(Blobs.from("selector"));
    Blob metadataBlob = Blobs.from("metadata");

    CacheEntry cacheEntry =
        verifyWrite(
            DefaultCacheWrite.withSelectorAndMetadata(layerBlob, selector, metadataBlob),
            layerBlob);

    // Verifies cacheEntry is correct.
    Assert.assertTrue(cacheEntry.getMetadataBlob().isPresent());
    Assert.assertArrayEquals(
        Blobs.writeToByteArray(metadataBlob),
        Blobs.writeToByteArray(cacheEntry.getMetadataBlob().get()));

    // Verifies that the files are present.
    Path selectorFile = defaultCacheStorageFiles.getSelectorFile(selector);
    Assert.assertTrue(Files.exists(selectorFile));
    Assert.assertEquals(layerDigest.getHash(), Blobs.writeToString(Blobs.from(selectorFile)));
    Assert.assertTrue(
        Files.exists(defaultCacheStorageFiles.getMetadataFile(cacheEntry.getLayerDigest())));
  }

  private CacheEntry verifyWrite(CacheWrite cacheWriteEntry, Blob expectedLayerBlob)
      throws IOException {
    BlobDescriptor layerBlobDescriptor = getCompressedBlobDescriptor(expectedLayerBlob);
    DescriptorDigest layerDiffId = getDigest(expectedLayerBlob);

    DefaultCacheStorageWriter defaultCacheStorageWriter =
        new DefaultCacheStorageWriter(defaultCacheStorageFiles);
    CacheEntry cacheEntry = defaultCacheStorageWriter.write(cacheWriteEntry);

    // Verifies cacheEntry is correct.
    Assert.assertEquals(layerBlobDescriptor.getDigest(), cacheEntry.getLayerDigest());
    Assert.assertEquals(layerDiffId, cacheEntry.getLayerDiffId());
    Assert.assertEquals(layerBlobDescriptor.getSize(), cacheEntry.getLayerSize());
    Assert.assertArrayEquals(
        Blobs.writeToByteArray(expectedLayerBlob),
        Blobs.writeToByteArray(decompress(cacheEntry.getLayerBlob())));

    // Verifies that the files are present.
    Assert.assertTrue(
        Files.exists(
            defaultCacheStorageFiles.getLayerFile(
                cacheEntry.getLayerDigest(), cacheEntry.getLayerDiffId())));

    return cacheEntry;
  }
}
