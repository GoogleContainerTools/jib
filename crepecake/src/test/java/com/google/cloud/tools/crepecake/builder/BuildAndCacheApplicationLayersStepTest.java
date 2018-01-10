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
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Tests for {@link BuildAndCacheApplicationLayersStep}. */
public class BuildAndCacheApplicationLayersStepTest {

  private static class TestSourceFilesConfiguration implements SourceFilesConfiguration {

    private final Set<Path> dependenciesSourceFiles;
    private final Set<Path> resourcesSourceFiles;
    private final Set<Path> classesSourceFiles;
    private final String extractionPath;

    private TestSourceFilesConfiguration(
        Set<Path> dependenciesSourceFiles,
        Set<Path> resourcesSourceFiles,
        Set<Path> classesSourceFiles,
        String extractionPath) {
      this.dependenciesSourceFiles = dependenciesSourceFiles;
      this.resourcesSourceFiles = resourcesSourceFiles;
      this.classesSourceFiles = classesSourceFiles;
      this.extractionPath = extractionPath;
    }

    @Override
    public Set<Path> getDependenciesFiles() {
      return dependenciesSourceFiles;
    }

    @Override
    public Set<Path> getResourcesFiles() {
      return resourcesSourceFiles;
    }

    @Override
    public Set<Path> getClassesFiles() {
      return classesSourceFiles;
    }

    @Override
    public String getDependenciesExtractionPath() {
      return extractionPath;
    }

    @Override
    public String getResourcesExtractionPath() {
      return extractionPath;
    }

    @Override
    public String getClassesExtractionPath() {
      return extractionPath;
    }
  }

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  private static final String fakeExtractionPath = "some/extraction/path";

  private TestSourceFilesConfiguration testSourceFilesConfiguration;

  @Before
  public void setUp() throws URISyntaxException {
    Path dependenciesSourceFile = Paths.get(Resources.getResource("layer").toURI());
    Path resourcesSourceFile = Paths.get(Resources.getResource("directoryA").toURI());
    Path classesSourceFile = Paths.get(Resources.getResource("cache").toURI());
    testSourceFilesConfiguration =
        new TestSourceFilesConfiguration(
            new HashSet<>(Collections.singletonList(dependenciesSourceFile)),
            new HashSet<>(Collections.singletonList(resourcesSourceFile)),
            new HashSet<>(Collections.singletonList(classesSourceFile)),
            fakeExtractionPath);
  }

  @Test
  public void testRun()
      throws LayerPropertyNotFoundException, DuplicateLayerException, IOException,
          CacheMetadataCorruptedException {
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
        applicationLayers.get(0),
        cacheReader.getLayerFile(
            CachedLayerType.DEPENDENCIES, testSourceFilesConfiguration.getDependenciesFiles()));
    Assert.assertEquals(
        applicationLayers.get(1),
        cacheReader.getLayerFile(
            CachedLayerType.DEPENDENCIES, testSourceFilesConfiguration.getResourcesFiles()));
    Assert.assertEquals(
        applicationLayers.get(2),
        cacheReader.getLayerFile(
            CachedLayerType.DEPENDENCIES, testSourceFilesConfiguration.getClassesFiles()));
  }
}
