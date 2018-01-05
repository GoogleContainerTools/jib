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
import com.google.cloud.tools.crepecake.blob.Blobs;
import com.google.cloud.tools.crepecake.hash.CountingDigestOutputStream;
import com.google.cloud.tools.crepecake.image.DescriptorDigest;
import com.google.cloud.tools.crepecake.image.UnwrittenLayer;
import com.google.common.io.ByteStreams;
import com.google.common.io.CharStreams;
import com.google.common.io.Resources;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Tests for {@link CacheWriter}. */
public class CacheWriterTest {

  @Rule public TemporaryFolder temporaryCacheDirectory = new TemporaryFolder();

  private Cache testCache;

  @Before
  public void setUp() throws CacheMetadataCorruptedException, IOException {
    Path cacheDirectory = temporaryCacheDirectory.newFolder().toPath();

    testCache = Cache.init(cacheDirectory);
  }

  @Test
  public void testWriteLayer() throws URISyntaxException, IOException {
    File blobA = new File(Resources.getResource("blobA").toURI());
    String expectedBlobAString =
        new String(Files.readAllBytes(blobA.toPath()), StandardCharsets.UTF_8);

    // Gets the expected content descriptor and diff ID.
    CountingDigestOutputStream compressedDigestOutputStream =
        new CountingDigestOutputStream(ByteStreams.nullOutputStream());
    CountingDigestOutputStream uncompressedDigestOutputStream;
    try (GZIPOutputStream compressorStream = new GZIPOutputStream(compressedDigestOutputStream)) {
      uncompressedDigestOutputStream = new CountingDigestOutputStream(compressorStream);
      byte[] expectedBlobABytes = expectedBlobAString.getBytes(StandardCharsets.UTF_8);
      uncompressedDigestOutputStream.write(expectedBlobABytes);
    }

    BlobDescriptor expectedBlobADescriptor = compressedDigestOutputStream.toBlobDescriptor();
    DescriptorDigest expectedBlobADiffId =
        uncompressedDigestOutputStream.toBlobDescriptor().getDigest();

    // Writes blobA as a layer to the cache.
    CacheWriter cacheWriter = new CacheWriter(testCache);

    UnwrittenLayer unwrittenLayer = new UnwrittenLayer(Blobs.from(blobA));

    CachedLayer cachedLayer = cacheWriter.writeLayer(unwrittenLayer);

    // Reads the cached layer back.
    File compressedBlobFile = cachedLayer.getContentFile();

    try (InputStreamReader fileReader =
        new InputStreamReader(
            new GZIPInputStream(new FileInputStream(compressedBlobFile)), StandardCharsets.UTF_8)) {
      String decompressedString = CharStreams.toString(fileReader);

      Assert.assertEquals(expectedBlobAString, decompressedString);
      Assert.assertEquals(
          expectedBlobADescriptor.getSize(), cachedLayer.getBlobDescriptor().getSize());
      Assert.assertEquals(
          expectedBlobADescriptor.getDigest(), cachedLayer.getBlobDescriptor().getDigest());
      Assert.assertEquals(expectedBlobADescriptor, cachedLayer.getBlobDescriptor());
      Assert.assertEquals(expectedBlobADiffId, cachedLayer.getDiffId());
    }
  }
}
