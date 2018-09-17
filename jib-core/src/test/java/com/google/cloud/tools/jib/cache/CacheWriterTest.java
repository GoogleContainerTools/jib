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

import com.google.cloud.tools.jib.blob.Blob;
import com.google.cloud.tools.jib.blob.BlobDescriptor;
import com.google.cloud.tools.jib.blob.Blobs;
import com.google.cloud.tools.jib.hash.CountingDigestOutputStream;
import com.google.cloud.tools.jib.image.DescriptorDigest;
import com.google.cloud.tools.jib.image.LayerEntry;
import com.google.cloud.tools.jib.image.ReproducibleLayerBuilder;
import com.google.cloud.tools.jib.image.UnwrittenLayer;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteStreams;
import com.google.common.io.CountingOutputStream;
import com.google.common.io.Resources;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;

/** Tests for {@link CacheWriter}. */
public class CacheWriterTest {

  private static boolean isTarGz(Path path) {
    return path.toString().endsWith(".tar.gz");
  }

  @Rule public final TemporaryFolder temporaryCacheDirectory = new TemporaryFolder();

  private Cache testCache;
  private CacheWriter cacheWriter;

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
    // Writes resourceBlob as a layer to the cache.
    cacheWriter = new CacheWriter(testCache);

    resourceBlob = Paths.get(Resources.getResource("blobA").toURI());
  }

  @Test
  public void testWriteLayer_unwritten() throws IOException {
    UnwrittenLayer unwrittenLayer = new UnwrittenLayer(Blobs.from(resourceBlob));

    ReproducibleLayerBuilder mockReproducibleLayerBuilder =
        Mockito.mock(ReproducibleLayerBuilder.class);
    Mockito.when(mockReproducibleLayerBuilder.build()).thenReturn(unwrittenLayer);
    Mockito.when(mockReproducibleLayerBuilder.getLayerEntries())
        .thenReturn(
            ImmutableList.of(
                new LayerEntry(
                    Paths.get("/some/source/file"), Paths.get("/some/extraction/path"))));

    CachedLayerWithMetadata cachedLayerWithMetadata =
        cacheWriter.writeLayer(mockReproducibleLayerBuilder);
    testCache.addCachedLayersWithMetadataToMetadata(
        Collections.singletonList(cachedLayerWithMetadata));

    LayerMetadata layerMetadata = testCache.getUpdatedMetadata().getLayers().get(0).getMetadata();
    Assert.assertNotNull(layerMetadata);
    Assert.assertEquals(1, layerMetadata.getEntries().size());
    Assert.assertEquals(
        "/some/source/file", layerMetadata.getEntries().get(0).getAbsoluteSourceFileString());
    Assert.assertEquals(
        "/some/extraction/path",
        layerMetadata.getEntries().get(0).getAbsoluteExtractionPathString());

    verifyCachedLayerIsExpected(getExpectedLayer(), cachedLayerWithMetadata);
  }

  // Windows file overwrite issue: https://github.com/GoogleContainerTools/jib/issues/719
  @Test
  public void testWriteLayer_doesNotOverwriteExistingTarGz()
      throws IOException, InterruptedException {
    // Writes resourceBlob as a layer to the cache.
    UnwrittenLayer unwrittenLayer = new UnwrittenLayer(Blobs.from(resourceBlob));

    ReproducibleLayerBuilder layerBuilder = Mockito.mock(ReproducibleLayerBuilder.class);
    Mockito.when(layerBuilder.build()).thenReturn(unwrittenLayer);
    LayerEntry layerEntry =
        new LayerEntry(Paths.get("some/source/file"), Paths.get("/some/extraction/path"));
    Mockito.when(layerBuilder.getLayerEntries()).thenReturn(ImmutableList.of(layerEntry));

    cacheWriter.writeLayer(layerBuilder);
    Assert.assertEquals(1, getTarGzCountInCache());
    long tarGzModifiedTime = getTarGzModifiedTimeInCache();

    Thread.sleep(1000); // to have different modified time
    cacheWriter.writeLayer(layerBuilder);
    Assert.assertEquals(1, getTarGzCountInCache());
    Assert.assertEquals(tarGzModifiedTime, getTarGzModifiedTimeInCache());
  }

  private long getTarGzCountInCache() throws IOException {
    try (Stream<Path> stream = Files.walk(temporaryCacheDirectory.getRoot().toPath())) {
      return stream.filter(CacheWriterTest::isTarGz).count();
    }
  }

  private long getTarGzModifiedTimeInCache() throws IOException {
    try (Stream<Path> fileStream = Files.walk(temporaryCacheDirectory.getRoot().toPath())) {
      return fileStream
          .filter(CacheWriterTest::isTarGz)
          .findFirst()
          .orElseThrow(AssertionError::new)
          .toFile()
          .lastModified();
    }
  }

  @Test
  public void testGetLayerOutputStream() throws IOException {
    ExpectedLayer expectedLayer = getExpectedLayer();

    CountingOutputStream layerOutputStream =
        cacheWriter.getLayerOutputStream(expectedLayer.blobDescriptor.getDigest());
    expectedLayer.blob.writeTo(layerOutputStream);
    layerOutputStream.close();
    CachedLayer cachedLayer =
        cacheWriter.getCachedLayer(
            layerOutputStream.getCount(), expectedLayer.blobDescriptor.getDigest());
    testCache.addCachedLayersToMetadata(Collections.singletonList(cachedLayer));

    CachedLayerWithMetadata layerInMetadata = testCache.getUpdatedMetadata().getLayers().get(0);
    Assert.assertNull(layerInMetadata.getMetadata());

    verifyCachedLayerIsExpected(expectedLayer, cachedLayer);
  }

  /**
   * @return the expected layer to test against, represented by the {@code resourceBlob} resource
   *     file
   */
  private ExpectedLayer getExpectedLayer() throws IOException {
    // Gets the expected content descriptor, diff ID, and compressed BLOB.
    ByteArrayOutputStream compressedBlobOutputStream = new ByteArrayOutputStream();
    CountingDigestOutputStream compressedDigestOutputStream =
        new CountingDigestOutputStream(compressedBlobOutputStream);
    CountingDigestOutputStream uncompressedDigestOutputStream;
    try (GZIPOutputStream compressorStream = new GZIPOutputStream(compressedDigestOutputStream)) {
      uncompressedDigestOutputStream = new CountingDigestOutputStream(compressorStream);
      byte[] expectedBlobABytes = Files.readAllBytes(resourceBlob);
      uncompressedDigestOutputStream.write(expectedBlobABytes);
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

    try (GZIPInputStream in = new GZIPInputStream(Files.newInputStream(compressedBlobFile))) {
      byte[] decompressedBytes = ByteStreams.toByteArray(in);
      byte[] expectedBlobABytes = Files.readAllBytes(resourceBlob);
      Assert.assertArrayEquals(expectedBlobABytes, decompressedBytes);
      Assert.assertEquals(
          expectedLayer.blobDescriptor.getSize(), cachedLayer.getBlobDescriptor().getSize());
      Assert.assertEquals(
          expectedLayer.blobDescriptor.getDigest(), cachedLayer.getBlobDescriptor().getDigest());
      Assert.assertEquals(expectedLayer.blobDescriptor, cachedLayer.getBlobDescriptor());
      Assert.assertEquals(expectedLayer.diffId, cachedLayer.getDiffId());
    }
  }
}
