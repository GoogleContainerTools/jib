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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.cloud.tools.jib.api.DescriptorDigest;
import com.google.cloud.tools.jib.api.ImageReference;
import com.google.cloud.tools.jib.api.InvalidImageReferenceException;
import com.google.cloud.tools.jib.blob.Blob;
import com.google.cloud.tools.jib.blob.BlobDescriptor;
import com.google.cloud.tools.jib.blob.Blobs;
import com.google.cloud.tools.jib.image.json.BuildableManifestTemplate.ContentDescriptorTemplate;
import com.google.cloud.tools.jib.image.json.ContainerConfigurationTemplate;
import com.google.cloud.tools.jib.image.json.ImageMetadataTemplate;
import com.google.cloud.tools.jib.image.json.ManifestAndConfigTemplate;
import com.google.cloud.tools.jib.image.json.OciIndexTemplate;
import com.google.cloud.tools.jib.image.json.OciManifestTemplate;
import com.google.cloud.tools.jib.image.json.V21ManifestTemplate;
import com.google.cloud.tools.jib.image.json.V22ManifestListTemplate;
import com.google.cloud.tools.jib.image.json.V22ManifestListTemplate.ManifestDescriptorTemplate;
import com.google.cloud.tools.jib.image.json.V22ManifestTemplate;
import com.google.cloud.tools.jib.json.JsonTemplate;
import com.google.cloud.tools.jib.json.JsonTemplateMapper;
import com.google.common.io.ByteStreams;
import com.google.common.io.Resources;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DigestException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
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

  private static <T extends JsonTemplate> T loadJsonResource(String path, Class<T> jsonClass)
      throws URISyntaxException, IOException {
    return JsonTemplateMapper.readJsonFromFile(
        Paths.get(Resources.getResource(path).toURI()), jsonClass);
  }

  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  private CacheStorageFiles cacheStorageFiles;
  private CacheStorageWriter cacheStorageWriter;
  private Path cacheRoot;

  @Before
  public void setUp() throws IOException {
    cacheRoot = temporaryFolder.newFolder().toPath();
    cacheStorageFiles = new CacheStorageFiles(cacheRoot);
    cacheStorageWriter = new CacheStorageWriter(cacheStorageFiles);
  }

  @Test
  public void testWriteCompressed() throws IOException {
    Blob uncompressedLayerBlob = Blobs.from("uncompressedLayerBlob");

    CachedLayer cachedLayer = cacheStorageWriter.writeCompressed(compress(uncompressedLayerBlob));

    verifyCachedLayer(cachedLayer, uncompressedLayerBlob);
  }

  @Test
  public void testWriteUncompressed() throws IOException {
    Blob uncompressedLayerBlob = Blobs.from("uncompressedLayerBlob");
    DescriptorDigest layerDigest = getDigest(compress(uncompressedLayerBlob)).getDigest();
    DescriptorDigest selector = getDigest(Blobs.from("selector")).getDigest();

    CachedLayer cachedLayer = cacheStorageWriter.writeUncompressed(uncompressedLayerBlob, selector);

    verifyCachedLayer(cachedLayer, uncompressedLayerBlob);

    // Verifies that the files are present.
    Path selectorFile = cacheStorageFiles.getSelectorFile(selector);
    Assert.assertTrue(Files.exists(selectorFile));
    Assert.assertEquals(layerDigest.getHash(), Blobs.writeToString(Blobs.from(selectorFile)));
  }

  @Test
  public void testWriteTarLayer() throws IOException {
    Blob uncompressedLayerBlob = Blobs.from("uncompressedLayerBlob");
    DescriptorDigest diffId = getDigest(uncompressedLayerBlob).getDigest();

    CachedLayer cachedLayer =
        cacheStorageWriter.writeTarLayer(diffId, compress(uncompressedLayerBlob));

    BlobDescriptor layerBlobDescriptor = getDigest(compress(uncompressedLayerBlob));

    // Verifies cachedLayer is correct.
    Assert.assertEquals(layerBlobDescriptor.getDigest(), cachedLayer.getDigest());
    Assert.assertEquals(diffId, cachedLayer.getDiffId());
    Assert.assertEquals(layerBlobDescriptor.getSize(), cachedLayer.getSize());
    Assert.assertArrayEquals(
        Blobs.writeToByteArray(uncompressedLayerBlob),
        Blobs.writeToByteArray(decompress(cachedLayer.getBlob())));

    // Verifies that the files are present.
    Assert.assertTrue(
        Files.exists(
            cacheStorageFiles
                .getLocalDirectory()
                .resolve(cachedLayer.getDiffId().getHash())
                .resolve(cachedLayer.getDigest().getHash())));
  }

  @Test
  public void testWriteMetadata_v21()
      throws IOException, URISyntaxException, InvalidImageReferenceException {
    V21ManifestTemplate v21Manifest =
        loadJsonResource("core/json/v21manifest.json", V21ManifestTemplate.class);
    ImageReference imageReference = ImageReference.parse("image.reference/project/thing:tag");

    ManifestAndConfigTemplate manifestAndConfig = new ManifestAndConfigTemplate(v21Manifest, null);
    cacheStorageWriter.writeMetadata(
        imageReference, new ImageMetadataTemplate(null, Arrays.asList(manifestAndConfig)));

    Path savedMetadataPath =
        cacheRoot.resolve("images/image.reference/project/thing!tag/manifests_configs.json");
    Assert.assertTrue(Files.exists(savedMetadataPath));

    ImageMetadataTemplate savedMetadata =
        JsonTemplateMapper.readJsonFromFile(savedMetadataPath, ImageMetadataTemplate.class);
    Assert.assertNull(savedMetadata.getManifestList());
    Assert.assertEquals(1, savedMetadata.getManifestsAndConfigs().size());

    ManifestAndConfigTemplate savedManifestAndConfig =
        savedMetadata.getManifestsAndConfigs().get(0);
    Assert.assertNull(savedManifestAndConfig.getConfig());

    V21ManifestTemplate savedManifest = (V21ManifestTemplate) savedManifestAndConfig.getManifest();
    Assert.assertEquals(
        "ppc64le", savedManifest.getContainerConfiguration().get().getArchitecture());
  }

  @Test
  public void testWriteMetadata_v22()
      throws IOException, URISyntaxException, InvalidImageReferenceException {
    ContainerConfigurationTemplate containerConfig =
        loadJsonResource("core/json/containerconfig.json", ContainerConfigurationTemplate.class);
    V22ManifestTemplate manifest1 =
        loadJsonResource("core/json/v22manifest.json", V22ManifestTemplate.class);
    V22ManifestTemplate manifest2 =
        loadJsonResource(
            "core/json/v22manifest_optional_properties.json", V22ManifestTemplate.class);
    V22ManifestListTemplate manifestList =
        loadJsonResource("core/json/v22manifest_list.json", V22ManifestListTemplate.class);

    ImageReference imageReference = ImageReference.parse("image.reference/project/thing:tag");

    List<ManifestAndConfigTemplate> manifestsAndConfigs =
        Arrays.asList(
            new ManifestAndConfigTemplate(manifest1, containerConfig, "sha256:digest"),
            new ManifestAndConfigTemplate(manifest2, containerConfig, "sha256:digest"));
    cacheStorageWriter.writeMetadata(
        imageReference, new ImageMetadataTemplate(manifestList, manifestsAndConfigs));

    Path savedMetadataPath =
        cacheRoot.resolve("images/image.reference/project/thing!tag/manifests_configs.json");
    Assert.assertTrue(Files.exists(savedMetadataPath));

    ImageMetadataTemplate savedMetadata =
        JsonTemplateMapper.readJsonFromFile(savedMetadataPath, ImageMetadataTemplate.class);

    MatcherAssert.assertThat(
        savedMetadata.getManifestList(), CoreMatchers.instanceOf(V22ManifestListTemplate.class));
    List<ManifestDescriptorTemplate> savedManifestDescriptors =
        ((V22ManifestListTemplate) savedMetadata.getManifestList()).getManifests();

    Assert.assertEquals(3, savedManifestDescriptors.size());
    Assert.assertEquals(
        "sha256:e692418e4cbaf90ca69d05a66403747baa33ee08806650b51fab815ad7fc331f",
        savedManifestDescriptors.get(0).getDigest());
    Assert.assertEquals(
        "sha256:5b0bcabd1ed22e9fb1310cf6c2dec7cdef19f0ad69efa1f392e94a4333501270",
        savedManifestDescriptors.get(1).getDigest());
    Assert.assertEquals(
        "sha256:cccbcabd1ed22e9fb1310cf6c2dec7cdef19f0ad69efa1f392e94a4333501999",
        savedManifestDescriptors.get(2).getDigest());

    Assert.assertEquals(2, savedMetadata.getManifestsAndConfigs().size());
    ManifestAndConfigTemplate savedManifestAndConfig1 =
        savedMetadata.getManifestsAndConfigs().get(0);
    ManifestAndConfigTemplate savedManifestAndConfig2 =
        savedMetadata.getManifestsAndConfigs().get(1);

    V22ManifestTemplate savedManifest1 =
        (V22ManifestTemplate) savedManifestAndConfig1.getManifest();
    V22ManifestTemplate savedManifest2 =
        (V22ManifestTemplate) savedManifestAndConfig2.getManifest();
    Assert.assertEquals(2, savedManifest1.getSchemaVersion());
    Assert.assertEquals(2, savedManifest2.getSchemaVersion());

    Assert.assertEquals(1, savedManifest1.getLayers().size());
    Assert.assertEquals(
        "4945ba5011739b0b98c4a41afe224e417f47c7c99b2ce76830999c9a0861b236",
        savedManifest1.getLayers().get(0).getDigest().getHash());
    Assert.assertEquals(
        Arrays.asList(
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"),
        savedManifest2
            .getLayers()
            .stream()
            .map(layer -> layer.getDigest().getHash())
            .collect(Collectors.toList()));

    Assert.assertEquals(
        "8c662931926fa990b41da3c9f42663a537ccd498130030f9149173a0493832ad",
        savedManifest1.getContainerConfiguration().getDigest().getHash());
    Assert.assertEquals(
        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
        savedManifest2.getContainerConfiguration().getDigest().getHash());

    Assert.assertEquals("wasm", savedManifestAndConfig1.getConfig().getArchitecture());
    Assert.assertEquals("wasm", savedManifestAndConfig2.getConfig().getArchitecture());
  }

  @Test
  public void testWriteMetadata_oci()
      throws URISyntaxException, IOException, InvalidImageReferenceException {
    ContainerConfigurationTemplate containerConfig =
        loadJsonResource("core/json/containerconfig.json", ContainerConfigurationTemplate.class);
    OciManifestTemplate manifest =
        loadJsonResource("core/json/ocimanifest.json", OciManifestTemplate.class);
    OciIndexTemplate ociIndex = loadJsonResource("core/json/ociindex.json", OciIndexTemplate.class);

    ImageReference imageReference = ImageReference.parse("image.reference/project/thing:tag");

    cacheStorageWriter.writeMetadata(
        imageReference,
        new ImageMetadataTemplate(
            ociIndex,
            Arrays.asList(
                new ManifestAndConfigTemplate(manifest, containerConfig, "sha256:digest"))));

    Path savedMetadataPath =
        cacheRoot.resolve("images/image.reference/project/thing!tag/manifests_configs.json");
    Assert.assertTrue(Files.exists(savedMetadataPath));

    ImageMetadataTemplate savedMetadata =
        JsonTemplateMapper.readJsonFromFile(savedMetadataPath, ImageMetadataTemplate.class);

    MatcherAssert.assertThat(
        savedMetadata.getManifestList(), CoreMatchers.instanceOf(OciIndexTemplate.class));
    List<ContentDescriptorTemplate> savedManifestDescriptors =
        ((OciIndexTemplate) savedMetadata.getManifestList()).getManifests();

    Assert.assertEquals(1, savedManifestDescriptors.size());
    Assert.assertEquals(
        "8c662931926fa990b41da3c9f42663a537ccd498130030f9149173a0493832ad",
        savedManifestDescriptors.get(0).getDigest().getHash());

    Assert.assertEquals(1, savedMetadata.getManifestsAndConfigs().size());
    ManifestAndConfigTemplate savedManifestAndConfig =
        savedMetadata.getManifestsAndConfigs().get(0);

    OciManifestTemplate savedManifest1 = (OciManifestTemplate) savedManifestAndConfig.getManifest();
    Assert.assertEquals(2, savedManifest1.getSchemaVersion());

    Assert.assertEquals(1, savedManifest1.getLayers().size());
    Assert.assertEquals(
        "4945ba5011739b0b98c4a41afe224e417f47c7c99b2ce76830999c9a0861b236",
        savedManifest1.getLayers().get(0).getDigest().getHash());

    Assert.assertEquals(
        "8c662931926fa990b41da3c9f42663a537ccd498130030f9149173a0493832ad",
        savedManifest1.getContainerConfiguration().getDigest().getHash());

    Assert.assertEquals("wasm", savedManifestAndConfig.getConfig().getArchitecture());
  }

  @Test
  public void testWriteLocalConfig() throws IOException, URISyntaxException, DigestException {
    ContainerConfigurationTemplate containerConfigurationTemplate =
        loadJsonResource("core/json/containerconfig.json", ContainerConfigurationTemplate.class);

    cacheStorageWriter.writeLocalConfig(
        DescriptorDigest.fromHash(
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"),
        containerConfigurationTemplate);

    Path savedConfigPath =
        cacheStorageFiles
            .getLocalDirectory()
            .resolve("config")
            .resolve("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
    Assert.assertTrue(Files.exists(savedConfigPath));
    ContainerConfigurationTemplate savedContainerConfig =
        JsonTemplateMapper.readJsonFromFile(savedConfigPath, ContainerConfigurationTemplate.class);
    Assert.assertEquals("wasm", savedContainerConfig.getArchitecture());
  }

  @Test
  public void testMoveIfDoesNotExist_exceptionAfterFailure() {
    Exception exception =
        assertThrows(
            IOException.class,
            () -> CacheStorageWriter.moveIfDoesNotExist(Paths.get("/foo"), Paths.get("/bar")));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(
            "unable to move: /foo to /bar; such failures are often caused by interference from "
                + "antivirus (https://github.com/GoogleContainerTools/jib/issues/3127#issuecomment-796838294), "
                + "or rarely if the operation is not supported by the file system (for example: "
                + "special non-local file system)");
    assertThat(exception).hasCauseThat().isInstanceOf(NoSuchFileException.class);
    assertThat(exception.getCause()).hasMessageThat().isEqualTo("/foo");
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
