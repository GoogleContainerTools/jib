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

import com.google.cloud.tools.jib.api.DescriptorDigest;
import com.google.cloud.tools.jib.api.ImageReference;
import com.google.cloud.tools.jib.blob.Blobs;
import com.google.cloud.tools.jib.image.json.BuildableManifestTemplate.ContentDescriptorTemplate;
import com.google.cloud.tools.jib.image.json.ContainerConfigurationTemplate;
import com.google.cloud.tools.jib.image.json.ImageMetadataTemplate;
import com.google.cloud.tools.jib.image.json.ManifestAndConfigTemplate;
import com.google.cloud.tools.jib.image.json.ManifestTemplate;
import com.google.cloud.tools.jib.image.json.OciIndexTemplate;
import com.google.cloud.tools.jib.image.json.OciManifestTemplate;
import com.google.cloud.tools.jib.image.json.V21ManifestTemplate;
import com.google.cloud.tools.jib.image.json.V22ManifestListTemplate;
import com.google.cloud.tools.jib.image.json.V22ManifestListTemplate.ManifestDescriptorTemplate;
import com.google.cloud.tools.jib.image.json.V22ManifestTemplate;
import com.google.cloud.tools.jib.json.JsonTemplate;
import com.google.cloud.tools.jib.json.JsonTemplateMapper;
import com.google.common.io.Resources;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DigestException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;

/** Tests for {@link CacheStorageReader}. */
public class CacheStorageReaderTest {

  private static void setupCachedMetadataV21(Path cacheDirectory)
      throws IOException, URISyntaxException {
    Path imageDirectory = cacheDirectory.resolve("images/test/image!tag");
    Files.createDirectories(imageDirectory);

    ManifestAndConfigTemplate manifestAndConfig =
        new ManifestAndConfigTemplate(
            loadJsonResource("core/json/v21manifest.json", V21ManifestTemplate.class), null);
    try (OutputStream out =
        Files.newOutputStream(imageDirectory.resolve("manifests_configs.json"))) {
      JsonTemplateMapper.writeTo(
          new ImageMetadataTemplate(null, Arrays.asList(manifestAndConfig)), out);
    }
  }

  private static void setupCachedMetadataV22(Path cacheDirectory)
      throws IOException, URISyntaxException {
    Path imageDirectory = cacheDirectory.resolve("images/test/image!tag");
    Files.createDirectories(imageDirectory);

    ManifestAndConfigTemplate manifestAndConfig =
        new ManifestAndConfigTemplate(
            loadJsonResource("core/json/v22manifest.json", V22ManifestTemplate.class),
            loadJsonResource(
                "core/json/containerconfig.json", ContainerConfigurationTemplate.class),
            "sha256:digest");
    try (OutputStream out =
        Files.newOutputStream(imageDirectory.resolve("manifests_configs.json"))) {
      JsonTemplateMapper.writeTo(
          new ImageMetadataTemplate(null, Arrays.asList(manifestAndConfig)), out);
    }
  }

  private static void setupCachedMetadataV22ManifestList(Path cacheDirectory)
      throws IOException, URISyntaxException {
    Path imageDirectory = cacheDirectory.resolve("images/test/image!tag");
    Files.createDirectories(imageDirectory);

    ManifestTemplate v22ManifestList =
        loadJsonResource("core/json/v22manifest_list.json", V22ManifestListTemplate.class);
    ManifestTemplate v22Manifest1 =
        loadJsonResource("core/json/v22manifest.json", V22ManifestTemplate.class);
    ManifestTemplate v22Manifest2 =
        loadJsonResource("core/json/translated_v22manifest.json", V22ManifestTemplate.class);
    ContainerConfigurationTemplate containerConfig =
        loadJsonResource("core/json/containerconfig.json", ContainerConfigurationTemplate.class);
    List<ManifestAndConfigTemplate> manifestsAndConfigs =
        Arrays.asList(
            new ManifestAndConfigTemplate(v22Manifest1, containerConfig, "sha256:digest"),
            new ManifestAndConfigTemplate(v22Manifest2, containerConfig, "sha256:digest"));
    try (OutputStream out =
        Files.newOutputStream(imageDirectory.resolve("manifests_configs.json"))) {
      JsonTemplateMapper.writeTo(
          new ImageMetadataTemplate(v22ManifestList, manifestsAndConfigs), out);
    }
  }

