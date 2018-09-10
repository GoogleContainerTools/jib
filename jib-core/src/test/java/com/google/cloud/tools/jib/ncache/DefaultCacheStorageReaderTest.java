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
import java.io.IOException;
import java.nio.file.Files;
import java.security.DigestException;
import java.util.Arrays;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Tests for {@link DefaultCacheStorageReader}. */
public class DefaultCacheStorageReaderTest {

  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void testListDigests() throws IOException, DigestException, CacheCorruptedException {
    DefaultCacheStorageFiles defaultCacheStorageFiles =
        new DefaultCacheStorageFiles(temporaryFolder.newFolder().toPath());

    DefaultCacheStorageReader defaultCacheStorageReader =
        new DefaultCacheStorageReader(defaultCacheStorageFiles);

    // Checks that layer directories created are all listed.
    DescriptorDigest layerDigest1 =
        DescriptorDigest.fromHash(
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
    DescriptorDigest layerDigest2 =
        DescriptorDigest.fromHash(
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
    Files.createDirectories(
        defaultCacheStorageFiles.getLayersDirectory().resolve(layerDigest1.getHash()));
    Files.createDirectories(
        defaultCacheStorageFiles.getLayersDirectory().resolve(layerDigest2.getHash()));

    Assert.assertEquals(
        Arrays.asList(layerDigest1, layerDigest2), defaultCacheStorageReader.listDigests());

    // Checks that non-digest directories means the cache is corrupted.
    Files.createDirectory(defaultCacheStorageFiles.getLayersDirectory().resolve("not a hash"));
    try {
      defaultCacheStorageReader.listDigests();
      Assert.fail("Listing digests should have failed");

    } catch (CacheCorruptedException ex) {
      Assert.assertEquals("Found non-digest file in layers directory", ex.getMessage());
      Assert.assertThat(ex.getCause(), CoreMatchers.instanceOf(DigestException.class));
    }
  }
}
