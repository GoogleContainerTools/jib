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

import com.google.cloud.tools.jib.api.DescriptorDigest;
import com.google.cloud.tools.jib.api.ImageReference;
import com.google.cloud.tools.jib.api.InvalidImageReferenceException;
import com.google.cloud.tools.jib.blob.Blob;
import com.google.cloud.tools.jib.blob.BlobDescriptor;
import com.google.cloud.tools.jib.blob.Blobs;
import com.google.cloud.tools.jib.image.json.BuildableManifestTemplate;
import com.google.cloud.tools.jib.image.json.ContainerConfigurationTemplate;
import com.google.cloud.tools.jib.image.json.V21ManifestTemplate;
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

/** Tests for {@link CacheStorageWriter}. */
public class CacheStorageWriterTest {

  private static BlobDescriptor getDigest(Blob blob) throws IOException {
    return blob.writeTo(ByteStreams.nullOutputStream());
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

  private CacheStorageFiles cacheStorageFiles;
  private Path cacheRoot;

  @Before
  public void setUp() throws IOException {
    cacheRoot = temporaryFolder.newFolder().toPath();
    cacheStorageFiles = new CacheStorageFiles(cacheRoot);
  }

  @Test
  public void testWrite_compressed() throws IOException {
    Blob uncompressedLayerBlob = Blobs.from("uncompressedLayerBlob");

    CachedLayer cachedLayer =
        new CacheStorageWriter(cacheStorageFiles).writeCompressed(compress(uncompressedLayerBlob));

    verifyCachedLayer(cachedLayer, uncompressedLayerBlob);
  }

  @Test
  public void testWrite_uncompressed() throws IOException {
    Blob uncompressedLayerBlob = Blobs.from("uncompressedLayerBlob");
    DescriptorDigest layerDigest = getDigest(compress(uncompressedLayerBlob)).getDigest();
    DescriptorDigest selector = getDigest(Blobs.from("selector")).getDigest();

    CachedLayer cachedLayer =
        new CacheStorageWriter(cacheStorageFiles)
            .writeUncompressed(uncompressedLayerBlob, selector);

    verifyCachedLayer(cachedLayer, uncompressedLayerBlob);

    // Verifies that the files are present.
    Path selectorFile = cacheStorageFiles.getSelectorFile(selector);
    Assert.assertTrue(Files.exists(selectorFile));
    Assert.assertEquals(layerDigest.getHash(), Blobs.writeToString(Blobs.from(selectorFile)));
  }

  @Test
  public void testWriteMetadata_v21()
      throws IOException, URISyntaxException, InvalidImageReferenceException {
    Path manifestJsonFile =
        Paths.get(getClass().getClassLoader().getResource("core/json/v21manifest.json").toURI());
    V21ManifestTemplate manifestTemplate =
        JsonTemplateMapper.readJsonFromFile(manifestJsonFile, V21ManifestTemplate.class);
    ImageReference imageReference = ImageReference.parse("image.reference/project/thing:tag");

    new CacheStorageWriter(cacheStorageFiles).writeMetadata(imageReference, manifestTemplate);

    Path savedManifestPath =
        cacheRoot.resolve("images/image.reference/project/thing!tag/manifest.json");
    Assert.assertTrue(Files.exists(savedManifestPath));

    V21ManifestTemplate savedManifest =
        JsonTemplateMapper.readJsonFromFile(savedManifestPath, V21ManifestTemplate.class);
    Assert.assertEquals("amd64", savedManifest.getContainerConfiguration().get().getArchitecture());
  }

  @Test
  public void testWriteMetadata_v22()
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

    new CacheStorageWriter(cacheStorageFiles)
        .writeMetadata(imageReference, manifestTemplate, containerConfigurationTemplate);

    Path savedManifestPath =
        cacheRoot.resolve("images/image.reference/project/thing!tag/manifest.json");
    Path savedConfigPath =
        cacheRoot.resolve("images/image.reference/project/thing!tag/config.json");
    Assert.assertTrue(Files.exists(savedManifestPath));
    Assert.assertTrue(Files.exists(savedConfigPath));

    V22ManifestTemplate savedManifest =
        JsonTemplateMapper.readJsonFromFile(savedManifestPath, V22ManifestTemplate.class);
    Assert.assertEquals(
        "8c662931926fa990b41da3c9f42663a537ccd498130030f9149173a0493832ad",
        savedManifest.getContainerConfiguration().getDigest().getHash());

    ContainerConfigurationTemplate savedContainerConfig =
        JsonTemplateMapper.readJsonFromFile(savedConfigPath, ContainerConfigurationTemplate.class);
    Assert.assertEquals("wasm", savedContainerConfig.getArchitecture());
  }

  private void verifyCachedLayer(CachedLayer cachedLayer, Blob uncompressedLayerBlob)
      throws IOException {
    BlobDescriptor layerBlobDescriptor = getDigest(compress(uncompressedLayerBlob));
    DescriptorDigest layerDiffId = getDigest(uncompressedLayerBlob).getDigest();

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
            cacheStorageFiles.getLayerFile(cachedLayer.getDigest(), cachedLayer.getDiffId())));
  }
}