  private static void setupCachedMetadataOci(Path cacheDirectory)
      throws IOException, URISyntaxException {
    Path imageDirectory = cacheDirectory.resolve("images/test/image!tag");
    Files.createDirectories(imageDirectory);

    ManifestAndConfigTemplate manifestAndConfig =
        new ManifestAndConfigTemplate(
            loadJsonResource("core/json/ocimanifest.json", OciManifestTemplate.class),
            loadJsonResource(
                "core/json/containerconfig.json", ContainerConfigurationTemplate.class),
            "sha256:digest");
    try (OutputStream out =
        Files.newOutputStream(imageDirectory.resolve("manifests_configs.json"))) {
      JsonTemplateMapper.writeTo(
          new ImageMetadataTemplate(null, Arrays.asList(manifestAndConfig)), out);
    }
  }

  private static void setupCachedMetadataOciImageIndex(Path cacheDirectory)
      throws IOException, URISyntaxException {
    Path imageDirectory = cacheDirectory.resolve("images/test/image!tag");
    Files.createDirectories(imageDirectory);

    ManifestTemplate ociIndex = loadJsonResource("core/json/ociindex.json", OciIndexTemplate.class);
    ManifestTemplate ociManifest =
        loadJsonResource("core/json/ocimanifest.json", OciManifestTemplate.class);
    ContainerConfigurationTemplate containerConfig =
        loadJsonResource("core/json/containerconfig.json", ContainerConfigurationTemplate.class);
    List<ManifestAndConfigTemplate> manifestsAndConfigs =
        Arrays.asList(new ManifestAndConfigTemplate(ociManifest, containerConfig, "sha256:digest"));
    try (OutputStream out =
        Files.newOutputStream(imageDirectory.resolve("manifests_configs.json"))) {
      JsonTemplateMapper.writeTo(new ImageMetadataTemplate(ociIndex, manifestsAndConfigs), out);
    }
  }

  private static <T extends JsonTemplate> T loadJsonResource(String path, Class<T> jsonClass)
      throws URISyntaxException, IOException {
    return JsonTemplateMapper.readJsonFromFile(
        Paths.get(Resources.getResource(path).toURI()), jsonClass);
  }

  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  private Path cacheDirectory;
  private DescriptorDigest layerDigest1;
  private DescriptorDigest layerDigest2;
  private CacheStorageFiles cacheStorageFiles;
  private CacheStorageReader cacheStorageReader;

