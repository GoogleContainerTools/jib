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
import com.google.cloud.tools.jib.blob.Blobs;
import com.google.cloud.tools.jib.image.json.ContainerConfigurationTemplate;
import com.google.cloud.tools.jib.image.json.V21ManifestTemplate;
import com.google.cloud.tools.jib.image.json.V22ManifestTemplate;
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
import java.util.HashSet;
import java.util.Optional;
import org.hamcrest.CoreMatchers;
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
    Files.copy(
        Paths.get(Resources.getResource("core/json/v21manifest.json").toURI()),
        imageDirectory.resolve("manifest.json"));
  }

  private static void setupCachedMetadataV22(Path cacheDirectory)
      throws IOException, URISyntaxException {
    Path imageDirectory = cacheDirectory.resolve("images/test/image!tag");
    Files.createDirectories(imageDirectory);
    Files.copy(
        Paths.get(Resources.getResource("core/json/v22manifest.json").toURI()),
        imageDirectory.resolve("manifest.json"));
    Files.copy(
        Paths.get(Resources.getResource("core/json/containerconfig.json").toURI()),
        imageDirectory.resolve("config.json"));
  }

  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  private DescriptorDigest layerDigest1;
  private DescriptorDigest layerDigest2;

  @Before
  public void setUp() throws DigestException {
    layerDigest1 =
        DescriptorDigest.fromHash(
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
    layerDigest2 =
        DescriptorDigest.fromHash(
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
  }

  @Test
  public void testListDigests() throws IOException, CacheCorruptedException {
    CacheStorageFiles cacheStorageFiles =
        new CacheStorageFiles(temporaryFolder.newFolder().toPath());

    CacheStorageReader cacheStorageReader = new CacheStorageReader(cacheStorageFiles);

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
      Assert.assertThat(
          ex.getMessage(), CoreMatchers.startsWith("Found non-digest file in layers directory"));
      Assert.assertThat(ex.getCause(), CoreMatchers.instanceOf(DigestException.class));
    }
  }

  @Test
  public void testRetrieveManifest_v21()
      throws IOException, URISyntaxException, CacheCorruptedException {
    Path cacheDirectory = temporaryFolder.newFolder().toPath();
    setupCachedMetadataV21(cacheDirectory);

    CacheStorageFiles cacheStorageFiles = new CacheStorageFiles(cacheDirectory);
    CacheStorageReader cacheStorageReader = new CacheStorageReader(cacheStorageFiles);

    V21ManifestTemplate manifestTemplate =
        (V21ManifestTemplate)
            cacheStorageReader
                .retrieveMetadata(ImageReference.of("test", "image", "tag"))
                .get()
                .getManifest();
    Assert.assertEquals(1, manifestTemplate.getSchemaVersion());
  }

  @Test
  public void testRetrieveManifest_v22()
      throws IOException, URISyntaxException, CacheCorruptedException {
    Path cacheDirectory = temporaryFolder.newFolder().toPath();
    setupCachedMetadataV22(cacheDirectory);

    CacheStorageFiles cacheStorageFiles = new CacheStorageFiles(cacheDirectory);
    CacheStorageReader cacheStorageReader = new CacheStorageReader(cacheStorageFiles);

    V22ManifestTemplate manifestTemplate =
        (V22ManifestTemplate)
            cacheStorageReader
                .retrieveMetadata(ImageReference.of("test", "image", "tag"))
                .get()
                .getManifest();
    Assert.assertEquals(2, manifestTemplate.getSchemaVersion());
  }

  @Test
  public void testRetrieveContainerConfiguration()
      throws IOException, URISyntaxException, CacheCorruptedException {
    Path cacheDirectory = temporaryFolder.newFolder().toPath();
    setupCachedMetadataV22(cacheDirectory);

    CacheStorageFiles cacheStorageFiles = new CacheStorageFiles(cacheDirectory);
    CacheStorageReader cacheStorageReader = new CacheStorageReader(cacheStorageFiles);

    ContainerConfigurationTemplate configurationTemplate =
        cacheStorageReader
            .retrieveMetadata(ImageReference.of("test", "image", "tag"))
            .get()
            .getConfig()
            .get();
    Assert.assertEquals("wasm", configurationTemplate.getArchitecture());
    Assert.assertEquals("js", configurationTemplate.getOs());
  }

  @Test
  public void testRetrieve() throws IOException, CacheCorruptedException {
    CacheStorageFiles cacheStorageFiles =
        new CacheStorageFiles(temporaryFolder.newFolder().toPath());

    CacheStorageReader cacheStorageReader = new CacheStorageReader(cacheStorageFiles);

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
      Assert.assertThat(
          ex.getMessage(),
          CoreMatchers.startsWith(
              "Multiple layer files found for layer with digest "
                  + layerDigest.getHash()
                  + " in directory: "
                  + cacheStorageFiles.getLayerDirectory(layerDigest)));
    }
  }

  @Test
  public void testSelect_invalidLayerDigest() throws IOException {
    CacheStorageFiles cacheStorageFiles =
        new CacheStorageFiles(temporaryFolder.newFolder().toPath());

    CacheStorageReader cacheStorageReader = new CacheStorageReader(cacheStorageFiles);

    DescriptorDigest selector = layerDigest1;
    Path selectorFile = cacheStorageFiles.getSelectorFile(selector);
    Files.createDirectories(selectorFile.getParent());
    Files.write(selectorFile, "not a valid layer digest".getBytes(StandardCharsets.UTF_8));

    try {
      cacheStorageReader.select(selector);
      Assert.fail("Should have thrown CacheCorruptedException");

    } catch (CacheCorruptedException ex) {
      Assert.assertThat(
          ex.getMessage(),
          CoreMatchers.startsWith(
              "Expected valid layer digest as contents of selector file `"
                  + selectorFile
                  + "` for selector `aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa`, but got: not a valid layer digest"));
    }
  }

  @Test
  public void testSelect() throws IOException, CacheCorruptedException {
    CacheStorageFiles cacheStorageFiles =
        new CacheStorageFiles(temporaryFolder.newFolder().toPath());

    CacheStorageReader cacheStorageReader = new CacheStorageReader(cacheStorageFiles);

    DescriptorDigest selector = layerDigest1;
    Path selectorFile = cacheStorageFiles.getSelectorFile(selector);
    Files.createDirectories(selectorFile.getParent());
    Files.write(selectorFile, layerDigest2.getHash().getBytes(StandardCharsets.UTF_8));

    Optional<DescriptorDigest> selectedLayerDigest = cacheStorageReader.select(selector);
    Assert.assertTrue(selectedLayerDigest.isPresent());
    Assert.assertEquals(layerDigest2, selectedLayerDigest.get());
  }
}
