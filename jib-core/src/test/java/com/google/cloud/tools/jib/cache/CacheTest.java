/*
 * Copyright 2017 Google LLC. All rights reserved.
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

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
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
}
