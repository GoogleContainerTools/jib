/*
 * Copyright 2018 Google Inc.
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

package com.google.cloud.tools.crepecake.cache;

import com.google.common.io.Resources;
import java.io.File;
import java.net.URISyntaxException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashSet;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link CacheReader}. */
public class CacheReaderTest {

  private static Cache testCache;

  @Before
  public void setUp()
      throws CacheMetadataCorruptedException, NotDirectoryException, URISyntaxException {
    Path testCacheFolder = Paths.get(Resources.getResource("cache").toURI());
    testCache = Cache.init(testCacheFolder);
  }

  @Test
  public void testGetLayerFile() throws URISyntaxException, CacheMetadataCorruptedException {
    File expectedFile =
        new File(
            Resources.getResource(
                    "cache/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.tar.gz")
                .toURI());

    CacheReader cacheReader = new CacheReader(testCache);

    Assert.assertEquals(
        expectedFile,
        cacheReader.getLayerFile(
            CachedLayerType.CLASSES,
            new HashSet<>(Collections.singletonList(Paths.get("some/source/directory")))));
    Assert.assertNull(cacheReader.getLayerFile(CachedLayerType.RESOURCES, new HashSet<>()));
    Assert.assertNull(cacheReader.getLayerFile(CachedLayerType.DEPENDENCIES, new HashSet<>()));
    try {
      cacheReader.getLayerFile(CachedLayerType.BASE, new HashSet<>());
      Assert.fail("Should not be able to get layer file for base image layer");

    } catch (UnsupportedOperationException ex) {
      Assert.assertEquals("Can only find layer files for application layers", ex.getMessage());
    }
  }
}
