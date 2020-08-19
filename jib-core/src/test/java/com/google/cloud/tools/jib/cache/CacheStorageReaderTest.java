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

import com.google.cloud.tools.jib.api.DescriptorDigest;
import com.google.cloud.tools.jib.api.ImageReference;
import com.google.cloud.tools.jib.blob.Blobs;
import com.google.cloud.tools.jib.image.json.ContainerConfigurationTemplate;
import com.google.cloud.tools.jib.image.json.ImageMetadataTemplate;
import com.google.cloud.tools.jib.image.json.ManifestAndConfigTemplate;
import com.google.cloud.tools.jib.image.json.ManifestTemplate;
import com.google.cloud.tools.jib.image.json.V21ManifestTemplate;
import com.google.cloud.tools.jib.image.json.V22ManifestListTemplate;
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
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Tests for {@link CacheStorageReader}. */
public class CacheStorageReaderTest {

  private static void setupCachedMetadataV21(Path cacheDirectory)
      throws IOException, URISyntaxException {
    Path imageDirectory = cacheDirectory.resolve("images/test/image!tag");
    Files.createDirectories(imageDirectory);

    ManifestAndConfigTemplate manifestAndConfig =
        new ManifestAndConfigTemplate(
            readJsonFromFile("core/json/v21manifest.json", V21ManifestTemplate.class), null);
    try (OutputStream out =
        Files.newOutputStream(imageDirectory.resolve("manifests_configs.json"))) {
      JsonTemplateMapper.writeTo(
          new ImageMetadataTemplate(null, Collections.singletonList(manifestAndConfig)), out);
    }
  }

  private static void setupCachedMetadataV22(Path cacheDirectory)
      throws IOException, URISyntaxException {
    Path imageDirectory = cacheDirectory.resolve("images/test/image!tag");
    Files.createDirectories(imageDirectory);

    ManifestAndConfigTemplate manifestAndConfig =
        new ManifestAndConfigTemplate(
            readJsonFromFile("core/json/v22manifest.json", V22ManifestTemplate.class),
            readJsonFromFile(
                "core/json/containerconfig.json", ContainerConfigurationTemplate.class));
    try (OutputStream out =
        Files.newOutputStream(imageDirectory.resolve("manifests_configs.json"))) {
      JsonTemplateMapper.writeTo(
          new ImageMetadataTemplate(null, Collections.singletonList(manifestAndConfig)), out);
    }
  }

  private static void setupCachedMetadataV22ManifestList(Path cacheDirectory)
      throws IOException, URISyntaxException {
    Path imageDirectory = cacheDirectory.resolve("images/test/image!tag");
    Files.createDirectories(imageDirectory);

    ManifestTemplate v22ManifestList =
        readJsonFromFile("core/json/v22manifest_list.json", V22ManifestListTemplate.class);
    ManifestTemplate v22Manifest1 =
        readJsonFromFile("core/json/v22manifest.json", V22ManifestTemplate.class);
    ManifestTemplate v22Manifest2 =
        readJsonFromFile("core/json/translated_v22manifest.json", V22ManifestTemplate.class);
    ContainerConfigurationTemplate containerConfig =
        readJsonFromFile("core/json/containerconfig.json", ContainerConfigurationTemplate.class);
    List<ManifestAndConfigTemplate> manifestsAndConfigs =
        Arrays.asList(
            new ManifestAndConfigTemplate(v22Manifest1, containerConfig),
            new ManifestAndConfigTemplate(v22Manifest2, containerConfig));
    try (OutputStream out =
        Files.newOutputStream(imageDirectory.resolve("manifests_configs.json"))) {
      JsonTemplateMapper.writeTo(
          new ImageMetadataTemplate(v22ManifestList, manifestsAndConfigs), out);
    }
  }

  private static <T extends JsonTemplate> T readJsonFromFile(String path, Class<T> jsonClass)
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
  public void testListDigests() throws IOException, CacheCorruptedException {
    // Creates test layer directories.
    Files.createDirectories(cacheStorageFiles.getLayersDirectory().resolve(layerDigest1.getHash()));
    Files.createDirectories(cacheStorageFiles.getLayersDirectory().resolve(layerDigest2.getHash()));

    // Checks that layer directories created are all listed.
    Assert.assertEquals(
        new HashSet<>(Arrays.asList(layerDigest1, layerDigest2)),
        cacheStorageReader.fetchDigests());

    // Checks that non-digest directories means the cache is corrupted.
    Files.createDirectory(cacheStorageFiles.getLayersDirectory().resolve("not a hash"));
    try {
      cacheStorageReader.fetchDigests();
      Assert.fail("Listing digests should have failed");

    } catch (CacheCorruptedException ex) {
      MatcherAssert.assertThat(
          ex.getMessage(), CoreMatchers.startsWith("Found non-digest file in layers directory"));
      MatcherAssert.assertThat(ex.getCause(), CoreMatchers.instanceOf(DigestException.class));
    }
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
  }

  @Test
  public void testRetrieveMetadata_v22ManifestList() {}

  @Test
  public void testRetrieveMetadata_ociSingleManifest() {}

  @Test
  public void testRetrieveMetadata_ociImageIndex() {}

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
}
