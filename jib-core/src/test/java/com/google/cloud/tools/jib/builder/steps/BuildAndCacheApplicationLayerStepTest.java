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

package com.google.cloud.tools.jib.builder.steps;

import com.google.cloud.tools.jib.async.NonBlockingSteps;
import com.google.cloud.tools.jib.builder.BuildConfiguration;
import com.google.cloud.tools.jib.builder.TestBuildLogger;
import com.google.cloud.tools.jib.builder.TestSourceFilesConfiguration;
import com.google.cloud.tools.jib.cache.Cache;
import com.google.cloud.tools.jib.cache.CacheMetadataCorruptedException;
import com.google.cloud.tools.jib.cache.CacheReader;
import com.google.cloud.tools.jib.cache.CachedLayerWithMetadata;
import com.google.cloud.tools.jib.configuration.LayerConfiguration;
import com.google.cloud.tools.jib.image.ImageLayers;
import com.google.cloud.tools.jib.image.LayerEntry;
import com.google.cloud.tools.jib.image.LayerPropertyNotFoundException;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutionException;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link BuildAndCacheApplicationLayerStep}. */
@RunWith(MockitoJUnitRunner.class)
public class BuildAndCacheApplicationLayerStepTest {

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Mock private BuildConfiguration mockBuildConfiguration;

  @Test
  public void testRun()
      throws LayerPropertyNotFoundException, IOException, CacheMetadataCorruptedException,
          URISyntaxException, ExecutionException {
    Mockito.when(mockBuildConfiguration.getBuildLogger()).thenReturn(new TestBuildLogger());
    TestSourceFilesConfiguration testSourceFilesConfiguration = new TestSourceFilesConfiguration();
    Path temporaryCacheDirectory = temporaryFolder.newFolder().toPath();

    // Adds an extra file layer.
    ImmutableList<Path> extraFilesLayerSourceFiles =
        ImmutableList.of(
            Paths.get(Resources.getResource("fileA").toURI()),
            Paths.get(Resources.getResource("fileB").toURI()));
    String extraFilesLayerExtractionPath = "/extra";
    Mockito.when(mockBuildConfiguration.getExtraFilesLayerConfiguration())
        .thenReturn(
            LayerConfiguration.builder()
                .addEntry(extraFilesLayerSourceFiles, extraFilesLayerExtractionPath)
                .build());

    ImageLayers.Builder<CachedLayerWithMetadata> applicationLayersBuilder = ImageLayers.builder();
    ImageLayers<CachedLayerWithMetadata> applicationLayers;

    try (Cache cache = Cache.init(temporaryCacheDirectory)) {
      ImmutableList<BuildAndCacheApplicationLayerStep> buildAndCacheApplicationLayerSteps =
          BuildAndCacheApplicationLayerStep.makeList(
              MoreExecutors.newDirectExecutorService(),
              mockBuildConfiguration,
              testSourceFilesConfiguration,
              cache);

      for (BuildAndCacheApplicationLayerStep buildAndCacheApplicationLayerStep :
          buildAndCacheApplicationLayerSteps) {
        applicationLayersBuilder.add(NonBlockingSteps.get(buildAndCacheApplicationLayerStep));
      }

      applicationLayers = applicationLayersBuilder.build();
      cache.addCachedLayersWithMetadataToMetadata(applicationLayers.getLayers());
      Assert.assertEquals(5, applicationLayers.size());
    }

    // Re-initialize cache with the updated metadata.
    Cache cache = Cache.init(temporaryCacheDirectory);

    ImmutableList<LayerEntry> dependenciesLayerEntry =
        ImmutableList.of(
            new LayerEntry(
                testSourceFilesConfiguration.getDependenciesFiles(),
                testSourceFilesConfiguration.getDependenciesPathOnImage()));
    ImmutableList<LayerEntry> snapshotDependenciesLayerEntry =
        ImmutableList.of(
            new LayerEntry(
                testSourceFilesConfiguration.getSnapshotDependenciesFiles(),
                testSourceFilesConfiguration.getSnapshotDependenciesPathOnImage()));
    ImmutableList<LayerEntry> resourcesLayerEntry =
        ImmutableList.of(
            new LayerEntry(
                testSourceFilesConfiguration.getResourcesFiles(),
                testSourceFilesConfiguration.getResourcesPathOnImage()));
    ImmutableList<LayerEntry> classesLayerEntry =
        ImmutableList.of(
            new LayerEntry(
                testSourceFilesConfiguration.getClassesFiles(),
                testSourceFilesConfiguration.getClassesPathOnImage()));
    ImmutableList<LayerEntry> extraFilesLayerEntry =
        ImmutableList.of(new LayerEntry(extraFilesLayerSourceFiles, extraFilesLayerExtractionPath));

    // Verifies that the cached layers are up-to-date.
    CacheReader cacheReader = new CacheReader(cache);
    Assert.assertEquals(
        applicationLayers.get(0).getBlobDescriptor(),
        cacheReader.getUpToDateLayerByLayerEntries(dependenciesLayerEntry).getBlobDescriptor());
    Assert.assertEquals(
        applicationLayers.get(1).getBlobDescriptor(),
        cacheReader
            .getUpToDateLayerByLayerEntries(snapshotDependenciesLayerEntry)
            .getBlobDescriptor());
    Assert.assertEquals(
        applicationLayers.get(2).getBlobDescriptor(),
        cacheReader.getUpToDateLayerByLayerEntries(resourcesLayerEntry).getBlobDescriptor());
    Assert.assertEquals(
        applicationLayers.get(3).getBlobDescriptor(),
        cacheReader.getUpToDateLayerByLayerEntries(classesLayerEntry).getBlobDescriptor());
    Assert.assertEquals(
        applicationLayers.get(4).getBlobDescriptor(),
        cacheReader.getUpToDateLayerByLayerEntries(extraFilesLayerEntry).getBlobDescriptor());

    // Verifies that the cache reader gets the same layers as the newest application layers.
    Assert.assertEquals(
        applicationLayers.get(0).getContentFile(),
        cacheReader.getLayerFile(dependenciesLayerEntry));
    Assert.assertEquals(
        applicationLayers.get(1).getContentFile(),
        cacheReader.getLayerFile(snapshotDependenciesLayerEntry));
    Assert.assertEquals(
        applicationLayers.get(2).getContentFile(), cacheReader.getLayerFile(resourcesLayerEntry));
    Assert.assertEquals(
        applicationLayers.get(3).getContentFile(), cacheReader.getLayerFile(classesLayerEntry));
    Assert.assertEquals(
        applicationLayers.get(4).getContentFile(), cacheReader.getLayerFile(extraFilesLayerEntry));
  }
}
