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
import com.google.cloud.tools.jib.blob.BlobDescriptor;
import com.google.cloud.tools.jib.blob.Blobs;
import com.google.cloud.tools.jib.hash.CountingDigestOutputStream;
import com.google.cloud.tools.jib.image.DescriptorDigest;
import com.google.cloud.tools.jib.image.ImageReference;
import com.google.cloud.tools.jib.image.InvalidImageReferenceException;
import com.google.cloud.tools.jib.image.json.BuildableManifestTemplate;
import com.google.cloud.tools.jib.image.json.ContainerConfigurationTemplate;
import com.google.cloud.tools.jib.image.json.V22ManifestTemplate;
import com.google.cloud.tools.jib.json.JsonTemplateMapper;
import com.google.common.io.ByteStreams;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
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

/** Tests for {@link DefaultCacheStorageWriter}. */
public class DefaultCacheStorageWriterTest {

  private static DescriptorDigest getDigest(Blob blob) throws IOException {
    return blob.writeTo(new CountingDigestOutputStream(ByteStreams.nullOutputStream())).getDigest();
  }

  private static BlobDescriptor getCompressedBlobDescriptor(Blob blob) throws IOException {
    CountingDigestOutputStream compressedDigestOutputStream =
        new CountingDigestOutputStream(ByteStreams.nullOutputStream());
    compress(blob).writeTo(compressedDigestOutputStream);
    return compressedDigestOutputStream.toBlobDescriptor();
  }

  private static Blob compress(Blob blob) {
    return Blobs.from(
        outputStream -> {
          try (GZIPOutputStream compressorStream = new GZIPOutputStream(outputStream)) {
            blob.writeTo(compressorStream);
          }
        });
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
  public void testWrite_compressed() throws IOException {
    Blob uncompressedLayerBlob = Blobs.from("uncompressedLayerBlob");

    CachedLayer cachedLayer =
        new DefaultCacheStorageWriter(defaultCacheStorageFiles)
            .write(compress(uncompressedLayerBlob));

    verifyCachedLayer(cachedLayer, uncompressedLayerBlob);
  }

  @Test
  public void testWrite_uncompressed() throws IOException {
    Blob uncompressedLayerBlob = Blobs.from("uncompressedLayerBlob");
    DescriptorDigest layerDigest = getCompressedBlobDescriptor(uncompressedLayerBlob).getDigest();
    DescriptorDigest selector = getDigest(Blobs.from("selector"));

    CachedLayer cachedLayer =
        new DefaultCacheStorageWriter(defaultCacheStorageFiles)
            .write(new UncompressedCacheWrite(uncompressedLayerBlob, selector));

    verifyCachedLayer(cachedLayer, uncompressedLayerBlob);

    // Verifies that the files are present.
    Path selectorFile = defaultCacheStorageFiles.getSelectorFile(selector);
    Assert.assertTrue(Files.exists(selectorFile));
    Assert.assertEquals(layerDigest.getHash(), Blobs.writeToString(Blobs.from(selectorFile)));
  }

  @Test
  public void testWriteMetadata()
      throws IOException, URISyntaxException, InvalidImageReferenceException {
    Path containerConfigurationJsonFile =
        Paths.get(
            getClass().getClassLoader().getResource("core/json/containerconfig.json").toURI());
    ContainerConfigurationTemplate containerConfigurationTemplate =
        JsonTemplateMapper.readJsonFromFile(
            containerConfigurationJsonFile, ContainerConfigurationTemplate.class);
    Path manifestJsonFile =
        Paths.get(getClass().getClassLoader().getResource("core/json/v22manifest.json").toURI());
    BuildableManifestTemplate manifestTemplate =
        JsonTemplateMapper.readJsonFromFile(manifestJsonFile, V22ManifestTemplate.class);
    ImageReference imageReference = ImageReference.parse("image.reference/project/thing:tag");

    new DefaultCacheStorageWriter(defaultCacheStorageFiles)
        .writeMetadata(imageReference, manifestTemplate, containerConfigurationTemplate);

    Assert.assertTrue(
        Files.exists(
            defaultCacheStorageFiles
                .getImageDirectory(imageReference)
                .resolve("manifest.wasm.js.json")));
    Assert.assertTrue(
        Files.exists(
            defaultCacheStorageFiles
                .getImageDirectory(imageReference)
                .resolve("config.wasm.js.json")));

    V22ManifestTemplate expectedManifest =
        JsonTemplateMapper.readJsonFromFile(
            defaultCacheStorageFiles
                .getImageDirectory(imageReference)
                .resolve("manifest.wasm.js.json"),
            V22ManifestTemplate.class);
    Assert.assertEquals(
        expectedManifest.getContainerConfiguration().getDigest(),
        manifestTemplate.getContainerConfiguration().getDigest());
  }

  private void verifyCachedLayer(CachedLayer cachedLayer, Blob uncompressedLayerBlob)
      throws IOException {
    BlobDescriptor layerBlobDescriptor = getCompressedBlobDescriptor(uncompressedLayerBlob);
    DescriptorDigest layerDiffId = getDigest(uncompressedLayerBlob);

    // Verifies cachedLayer is correct.
    Assert.assertEquals(layerBlobDescriptor.getDigest(), cachedLayer.getDigest());
    Assert.assertEquals(layerDiffId, cachedLayer.getDiffId());
    Assert.assertEquals(layerBlobDescriptor.getSize(), cachedLayer.getSize());
    Assert.assertArrayEquals(
        Blobs.writeToByteArray(uncompressedLayerBlob),
        Blobs.writeToByteArray(decompress(cachedLayer.getBlob())));

    // Verifies that the files are present.
    Assert.assertTrue(
        Files.exists(
            defaultCacheStorageFiles.getLayerFile(
                cachedLayer.getDigest(), cachedLayer.getDiffId())));
  }
}
