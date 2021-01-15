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
import com.google.cloud.tools.jib.api.InvalidImageReferenceException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DigestException;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Assert;
import org.junit.Test;

/** Tests for {@link CacheStorageFiles}. */
public class CacheStorageFilesTest {

  private static final CacheStorageFiles TEST_CACHE_STORAGE_FILES =
      new CacheStorageFiles(Paths.get("cache/directory"));

  @Test
  public void testIsLayerFile() {
    Assert.assertTrue(
        CacheStorageFiles.isLayerFile(
            Paths.get("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")));
    Assert.assertTrue(
        CacheStorageFiles.isLayerFile(
            Paths.get("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb")));
    Assert.assertFalse(CacheStorageFiles.isLayerFile(Paths.get("is.not.layer.file")));
  }

  @Test
  public void testGetDiffId() throws DigestException, CacheCorruptedException {
    Assert.assertEquals(
        DescriptorDigest.fromHash(
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"),
        TEST_CACHE_STORAGE_FILES.getDigestFromFilename(
            Paths.get(
                "layer",
                "file",
                "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb")));
    Assert.assertEquals(
        DescriptorDigest.fromHash(
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"),
        TEST_CACHE_STORAGE_FILES.getDigestFromFilename(
            Paths.get("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")));
  }

  @Test
  public void testGetDiffId_corrupted() {
    try {
      TEST_CACHE_STORAGE_FILES.getDigestFromFilename(Paths.get("not long enough"));
      Assert.fail("Should have thrown CacheCorruptedException");

    } catch (CacheCorruptedException ex) {
      MatcherAssert.assertThat(
          ex.getMessage(),
          CoreMatchers.startsWith("Layer file did not include valid hash: not long enough"));
      MatcherAssert.assertThat(ex.getCause(), CoreMatchers.instanceOf(DigestException.class));
    }

    try {
      TEST_CACHE_STORAGE_FILES.getDigestFromFilename(
          Paths.get(
              "not valid hash bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"));
      Assert.fail("Should have thrown CacheCorruptedException");

    } catch (CacheCorruptedException ex) {
      MatcherAssert.assertThat(
          ex.getMessage(),
          CoreMatchers.startsWith(
              "Layer file did not include valid hash: "
                  + "not valid hash bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"));
      MatcherAssert.assertThat(ex.getCause(), CoreMatchers.instanceOf(DigestException.class));
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
        TEST_CACHE_STORAGE_FILES.getLayerFile(layerDigest, diffId));
  }

  @Test
  public void testGetLayerFilename() throws DigestException {
    DescriptorDigest diffId =
        DescriptorDigest.fromHash(
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");

    Assert.assertEquals(
        "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
        TEST_CACHE_STORAGE_FILES.getLayerFilename(diffId));
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
        TEST_CACHE_STORAGE_FILES.getSelectorFile(selector));
  }

  @Test
  public void testGetLayersDirectory() {
    Assert.assertEquals(
        Paths.get("cache", "directory", "layers"), TEST_CACHE_STORAGE_FILES.getLayersDirectory());
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
        TEST_CACHE_STORAGE_FILES.getLayerDirectory(layerDigest));
  }

  @Test
  public void testGetTemporaryDirectory() {
    Assert.assertEquals(
        Paths.get("cache/directory/tmp"), TEST_CACHE_STORAGE_FILES.getTemporaryDirectory());
  }

  @Test
  public void testGetImagesDirectory() {
    Assert.assertEquals(
        Paths.get("cache/directory/images"), TEST_CACHE_STORAGE_FILES.getImagesDirectory());
  }

  @Test
  public void testGetImageDirectory() throws InvalidImageReferenceException {
    Path imagesDirectory = Paths.get("cache", "directory", "images");
    Assert.assertEquals(imagesDirectory, TEST_CACHE_STORAGE_FILES.getImagesDirectory());

    Assert.assertEquals(
        imagesDirectory.resolve("reg.istry/repo/sitory!tag"),
        TEST_CACHE_STORAGE_FILES.getImageDirectory(
            ImageReference.parse("reg.istry/repo/sitory:tag")));
    Assert.assertEquals(
        imagesDirectory.resolve(
            "reg.istry/repo!sha256!aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"),
        TEST_CACHE_STORAGE_FILES.getImageDirectory(
            ImageReference.parse(
                "reg.istry/repo@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")));
    Assert.assertEquals(
        imagesDirectory.resolve("reg.istry!5000/repo/sitory!tag"),
        TEST_CACHE_STORAGE_FILES.getImageDirectory(
            ImageReference.parse("reg.istry:5000/repo/sitory:tag")));
  }
}
