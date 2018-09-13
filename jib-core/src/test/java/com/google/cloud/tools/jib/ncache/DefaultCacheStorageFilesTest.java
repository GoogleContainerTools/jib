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

package com.google.cloud.tools.jib.ncache;

import com.google.cloud.tools.jib.image.DescriptorDigest;
import java.nio.file.Paths;
import java.security.DigestException;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Test;

/** Tests for {@link DefaultCacheStorageFiles}. */
public class DefaultCacheStorageFilesTest {

  private static final DefaultCacheStorageFiles testDefaultCacheStorageFiles =
      new DefaultCacheStorageFiles(Paths.get("cache/directory"));

  @Test
  public void testIsLayerFile() {
    Assert.assertTrue(
        DefaultCacheStorageFiles.isLayerFile(
            Paths.get("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")));
    Assert.assertTrue(
        DefaultCacheStorageFiles.isLayerFile(
            Paths.get("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb")));
    Assert.assertFalse(DefaultCacheStorageFiles.isLayerFile(Paths.get("is.not.layer.file")));
  }

  @Test
  public void testIsMetadataFile() {
    Assert.assertTrue(DefaultCacheStorageFiles.isMetadataFile(Paths.get("metadata")));
    Assert.assertTrue(DefaultCacheStorageFiles.isMetadataFile(Paths.get("is", "metadata")));
    Assert.assertFalse(DefaultCacheStorageFiles.isMetadataFile(Paths.get("not.metadata")));
  }

  @Test
  public void testGetDiffId() throws DigestException, CacheCorruptedException {
    Assert.assertEquals(
        DescriptorDigest.fromHash(
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"),
        DefaultCacheStorageFiles.getDiffId(
            Paths.get(
                "layer",
                "file",
                "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb")));
    Assert.assertEquals(
        DescriptorDigest.fromHash(
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"),
        DefaultCacheStorageFiles.getDiffId(
            Paths.get("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")));
  }

  @Test
  public void testGetDiffId_corrupted() {
    try {
      DefaultCacheStorageFiles.getDiffId(Paths.get("not long enough"));
      Assert.fail("Should have thrown CacheCorruptedException");

    } catch (CacheCorruptedException ex) {
      Assert.assertEquals(
          "Layer file did not include valid diff ID: not long enough", ex.getMessage());
      Assert.assertThat(ex.getCause(), CoreMatchers.instanceOf(DigestException.class));
    }

    try {
      DefaultCacheStorageFiles.getDiffId(
          Paths.get(
              "not valid hash bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"));
      Assert.fail("Should have thrown CacheCorruptedException");

    } catch (CacheCorruptedException ex) {
      Assert.assertEquals(
          "Layer file did not include valid diff ID: "
              + "not valid hash bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
          ex.getMessage());
      Assert.assertThat(ex.getCause(), CoreMatchers.instanceOf(DigestException.class));
    }
  }

  @Test
  public void testGetLayerFile() throws DigestException {
    DescriptorDigest layerDigest =
        DescriptorDigest.fromHash(
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
    DescriptorDigest diffId =
        DescriptorDigest.fromHash(
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");

    Assert.assertEquals(
        Paths.get(
            "cache",
            "directory",
            "layers",
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"),
        testDefaultCacheStorageFiles.getLayerFile(layerDigest, diffId));
  }

  @Test
  public void testGetLayerFilename() throws DigestException {
    DescriptorDigest diffId =
        DescriptorDigest.fromHash(
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");

    Assert.assertEquals(
        "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
        testDefaultCacheStorageFiles.getLayerFilename(diffId));
  }

  @Test
  public void testGetMetadataFile() throws DigestException {
    DescriptorDigest layerDigest =
        DescriptorDigest.fromHash(
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");

    Assert.assertEquals(
        Paths.get(
            "cache",
            "directory",
            "layers",
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            "metadata"),
        testDefaultCacheStorageFiles.getMetadataFile(layerDigest));
  }

  @Test
  public void testGetMetadataFilename() {
    Assert.assertEquals("metadata", testDefaultCacheStorageFiles.getMetadataFilename());
  }

  @Test
  public void testGetSelectorFile() throws DigestException {
    DescriptorDigest selector =
        DescriptorDigest.fromHash(
            "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc");

    Assert.assertEquals(
        Paths.get(
            "cache",
            "directory",
            "selectors",
            "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"),
        testDefaultCacheStorageFiles.getSelectorFile(selector));
  }

  @Test
  public void testGetLayersDirectory() {
    Assert.assertEquals(
        Paths.get("cache", "directory", "layers"),
        testDefaultCacheStorageFiles.getLayersDirectory());
  }

  @Test
  public void testGetLayerDirectory() throws DigestException {
    DescriptorDigest layerDigest =
        DescriptorDigest.fromHash(
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");

    Assert.assertEquals(
        Paths.get(
            "cache",
            "directory",
            "layers",
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"),
        testDefaultCacheStorageFiles.getLayerDirectory(layerDigest));
  }
}
