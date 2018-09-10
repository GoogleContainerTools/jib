/*
 * Copyright 2017 Google LLC.
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

import com.google.cloud.tools.jib.blob.BlobDescriptor;
import com.google.cloud.tools.jib.image.DescriptorDigest;
import com.google.cloud.tools.jib.image.LayerEntry;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.security.DigestException;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Tests for {@link Cache}. */
public class CacheTest {

  @Rule public TemporaryFolder temporaryCacheDirectory = new TemporaryFolder();

  @Test
  public void testInit_empty() throws IOException, CacheMetadataCorruptedException {
    Path cacheDirectory = temporaryCacheDirectory.newFolder().toPath();

    Cache cache = Cache.init(cacheDirectory);
    Assert.assertEquals(0, cache.getMetadata().getLayers().getLayers().size());
  }

  @Test
  public void testInit_notDirectory() throws CacheMetadataCorruptedException, IOException {
    Path tempFile = temporaryCacheDirectory.newFile().toPath();

    try {
      Cache.init(tempFile);
      Assert.fail("Cache should not be able to initialize on non-directory");

    } catch (NotDirectoryException ex) {
      Assert.assertEquals("The cache can only write to a directory", ex.getMessage());
    }
  }

  @Test
  public void testInit_withMetadata()
      throws URISyntaxException, IOException, CacheMetadataCorruptedException {
    Path cacheDirectory = temporaryCacheDirectory.newFolder().toPath();

    Path resourceMetadataJsonPath = PlatformSpecificMetadataJson.getMetadataJsonFile();
    Path testMetadataJsonPath = cacheDirectory.resolve(CacheFiles.METADATA_FILENAME);
    Files.copy(resourceMetadataJsonPath, testMetadataJsonPath);

    try (Cache cache = Cache.init(cacheDirectory)) {
      Assert.assertEquals(2, cache.getMetadata().getLayers().getLayers().size());
    }

    Assert.assertArrayEquals(
        Files.readAllBytes(resourceMetadataJsonPath), Files.readAllBytes(testMetadataJsonPath));
  }

  @Test
  public void test_saveMetadata_noDuplicates()
      throws IOException, CacheMetadataCorruptedException, DigestException, URISyntaxException {
    Path cacheDirectory = temporaryCacheDirectory.newFolder().toPath();

    Path resourceMetadataJsonPath = PlatformSpecificMetadataJson.getMetadataJsonFile();
    Path testMetadataJsonPath = cacheDirectory.resolve(CacheFiles.METADATA_FILENAME);
    Files.copy(resourceMetadataJsonPath, testMetadataJsonPath);

    DescriptorDigest descriptorDigest1 =
        DescriptorDigest.fromHash(
            "8c662931926fa990b41da3c9f42663a537ccd498130030f9149173a0493832ad");
    DescriptorDigest descriptorDigest2 =
        DescriptorDigest.fromHash(
            "6f70bf18a086007016e948b04aed3b82103a36bea41755b6cddfaf10ace3c6ef");

    LayerEntry layerEntry1 =
        new LayerEntry(
            ImmutableList.of(Paths.get("some", "file"), Paths.get("some", "other", "file")),
            "extractionPath1");
    LayerEntry layerEntry2 =
        new LayerEntry(
            ImmutableList.of(Paths.get("another", "file"), Paths.get("yet", "another", "file")),
            "extractionPath2");

    LayerMetadata layerMetadata1 =
        LayerMetadata.from(
            ImmutableList.of(layerEntry1, layerEntry2), FileTime.from(Instant.now()));
    LayerMetadata layerMetadata2 =
        LayerMetadata.from(ImmutableList.of(layerEntry2), FileTime.from(Instant.EPOCH));

    DescriptorDigest mockDiffId =
        DescriptorDigest.fromHash(
            "91e0cae00b86c289b33fee303a807ae72dd9f0315c16b74e6ab0cdbe9d996c10");

    // Layers ABA.
    List<CachedLayerWithMetadata> cachedLayersWithMetadata =
        Arrays.asList(
            new CachedLayerWithMetadata(
                new CachedLayer(
                    Paths.get("nonexistent"), new BlobDescriptor(descriptorDigest1), mockDiffId),
                layerMetadata1),
            new CachedLayerWithMetadata(
                new CachedLayer(
                    Paths.get("nonexistent"), new BlobDescriptor(descriptorDigest2), mockDiffId),
                layerMetadata2),
            new CachedLayerWithMetadata(
                new CachedLayer(
                    Paths.get("nonexistent"), new BlobDescriptor(descriptorDigest1), mockDiffId),
                layerMetadata2));

    // Saves the new layers to the cache metadata.
    try (Cache cache = Cache.init(cacheDirectory)) {
      cache.addCachedLayersWithMetadataToMetadata(cachedLayersWithMetadata);
    }

    // Reload the cache and check that all digests are unique.
    try (Cache cache = Cache.init(cacheDirectory)) {
      Set<DescriptorDigest> encounteredDigests = new HashSet<>();
      for (CachedLayerWithMetadata layer : cache.getMetadata().getLayers()) {
        DescriptorDigest layerDigest = layer.getBlobDescriptor().getDigest();
        Assert.assertFalse(encounteredDigests.contains(layerDigest));
        encounteredDigests.add(layerDigest);
      }

      // The layer metadata for layer with digest descriptorDigest1 should be layerMetadata2.
      CachedLayerWithMetadata descriptorDigest1Layer =
          cache.getMetadata().getLayers().get(descriptorDigest1);
      Assert.assertNotNull(descriptorDigest1Layer);
      LayerMetadata layerMetadata = descriptorDigest1Layer.getMetadata();
      Assert.assertNotNull(layerMetadata);
      Assert.assertEquals(1, layerMetadata.getEntries().size());
      Assert.assertEquals(FileTime.from(Instant.EPOCH), layerMetadata.getLastModifiedTime());
      Assert.assertEquals(
          layerEntry2
              .getSourceFiles()
              .stream()
              .map(Path::toString)
              .collect(ImmutableList.toImmutableList()),
          layerMetadata.getEntries().get(0).getSourceFilesStrings());
      Assert.assertEquals(
          layerEntry2.getExtractionPath(), layerMetadata.getEntries().get(0).getExtractionPath());
    }
  }
}