  @Before
  public void setUp() throws DigestException, IOException {
    cacheDirectory = temporaryFolder.newFolder().toPath();
    cacheStorageFiles = new CacheStorageFiles(cacheDirectory);
    cacheStorageReader = new CacheStorageReader(cacheStorageFiles);
    layerDigest1 =
        DescriptorDigest.fromHash(
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
    layerDigest2 =
        DescriptorDigest.fromHash(
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
  }

  @Test
  public void testRetrieveMetadata_v21SingleManifest()
      throws IOException, URISyntaxException, CacheCorruptedException {
    setupCachedMetadataV21(cacheDirectory);

    ImageMetadataTemplate metadata =
        cacheStorageReader.retrieveMetadata(ImageReference.of("test", "image", "tag")).get();
    Assert.assertNull(metadata.getManifestList());
    Assert.assertEquals(1, metadata.getManifestsAndConfigs().size());
    Assert.assertNull(metadata.getManifestsAndConfigs().get(0).getConfig());

    V21ManifestTemplate manifestTemplate =
        (V21ManifestTemplate) metadata.getManifestsAndConfigs().get(0).getManifest();
    Assert.assertEquals(1, manifestTemplate.getSchemaVersion());
    Assert.assertEquals(2, manifestTemplate.getLayerDigests().size());
    Assert.assertEquals(
        "8c662931926fa990b41da3c9f42663a537ccd498130030f9149173a0493832ad",
        manifestTemplate.getLayerDigests().get(0).getHash());
    Assert.assertEquals(
        "5bd451067f9ab05e97cda8476c82f86d9b69c2dffb60a8ad2fe3723942544ab3",
        manifestTemplate.getLayerDigests().get(1).getHash());
  }

  @Test
  public void testRetrieveMetadata_v22SingleManifest()
      throws IOException, URISyntaxException, CacheCorruptedException {
    setupCachedMetadataV22(cacheDirectory);

    ImageMetadataTemplate metadata =
        cacheStorageReader.retrieveMetadata(ImageReference.of("test", "image", "tag")).get();
    Assert.assertNull(metadata.getManifestList());
    Assert.assertEquals(1, metadata.getManifestsAndConfigs().size());

    V22ManifestTemplate manifestTemplate =
        (V22ManifestTemplate) metadata.getManifestsAndConfigs().get(0).getManifest();
    Assert.assertEquals(2, manifestTemplate.getSchemaVersion());
    Assert.assertEquals(
        "8c662931926fa990b41da3c9f42663a537ccd498130030f9149173a0493832ad",
        manifestTemplate.getContainerConfiguration().getDigest().getHash());
  }

  @Test
  public void testRetrieveMetadata_v22ManifestList()
      throws IOException, URISyntaxException, CacheCorruptedException {
    setupCachedMetadataV22ManifestList(cacheDirectory);

    ImageMetadataTemplate metadata =
        cacheStorageReader.retrieveMetadata(ImageReference.of("test", "image", "tag")).get();

    MatcherAssert.assertThat(
        metadata.getManifestList(), CoreMatchers.instanceOf(V22ManifestListTemplate.class));
    List<ManifestDescriptorTemplate> manifestDescriptors =
        ((V22ManifestListTemplate) metadata.getManifestList()).getManifests();

    Assert.assertEquals(3, manifestDescriptors.size());
    Assert.assertEquals(
        "sha256:e692418e4cbaf90ca69d05a66403747baa33ee08806650b51fab815ad7fc331f",
        manifestDescriptors.get(0).getDigest());
    Assert.assertEquals(
        "sha256:5b0bcabd1ed22e9fb1310cf6c2dec7cdef19f0ad69efa1f392e94a4333501270",
        manifestDescriptors.get(1).getDigest());
    Assert.assertEquals(
        "sha256:cccbcabd1ed22e9fb1310cf6c2dec7cdef19f0ad69efa1f392e94a4333501999",
        manifestDescriptors.get(2).getDigest());

    Assert.assertEquals(2, metadata.getManifestsAndConfigs().size());
    ManifestAndConfigTemplate manifestAndConfig1 = metadata.getManifestsAndConfigs().get(0);
    ManifestAndConfigTemplate manifestAndConfig2 = metadata.getManifestsAndConfigs().get(1);

    V22ManifestTemplate manifest1 = (V22ManifestTemplate) manifestAndConfig1.getManifest();
    V22ManifestTemplate manifest2 = (V22ManifestTemplate) manifestAndConfig2.getManifest();
    Assert.assertEquals(2, manifest1.getSchemaVersion());
    Assert.assertEquals(2, manifest2.getSchemaVersion());
    Assert.assertEquals(1, manifest1.getLayers().size());
    Assert.assertEquals(1, manifest2.getLayers().size());
    Assert.assertEquals(
        "4945ba5011739b0b98c4a41afe224e417f47c7c99b2ce76830999c9a0861b236",
        manifest1.getLayers().get(0).getDigest().getHash());
    Assert.assertEquals(
        "8c662931926fa990b41da3c9f42663a537ccd498130030f9149173a0493832ad",
        manifest2.getLayers().get(0).getDigest().getHash());
    Assert.assertEquals(
        "8c662931926fa990b41da3c9f42663a537ccd498130030f9149173a0493832ad",
        manifest1.getContainerConfiguration().getDigest().getHash());
    Assert.assertEquals(
        "2000a70a1ce8bba401c493376fdb9eb81bcf7155212f4ce4c6ff96e577718a49",
        manifest2.getContainerConfiguration().getDigest().getHash());

    Assert.assertEquals("wasm", manifestAndConfig1.getConfig().getArchitecture());
    Assert.assertEquals("wasm", manifestAndConfig2.getConfig().getArchitecture());
  }

  @Test
  public void testRetrieveMetadata_ociSingleManifest()
      throws IOException, URISyntaxException, CacheCorruptedException {
    setupCachedMetadataOci(cacheDirectory);

    ImageMetadataTemplate metadata =
        cacheStorageReader.retrieveMetadata(ImageReference.of("test", "image", "tag")).get();
    Assert.assertNull(metadata.getManifestList());
    Assert.assertEquals(1, metadata.getManifestsAndConfigs().size());

    OciManifestTemplate manifestTemplate =
        (OciManifestTemplate) metadata.getManifestsAndConfigs().get(0).getManifest();
    Assert.assertEquals(2, manifestTemplate.getSchemaVersion());
    Assert.assertEquals(
        "8c662931926fa990b41da3c9f42663a537ccd498130030f9149173a0493832ad",
        manifestTemplate.getContainerConfiguration().getDigest().getHash());
  }

  @Test
  public void testRetrieveMetadata_ociImageIndex()
      throws IOException, URISyntaxException, CacheCorruptedException {
    setupCachedMetadataOciImageIndex(cacheDirectory);

    ImageMetadataTemplate metadata =
        cacheStorageReader.retrieveMetadata(ImageReference.of("test", "image", "tag")).get();

    MatcherAssert.assertThat(
        metadata.getManifestList(), CoreMatchers.instanceOf(OciIndexTemplate.class));
    List<? extends ContentDescriptorTemplate> manifestDescriptors =
        ((OciIndexTemplate) metadata.getManifestList()).getManifests();

    Assert.assertEquals(1, manifestDescriptors.size());
    Assert.assertEquals(
        "8c662931926fa990b41da3c9f42663a537ccd498130030f9149173a0493832ad",
        manifestDescriptors.get(0).getDigest().getHash());

    Assert.assertEquals(1, metadata.getManifestsAndConfigs().size());
    ManifestAndConfigTemplate manifestAndConfig = metadata.getManifestsAndConfigs().get(0);

    OciManifestTemplate manifestTemplate = (OciManifestTemplate) manifestAndConfig.getManifest();
    Assert.assertEquals(2, manifestTemplate.getSchemaVersion());
    Assert.assertEquals(
        "8c662931926fa990b41da3c9f42663a537ccd498130030f9149173a0493832ad",
        manifestTemplate.getContainerConfiguration().getDigest().getHash());

    Assert.assertEquals("wasm", manifestAndConfig.getConfig().getArchitecture());
  }

  @Test
  public void testRetrieveMetadata_containerConfiguration()
      throws IOException, URISyntaxException, CacheCorruptedException {
    setupCachedMetadataV22(cacheDirectory);

    ImageMetadataTemplate metadata =
        cacheStorageReader.retrieveMetadata(ImageReference.of("test", "image", "tag")).get();
    Assert.assertNull(metadata.getManifestList());
    Assert.assertEquals(1, metadata.getManifestsAndConfigs().size());

    ContainerConfigurationTemplate configurationTemplate =
        metadata.getManifestsAndConfigs().get(0).getConfig();
    Assert.assertNotNull(configurationTemplate);
    Assert.assertEquals("wasm", configurationTemplate.getArchitecture());
    Assert.assertEquals("js", configurationTemplate.getOs());
  }

  @Test
  public void testRetrieve() throws IOException, CacheCorruptedException {
    // Creates the test layer directory.
    DescriptorDigest layerDigest = layerDigest1;
    DescriptorDigest layerDiffId = layerDigest2;
    Files.createDirectories(cacheStorageFiles.getLayerDirectory(layerDigest));
    try (OutputStream out =
        Files.newOutputStream(cacheStorageFiles.getLayerFile(layerDigest, layerDiffId))) {
      out.write("layerBlob".getBytes(StandardCharsets.UTF_8));
    }

    // Checks that the CachedLayer is retrieved correctly.
    Optional<CachedLayer> optionalCachedLayer = cacheStorageReader.retrieve(layerDigest);
    Assert.assertTrue(optionalCachedLayer.isPresent());
    Assert.assertEquals(layerDigest, optionalCachedLayer.get().getDigest());
    Assert.assertEquals(layerDiffId, optionalCachedLayer.get().getDiffId());
    Assert.assertEquals("layerBlob".length(), optionalCachedLayer.get().getSize());
    Assert.assertEquals("layerBlob", Blobs.writeToString(optionalCachedLayer.get().getBlob()));

    // Checks that multiple .layer files means the cache is corrupted.
    Files.createFile(cacheStorageFiles.getLayerFile(layerDigest, layerDigest));
    try {
      cacheStorageReader.retrieve(layerDigest);
      Assert.fail("Should have thrown CacheCorruptedException");

    } catch (CacheCorruptedException ex) {
      MatcherAssert.assertThat(
          ex.getMessage(),
          CoreMatchers.startsWith(
              "No or multiple layer files found for layer hash "
                  + layerDigest.getHash()
                  + " in directory: "
                  + cacheStorageFiles.getLayerDirectory(layerDigest)));
    }
  }

  @Test
  public void testRetrieveTarLayer() throws IOException, CacheCorruptedException {
    // Creates the test layer directory.
    Path localDirectory = cacheStorageFiles.getLocalDirectory();
    DescriptorDigest layerDigest = layerDigest1;
    DescriptorDigest layerDiffId = layerDigest2;
    Files.createDirectories(localDirectory.resolve(layerDiffId.getHash()));
    try (OutputStream out =
        Files.newOutputStream(
            localDirectory.resolve(layerDiffId.getHash()).resolve(layerDigest.getHash()))) {
      out.write("layerBlob".getBytes(StandardCharsets.UTF_8));
    }

    // Checks that the CachedLayer is retrieved correctly.
    Optional<CachedLayer> optionalCachedLayer = cacheStorageReader.retrieveTarLayer(layerDiffId);
    Assert.assertTrue(optionalCachedLayer.isPresent());
    Assert.assertEquals(layerDigest, optionalCachedLayer.get().getDigest());
    Assert.assertEquals(layerDiffId, optionalCachedLayer.get().getDiffId());
    Assert.assertEquals("layerBlob".length(), optionalCachedLayer.get().getSize());
    Assert.assertEquals("layerBlob", Blobs.writeToString(optionalCachedLayer.get().getBlob()));

    // Checks that multiple layer files means the cache is corrupted.
    Files.createFile(localDirectory.resolve(layerDiffId.getHash()).resolve(layerDiffId.getHash()));
    try {
      cacheStorageReader.retrieveTarLayer(layerDiffId);
      Assert.fail("Should have thrown CacheCorruptedException");

    } catch (CacheCorruptedException ex) {
      MatcherAssert.assertThat(
          ex.getMessage(),
          CoreMatchers.startsWith(
              "No or multiple layer files found for layer hash "
                  + layerDiffId.getHash()
                  + " in directory: "
                  + localDirectory.resolve(layerDiffId.getHash())));
    }
  }

  @Test
  public void testRetrieveLocalConfig() throws IOException, URISyntaxException, DigestException {
    Path configDirectory = cacheDirectory.resolve("local").resolve("config");
    Files.createDirectories(configDirectory);
    Files.copy(
        Paths.get(Resources.getResource("core/json/containerconfig.json").toURI()),
        configDirectory.resolve(
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));

    ContainerConfigurationTemplate configurationTemplate =
        cacheStorageReader
            .retrieveLocalConfig(
                DescriptorDigest.fromHash(
                    "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"))
            .get();
    Assert.assertEquals("wasm", configurationTemplate.getArchitecture());
    Assert.assertEquals("js", configurationTemplate.getOs());

    Optional<ContainerConfigurationTemplate> missingConfigurationTemplate =
        cacheStorageReader.retrieveLocalConfig(
            DescriptorDigest.fromHash(
                "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"));
    Assert.assertFalse(missingConfigurationTemplate.isPresent());
  }

  @Test
  public void testSelect_invalidLayerDigest() throws IOException {
    DescriptorDigest selector = layerDigest1;
    Path selectorFile = cacheStorageFiles.getSelectorFile(selector);
    Files.createDirectories(selectorFile.getParent());
    Files.write(selectorFile, "not a valid layer digest".getBytes(StandardCharsets.UTF_8));

    try {
      cacheStorageReader.select(selector);
      Assert.fail("Should have thrown CacheCorruptedException");

    } catch (CacheCorruptedException ex) {
      MatcherAssert.assertThat(
          ex.getMessage(),
          CoreMatchers.startsWith(
              "Expected valid layer digest as contents of selector file `"
                  + selectorFile
                  + "` for selector `aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa`, but got: not a valid layer digest"));
    }
  }

  @Test
  public void testSelect() throws IOException, CacheCorruptedException {
    DescriptorDigest selector = layerDigest1;
    Path selectorFile = cacheStorageFiles.getSelectorFile(selector);
    Files.createDirectories(selectorFile.getParent());
    Files.write(selectorFile, layerDigest2.getHash().getBytes(StandardCharsets.UTF_8));

    Optional<DescriptorDigest> selectedLayerDigest = cacheStorageReader.select(selector);
    Assert.assertTrue(selectedLayerDigest.isPresent());
    Assert.assertEquals(layerDigest2, selectedLayerDigest.get());
  }

  @Test
  public void testVerifyImageMetadata_manifestCacheEmpty() {
    ImageMetadataTemplate metadata = new ImageMetadataTemplate(null, Collections.emptyList());
    try {
      CacheStorageReader.verifyImageMetadata(metadata, Paths.get("/cache/dir"));
      Assert.fail();
    } catch (CacheCorruptedException ex) {
      MatcherAssert.assertThat(ex.getMessage(), CoreMatchers.startsWith("Manifest cache empty"));
    }
  }

  @Test
  public void testVerifyImageMetadata_manifestListMissing() {
    ManifestAndConfigTemplate manifestAndConfig =
        new ManifestAndConfigTemplate(
            new V22ManifestListTemplate(), new ContainerConfigurationTemplate());
    ImageMetadataTemplate metadata =
        new ImageMetadataTemplate(null, Arrays.asList(manifestAndConfig, manifestAndConfig));
    try {
      CacheStorageReader.verifyImageMetadata(metadata, Paths.get("/cache/dir"));
      Assert.fail();
    } catch (CacheCorruptedException ex) {
      MatcherAssert.assertThat(ex.getMessage(), CoreMatchers.startsWith("Manifest list missing"));
    }
  }

  @Test
  public void testVerifyImageMetadata_manifestsMissing() {
    ManifestAndConfigTemplate manifestAndConfig =
        new ManifestAndConfigTemplate(null, new ContainerConfigurationTemplate());
    ImageMetadataTemplate metadata =
        new ImageMetadataTemplate(null, Arrays.asList(manifestAndConfig));
    try {
      CacheStorageReader.verifyImageMetadata(metadata, Paths.get("/cache/dir"));
      Assert.fail();
    } catch (CacheCorruptedException ex) {
      MatcherAssert.assertThat(ex.getMessage(), CoreMatchers.startsWith("Manifest(s) missing"));
    }
  }

  @Test
  public void testVerifyImageMetadata_schema1ManifestsCorrupted_manifestListExists() {
    ManifestAndConfigTemplate manifestAndConfig =
        new ManifestAndConfigTemplate(new V21ManifestTemplate(), null);
    ImageMetadataTemplate metadata =
        new ImageMetadataTemplate(new V22ManifestListTemplate(), Arrays.asList(manifestAndConfig));
    try {
      CacheStorageReader.verifyImageMetadata(metadata, Paths.get("/cache/dir"));
      Assert.fail();
    } catch (CacheCorruptedException ex) {
      MatcherAssert.assertThat(
          ex.getMessage(), CoreMatchers.startsWith("Schema 1 manifests corrupted"));
    }
  }

  @Test
  public void testVerifyImageMetadata_schema1ManifestsCorrupted_containerConfigExists() {
    ManifestAndConfigTemplate manifestAndConfig =
        new ManifestAndConfigTemplate(
            new V21ManifestTemplate(), new ContainerConfigurationTemplate());
    ImageMetadataTemplate metadata =
        new ImageMetadataTemplate(null, Arrays.asList(manifestAndConfig));
    try {
      CacheStorageReader.verifyImageMetadata(metadata, Paths.get("/cache/dir"));
      Assert.fail();
    } catch (CacheCorruptedException ex) {
      MatcherAssert.assertThat(
          ex.getMessage(), CoreMatchers.startsWith("Schema 1 manifests corrupted"));
    }
  }

  @Test
  public void testVerifyImageMetadata_schema2ManifestsCorrupted_nullContainerConfig() {
    ManifestAndConfigTemplate manifestAndConfig =
        new ManifestAndConfigTemplate(new V22ManifestTemplate(), null, "sha256:digest");
    ImageMetadataTemplate metadata =
        new ImageMetadataTemplate(null, Arrays.asList(manifestAndConfig));
    try {
      CacheStorageReader.verifyImageMetadata(metadata, Paths.get("/cache/dir"));
      Assert.fail();
    } catch (CacheCorruptedException ex) {
      MatcherAssert.assertThat(
          ex.getMessage(), CoreMatchers.startsWith("Schema 2 manifests corrupted"));
    }
  }

  @Test
  public void testVerifyImageMetadata_schema2ManifestsCorrupted_nullManifestDigest() {
    ManifestAndConfigTemplate manifestAndConfig =
        new ManifestAndConfigTemplate(
            new V22ManifestTemplate(), new ContainerConfigurationTemplate(), null);
    ImageMetadataTemplate metadata =
        new ImageMetadataTemplate(new V22ManifestListTemplate(), Arrays.asList(manifestAndConfig));
    try {
      CacheStorageReader.verifyImageMetadata(metadata, Paths.get("/cache/dir"));
      Assert.fail();
    } catch (CacheCorruptedException ex) {
      MatcherAssert.assertThat(
          ex.getMessage(), CoreMatchers.startsWith("Schema 2 manifests corrupted"));
    }
  }

  @Test
  public void testVerifyImageMetadata_unknownManifestType() {
    ManifestAndConfigTemplate manifestAndConfig =
        new ManifestAndConfigTemplate(
            Mockito.mock(ManifestTemplate.class), new ContainerConfigurationTemplate());
    ImageMetadataTemplate metadata =
        new ImageMetadataTemplate(null, Arrays.asList(manifestAndConfig));
    try {
      CacheStorageReader.verifyImageMetadata(metadata, Paths.get("/cache/dir"));
      Assert.fail();
    } catch (CacheCorruptedException ex) {
      MatcherAssert.assertThat(ex.getMessage(), CoreMatchers.startsWith("Unknown manifest type:"));
    }
  }

  @Test
  public void testVerifyImageMetadata_validV21() throws CacheCorruptedException {
    ManifestAndConfigTemplate manifestAndConfig =
        new ManifestAndConfigTemplate(new V21ManifestTemplate(), null);
    ImageMetadataTemplate metadata =
        new ImageMetadataTemplate(null, Arrays.asList(manifestAndConfig));
    CacheStorageReader.verifyImageMetadata(metadata, Paths.get("/cache/dir"));
    // should pass without CacheCorruptedException
  }

  @Test
  public void testVerifyImageMetadata_validV22() throws CacheCorruptedException {
    ManifestAndConfigTemplate manifestAndConfig =
        new ManifestAndConfigTemplate(
            new V22ManifestTemplate(), new ContainerConfigurationTemplate());
    ImageMetadataTemplate metadata =
        new ImageMetadataTemplate(null, Arrays.asList(manifestAndConfig));
    CacheStorageReader.verifyImageMetadata(metadata, Paths.get("/cache/dir"));
    // should pass without CacheCorruptedException
  }

  @Test
  public void testVerifyImageMetadata_validV22ManifestList() throws CacheCorruptedException {
    ManifestAndConfigTemplate manifestAndConfig =
        new ManifestAndConfigTemplate(
            new V22ManifestTemplate(), new ContainerConfigurationTemplate(), "sha256:digest");
    ImageMetadataTemplate metadata =
        new ImageMetadataTemplate(
            new V22ManifestListTemplate(), Arrays.asList(manifestAndConfig, manifestAndConfig));
    CacheStorageReader.verifyImageMetadata(metadata, Paths.get("/cache/dir"));
    // should pass without CacheCorruptedException
  }

  @Test
  public void testVerifyImageMetadata_validOci() throws CacheCorruptedException {
    ManifestAndConfigTemplate manifestAndConfig =
        new ManifestAndConfigTemplate(
            new OciManifestTemplate(), new ContainerConfigurationTemplate(), "sha256:digest");
    ImageMetadataTemplate metadata =
        new ImageMetadataTemplate(null, Arrays.asList(manifestAndConfig));
    CacheStorageReader.verifyImageMetadata(metadata, Paths.get("/cache/dir"));
    // should pass without CacheCorruptedException
  }

  @Test
  public void testVerifyImageMetadata_validOciImageIndex() throws CacheCorruptedException {
    ManifestAndConfigTemplate manifestAndConfig =
        new ManifestAndConfigTemplate(
            new OciManifestTemplate(), new ContainerConfigurationTemplate(), "sha256:digest");
    ImageMetadataTemplate metadata =
        new ImageMetadataTemplate(
            new OciIndexTemplate(), Arrays.asList(manifestAndConfig, manifestAndConfig));
    CacheStorageReader.verifyImageMetadata(metadata, Paths.get("/cache/dir"));
    // should pass without CacheCorruptedException
  }

  @Test
  public void testAllLayersCached_v21SingleManifest()
      throws IOException, CacheCorruptedException, DigestException, URISyntaxException {
    setupCachedMetadataV21(cacheDirectory);
    ImageMetadataTemplate metadata =
        cacheStorageReader.retrieveMetadata(ImageReference.of("test", "image", "tag")).get();
    V21ManifestTemplate manifest =
        (V21ManifestTemplate) metadata.getManifestsAndConfigs().get(0).getManifest();
    DescriptorDigest firstLayerDigest = manifest.getLayerDigests().get(0);
    DescriptorDigest secondLayerDigest = manifest.getLayerDigests().get(1);

    // Create only one of the layer directories so that layers are only partially cached.
    Files.createDirectories(cacheStorageFiles.getLayerDirectory(firstLayerDigest));
    boolean checkWithPartialLayersCached = cacheStorageReader.areAllLayersCached(manifest);
    // Create the other layer directory so that all layers are cached.
    Files.createDirectories(cacheStorageFiles.getLayerDirectory(secondLayerDigest));
    boolean checkWithAllLayersCached = cacheStorageReader.areAllLayersCached(manifest);

    assertThat(checkWithPartialLayersCached).isFalse();
    assertThat(checkWithAllLayersCached).isTrue();
  }

  @Test
  public void testAllLayersCached_v22SingleManifest()
      throws IOException, CacheCorruptedException, DigestException, URISyntaxException {
    setupCachedMetadataV22(cacheDirectory);
    ImageMetadataTemplate metadata =
        cacheStorageReader.retrieveMetadata(ImageReference.of("test", "image", "tag")).get();
    V22ManifestTemplate manifest =
        (V22ManifestTemplate) metadata.getManifestsAndConfigs().get(0).getManifest();
    DescriptorDigest layerDigest = manifest.getLayers().get(0).getDigest();

    boolean checkBeforeLayerCached = cacheStorageReader.areAllLayersCached(manifest);
    // Create the single layer directory so that all layers are cached.
    Files.createDirectories(cacheStorageFiles.getLayerDirectory(layerDigest));
    boolean checkAfterLayerCached = cacheStorageReader.areAllLayersCached(manifest);

    assertThat(checkBeforeLayerCached).isFalse();
    assertThat(checkAfterLayerCached).isTrue();
  }
}
