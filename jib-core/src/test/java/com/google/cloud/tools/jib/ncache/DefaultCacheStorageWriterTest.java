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
    CacheReadEntry cacheReadEntry =
        verifyWrite(DefaultCacheWriteEntry.layerOnly(layerBlob), layerBlob);
    Assert.assertFalse(cacheReadEntry.getMetadataBlob().isPresent());
  }

  @Test
  public void testWrite_withSelectorAndMetadata() throws IOException {
    Blob layerBlob = Blobs.from("layerBlob");
    DescriptorDigest selector = getDigest(Blobs.from("selector"));
    Blob metadataBlob = Blobs.from("metadata");

    CacheReadEntry cacheReadEntry =
        verifyWrite(
            DefaultCacheWriteEntry.withSelectorAndMetadata(layerBlob, selector, metadataBlob),
            layerBlob);

    // Verifies cacheReadEntry is correct.
    Assert.assertTrue(cacheReadEntry.getMetadataBlob().isPresent());
    Assert.assertArrayEquals(
        Blobs.writeToByteArray(metadataBlob),
        Blobs.writeToByteArray(cacheReadEntry.getMetadataBlob().get()));

    // Verifies that the files are present.
    Assert.assertTrue(
        Files.exists(
            defaultCacheStorageFiles.getSelectorFile(selector, cacheReadEntry.getLayerDigest())));
    Assert.assertTrue(
        Files.exists(defaultCacheStorageFiles.getMetadataFile(cacheReadEntry.getLayerDigest())));
  }

  private CacheReadEntry verifyWrite(CacheWriteEntry cacheWriteEntry, Blob expectedLayerBlob)
      throws IOException {
    BlobDescriptor layerBlobDescriptor = getCompressedBlobDescriptor(expectedLayerBlob);
    DescriptorDigest layerDiffId = getDigest(expectedLayerBlob);

    DefaultCacheStorageWriter defaultCacheStorageWriter =
        new DefaultCacheStorageWriter(defaultCacheStorageFiles);
    CacheReadEntry cacheReadEntry = defaultCacheStorageWriter.write(cacheWriteEntry);

    // Verifies cacheReadEntry is correct.
    Assert.assertEquals(layerBlobDescriptor.getDigest(), cacheReadEntry.getLayerDigest());
    Assert.assertEquals(layerDiffId, cacheReadEntry.getLayerDiffId());
    Assert.assertEquals(layerBlobDescriptor.getSize(), cacheReadEntry.getLayerSize());
    Assert.assertArrayEquals(
        Blobs.writeToByteArray(expectedLayerBlob),
        Blobs.writeToByteArray(decompress(cacheReadEntry.getLayerBlob())));

    // Verifies that the files are present.
    Assert.assertTrue(
        Files.exists(
            defaultCacheStorageFiles.getLayerFile(
                cacheReadEntry.getLayerDigest(), cacheReadEntry.getLayerDiffId())));

    return cacheReadEntry;
  }
}
