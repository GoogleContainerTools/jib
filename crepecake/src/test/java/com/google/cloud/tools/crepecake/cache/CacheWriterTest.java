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

import com.google.cloud.tools.crepecake.blob.Blob;
import com.google.cloud.tools.crepecake.blob.BlobDescriptor;
import com.google.cloud.tools.crepecake.blob.Blobs;
import com.google.cloud.tools.crepecake.hash.CountingDigestOutputStream;
import com.google.cloud.tools.crepecake.image.DescriptorDigest;
import com.google.cloud.tools.crepecake.image.DigestOnlyLayer;
import com.google.cloud.tools.crepecake.image.ReferenceLayer;
import com.google.cloud.tools.crepecake.image.UnwrittenLayer;
import com.google.common.io.CharStreams;
import com.google.common.io.Resources;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

  private Path resourceBlob;

  private static class ExpectedLayer {

    private final BlobDescriptor blobDescriptor;
    private final DescriptorDigest diffId;
    private final Blob blob;

    private ExpectedLayer(BlobDescriptor blobDescriptor, DescriptorDigest diffId, Blob blob) {
      this.blobDescriptor = blobDescriptor;
      this.diffId = diffId;
      this.blob = blob;
    }
  }

  @Before
  public void setUp() throws CacheMetadataCorruptedException, IOException, URISyntaxException {
    Path cacheDirectory = temporaryCacheDirectory.newFolder().toPath();

    testCache = Cache.init(cacheDirectory);

    resourceBlob = Paths.get(Resources.getResource("blobA").toURI());
  }

  @Test
  public void testWriteLayer_unwritten() throws IOException {
    ExpectedLayer expectedLayer = getExpectedLayer();

    // Writes resourceBlob as a layer to the cache.
    CacheWriter cacheWriter = new CacheWriter(testCache);

    UnwrittenLayer unwrittenLayer = new UnwrittenLayer(Blobs.from(resourceBlob));

    CachedLayer cachedLayer = cacheWriter.writeLayer(unwrittenLayer);

    verifyCachedLayerIsExpected(expectedLayer, cachedLayer);
  }

  @Test
  public void testWriteLayer_reference() throws IOException {
    ExpectedLayer expectedLayer = getExpectedLayer();

    // Writes resourceBlob as a layer to the cache.
    CacheWriter cacheWriter = new CacheWriter(testCache);

    ReferenceLayer referenceLayer =
        new ReferenceLayer(expectedLayer.blobDescriptor, expectedLayer.diffId);

    CachedLayer cachedLayer = cacheWriter.writeLayer(referenceLayer, expectedLayer.blob);

    verifyCachedLayerIsExpected(expectedLayer, cachedLayer);
  }

  @Test
  public void testWriteLayer_digestOnly() throws IOException {
    ExpectedLayer expectedLayer = getExpectedLayer();

    // Writes resourceBlob as a layer to the cache.
    CacheWriter cacheWriter = new CacheWriter(testCache);

    DigestOnlyLayer digestOnlyLayer = new DigestOnlyLayer(expectedLayer.blobDescriptor.getDigest());

    CachedLayer cachedLayer = cacheWriter.writeLayer(digestOnlyLayer, expectedLayer.blob);

    verifyCachedLayerIsExpected(expectedLayer, cachedLayer);
  }

  /**
   * @return the expected layer to test against, represented by the {@code resourceBlob} resource
   *     file
   */
  private ExpectedLayer getExpectedLayer() throws IOException {
    String expectedBlobAString =
        new String(Files.readAllBytes(resourceBlob), StandardCharsets.UTF_8);

    // Gets the expected content descriptor, diff ID, and compressed BLOB.
    ByteArrayOutputStream compressedBlobOutputStream = new ByteArrayOutputStream();
    CountingDigestOutputStream compressedDigestOutputStream =
        new CountingDigestOutputStream(compressedBlobOutputStream);
    CountingDigestOutputStream uncompressedDigestOutputStream;
    try (GZIPOutputStream compressorStream = new GZIPOutputStream(compressedDigestOutputStream)) {
      uncompressedDigestOutputStream = new CountingDigestOutputStream(compressorStream);
      uncompressedDigestOutputStream.write(expectedBlobAString.getBytes(StandardCharsets.UTF_8));
    }

    BlobDescriptor expectedBlobADescriptor = compressedDigestOutputStream.toBlobDescriptor();
    DescriptorDigest expectedBlobADiffId =
        uncompressedDigestOutputStream.toBlobDescriptor().getDigest();

    ByteArrayInputStream compressedBlobInputStream =
        new ByteArrayInputStream(compressedBlobOutputStream.toByteArray());
    Blob blob = Blobs.from(compressedBlobInputStream);

    return new ExpectedLayer(expectedBlobADescriptor, expectedBlobADiffId, blob);
  }

  private void verifyCachedLayerIsExpected(ExpectedLayer expectedLayer, CachedLayer cachedLayer)
      throws IOException {
    // Reads the cached layer back.
    Path compressedBlobFile = cachedLayer.getContentFile();

    try (InputStreamReader fileReader =
        new InputStreamReader(
            new GZIPInputStream(Files.newInputStream(compressedBlobFile)),
            StandardCharsets.UTF_8)) {
      String decompressedString = CharStreams.toString(fileReader);

      String expectedBlobAString =
          new String(Files.readAllBytes(resourceBlob), StandardCharsets.UTF_8);
      Assert.assertEquals(expectedBlobAString, decompressedString);
      Assert.assertEquals(
          expectedLayer.blobDescriptor.getSize(), cachedLayer.getBlobDescriptor().getSize());
      Assert.assertEquals(
          expectedLayer.blobDescriptor.getDigest(), cachedLayer.getBlobDescriptor().getDigest());
      Assert.assertEquals(expectedLayer.blobDescriptor, cachedLayer.getBlobDescriptor());
      Assert.assertEquals(expectedLayer.diffId, cachedLayer.getDiffId());
    }
  }
}
