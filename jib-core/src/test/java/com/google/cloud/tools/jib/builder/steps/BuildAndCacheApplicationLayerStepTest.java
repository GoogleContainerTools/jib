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
import com.google.cloud.tools.jib.cache.Cache;
import com.google.cloud.tools.jib.cache.CacheMetadataCorruptedException;
import com.google.cloud.tools.jib.cache.CacheReader;
import com.google.cloud.tools.jib.cache.CachedLayerWithMetadata;
import com.google.cloud.tools.jib.configuration.LayerConfiguration;
import com.google.cloud.tools.jib.image.ImageLayers;
import com.google.cloud.tools.jib.image.LayerPropertyNotFoundException;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;
import org.junit.Assert;
import org.junit.Before;
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

  // TODO: Consolidate with BuildStepsIntegrationTest.
  private static final String EXTRACTION_PATH = "/some/extraction/path/";

  /** Lists the files in the {@code resourcePath} resources directory. */
  private static ImmutableList<Path> getFilesList(String resourcePath)
      throws URISyntaxException, IOException {
    try (Stream<Path> fileStream =
        Files.list(Paths.get(Resources.getResource(resourcePath).toURI()))) {
      return fileStream.collect(ImmutableList.toImmutableList());
    }
  }

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Mock private BuildConfiguration mockBuildConfiguration;

  private ImmutableList<LayerConfiguration> fakeLayerConfigurations;

  @Before
  public void setUp() throws IOException, URISyntaxException {
    fakeLayerConfigurations =
        ImmutableList.of(
            LayerConfiguration.builder()
                .addEntry(getFilesList("application/dependencies"), EXTRACTION_PATH + "libs/")
                .build(),
            LayerConfiguration.builder()
                .addEntry(getFilesList("application/resources"), EXTRACTION_PATH + "resources/")
                .build(),
            LayerConfiguration.builder()
                .addEntry(getFilesList("application/classes"), EXTRACTION_PATH + "classes/")
                .build());
  }

  @Test
  public void testRun()
      throws LayerPropertyNotFoundException, IOException, CacheMetadataCorruptedException,
          ExecutionException {
    Mockito.when(mockBuildConfiguration.getBuildLogger()).thenReturn(new TestBuildLogger());
    Mockito.when(mockBuildConfiguration.getLayerConfigurations())
        .thenReturn(fakeLayerConfigurations);
    Path temporaryCacheDirectory = temporaryFolder.newFolder().toPath();

    ImageLayers.Builder<CachedLayerWithMetadata> applicationLayersBuilder = ImageLayers.builder();
    ImageLayers<CachedLayerWithMetadata> applicationLayers;

    try (Cache cache = Cache.init(temporaryCacheDirectory)) {
      ImmutableList<BuildAndCacheApplicationLayerStep> buildAndCacheApplicationLayerSteps =
          BuildAndCacheApplicationLayerStep.makeList(
              MoreExecutors.newDirectExecutorService(), mockBuildConfiguration, cache);

      for (BuildAndCacheApplicationLayerStep buildAndCacheApplicationLayerStep :
          buildAndCacheApplicationLayerSteps) {
        applicationLayersBuilder.add(NonBlockingSteps.get(buildAndCacheApplicationLayerStep));
      }

      applicationLayers = applicationLayersBuilder.build();
      cache.addCachedLayersWithMetadataToMetadata(applicationLayers.getLayers());
      Assert.assertEquals(3, applicationLayers.size());
    }

    // Re-initialize cache with the updated metadata.
    Cache cache = Cache.init(temporaryCacheDirectory);

    // Verifies that the cached layers are up-to-date.
    CacheReader cacheReader = new CacheReader(cache);
    Assert.assertEquals(
        applicationLayers.get(0).getBlobDescriptor(),
        cacheReader
            .getUpToDateLayerBySourceFiles(
                fakeLayerConfigurations.get(0).getLayerEntries().get(0).getSourceFiles())
            .getBlobDescriptor());
    Assert.assertEquals(
        applicationLayers.get(1).getBlobDescriptor(),
        cacheReader
            .getUpToDateLayerBySourceFiles(
                fakeLayerConfigurations.get(1).getLayerEntries().get(0).getSourceFiles())
            .getBlobDescriptor());
    Assert.assertEquals(
        applicationLayers.get(2).getBlobDescriptor(),
        cacheReader
            .getUpToDateLayerBySourceFiles(
                fakeLayerConfigurations.get(2).getLayerEntries().get(0).getSourceFiles())
            .getBlobDescriptor());

    // Verifies that the cache reader gets the same layers as the newest application layers.
    Assert.assertEquals(
        applicationLayers.get(0).getContentFile(),
        cacheReader.getLayerFile(
            fakeLayerConfigurations.get(0).getLayerEntries().get(0).getSourceFiles()));
    Assert.assertEquals(
        applicationLayers.get(1).getContentFile(),
        cacheReader.getLayerFile(
            fakeLayerConfigurations.get(1).getLayerEntries().get(0).getSourceFiles()));
    Assert.assertEquals(
        applicationLayers.get(2).getContentFile(),
        cacheReader.getLayerFile(
            fakeLayerConfigurations.get(2).getLayerEntries().get(0).getSourceFiles()));
  }
}
