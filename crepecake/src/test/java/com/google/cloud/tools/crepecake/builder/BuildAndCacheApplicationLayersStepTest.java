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

package com.google.cloud.tools.crepecake.builder;

import com.google.cloud.tools.crepecake.cache.Cache;
import com.google.cloud.tools.crepecake.cache.CacheChecker;
import com.google.cloud.tools.crepecake.cache.CacheMetadataCorruptedException;
import com.google.cloud.tools.crepecake.cache.CacheReader;
import com.google.cloud.tools.crepecake.cache.CachedLayer;
import com.google.cloud.tools.crepecake.cache.CachedLayerType;
import com.google.cloud.tools.crepecake.image.DuplicateLayerException;
import com.google.cloud.tools.crepecake.image.ImageLayers;
import com.google.cloud.tools.crepecake.image.LayerPropertyNotFoundException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Tests for {@link BuildAndCacheApplicationLayersStep}. */
public class BuildAndCacheApplicationLayersStepTest {

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void testRun()
      throws LayerPropertyNotFoundException, DuplicateLayerException, IOException,
          CacheMetadataCorruptedException, URISyntaxException {
    TestSourceFilesConfiguration testSourceFilesConfiguration = new TestSourceFilesConfiguration();
    Path temporaryCacheDirectory = temporaryFolder.newFolder().toPath();

    ImageLayers<CachedLayer> applicationLayers;

    try (Cache cache = Cache.init(temporaryCacheDirectory)) {
      BuildAndCacheApplicationLayersStep buildAndCacheApplicationLayersStep =
          new BuildAndCacheApplicationLayersStep(testSourceFilesConfiguration, cache);

      applicationLayers = buildAndCacheApplicationLayersStep.run(null);

      Assert.assertEquals(3, applicationLayers.size());
    }

    // Re-initialize cache with the updated metadata.
    Cache cache = Cache.init(temporaryCacheDirectory);

    // Verifies that the cached layers are up-to-date.
    CacheChecker cacheChecker = new CacheChecker(cache);
    Assert.assertFalse(
        cacheChecker.areSourceFilesModified(testSourceFilesConfiguration.getDependenciesFiles()));
    Assert.assertFalse(
        cacheChecker.areSourceFilesModified(testSourceFilesConfiguration.getResourcesFiles()));
    Assert.assertFalse(
        cacheChecker.areSourceFilesModified(testSourceFilesConfiguration.getClassesFiles()));

    // Verifies that the cache reader gets the same layers as the newest application layers.
    CacheReader cacheReader = new CacheReader(cache);
    Assert.assertEquals(
        applicationLayers.get(0).getContentFile(),
        cacheReader.getLayerFile(
            CachedLayerType.DEPENDENCIES, testSourceFilesConfiguration.getDependenciesFiles()));
    Assert.assertEquals(
        applicationLayers.get(1).getContentFile(),
        cacheReader.getLayerFile(
            CachedLayerType.RESOURCES, testSourceFilesConfiguration.getResourcesFiles()));
    Assert.assertEquals(
        applicationLayers.get(2).getContentFile(),
        cacheReader.getLayerFile(
            CachedLayerType.CLASSES, testSourceFilesConfiguration.getClassesFiles()));
  }
}
