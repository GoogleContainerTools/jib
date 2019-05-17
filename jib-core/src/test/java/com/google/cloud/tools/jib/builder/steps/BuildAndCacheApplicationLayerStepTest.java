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

package com.google.cloud.tools.jib.builder.steps;

import com.google.cloud.tools.jib.api.AbsoluteUnixPath;
import com.google.cloud.tools.jib.async.NonBlockingSteps;
import com.google.cloud.tools.jib.blob.Blob;
import com.google.cloud.tools.jib.blob.Blobs;
import com.google.cloud.tools.jib.builder.ProgressEventDispatcher;
import com.google.cloud.tools.jib.cache.Cache;
import com.google.cloud.tools.jib.cache.CacheCorruptedException;
import com.google.cloud.tools.jib.cache.CachedLayer;
import com.google.cloud.tools.jib.configuration.BuildConfiguration;
import com.google.cloud.tools.jib.configuration.LayerConfiguration;
import com.google.cloud.tools.jib.event.EventHandlers;
import com.google.cloud.tools.jib.image.ImageLayers;
import com.google.cloud.tools.jib.image.LayerEntry;
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
  private static final AbsoluteUnixPath EXTRACTION_PATH_ROOT =
      AbsoluteUnixPath.get("/some/extraction/path/");

  private static final AbsoluteUnixPath EXTRA_FILES_LAYER_EXTRACTION_PATH =
      AbsoluteUnixPath.get("/extra");

  /**
   * Lists the files in the {@code resourcePath} resources directory and creates a {@link
   * LayerConfiguration} with entries from those files.
   */
  private static LayerConfiguration makeLayerConfiguration(
      String resourcePath, AbsoluteUnixPath extractionPath) throws URISyntaxException, IOException {
    try (Stream<Path> fileStream =
        Files.list(Paths.get(Resources.getResource(resourcePath).toURI()))) {
      LayerConfiguration.Builder layerConfigurationBuilder = LayerConfiguration.builder();
      fileStream.forEach(
          sourceFile ->
              layerConfigurationBuilder.addEntry(
                  sourceFile, extractionPath.resolve(sourceFile.getFileName())));
      return layerConfigurationBuilder.build();
    }
  }

  private static void assertBlobsEqual(Blob expectedBlob, Blob blob) throws IOException {
    Assert.assertArrayEquals(Blobs.writeToByteArray(expectedBlob), Blobs.writeToByteArray(blob));
  }

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Mock private BuildConfiguration mockBuildConfiguration;

  private Cache cache;
  @Mock private EventHandlers mockEventHandlers;

  private LayerConfiguration fakeDependenciesLayerConfiguration;
  private LayerConfiguration fakeSnapshotDependenciesLayerConfiguration;
  private LayerConfiguration fakeResourcesLayerConfiguration;
  private LayerConfiguration fakeClassesLayerConfiguration;
  private LayerConfiguration fakeExtraFilesLayerConfiguration;
  private LayerConfiguration emptyLayerConfiguration;

  @Before
  public void setUp() throws IOException, URISyntaxException {
    fakeDependenciesLayerConfiguration =
        makeLayerConfiguration(
            "core/application/dependencies", EXTRACTION_PATH_ROOT.resolve("libs"));
    fakeSnapshotDependenciesLayerConfiguration =
        makeLayerConfiguration(
            "core/application/snapshot-dependencies", EXTRACTION_PATH_ROOT.resolve("libs"));
    fakeResourcesLayerConfiguration =
        makeLayerConfiguration(
            "core/application/resources", EXTRACTION_PATH_ROOT.resolve("resources"));
    fakeClassesLayerConfiguration =
        makeLayerConfiguration("core/application/classes", EXTRACTION_PATH_ROOT.resolve("classes"));
    fakeExtraFilesLayerConfiguration =
        LayerConfiguration.builder()
            .addEntry(
                Paths.get(Resources.getResource("core/fileA").toURI()),
                EXTRA_FILES_LAYER_EXTRACTION_PATH.resolve("fileA"))
            .addEntry(
                Paths.get(Resources.getResource("core/fileB").toURI()),
                EXTRA_FILES_LAYER_EXTRACTION_PATH.resolve("fileB"))
            .build();
    emptyLayerConfiguration = LayerConfiguration.builder().build();

    cache = Cache.withDirectory(temporaryFolder.newFolder().toPath());

    Mockito.when(mockBuildConfiguration.getEventHandlers()).thenReturn(mockEventHandlers);
    Mockito.when(mockBuildConfiguration.getApplicationLayersCache()).thenReturn(cache);
  }

  private ImageLayers buildFakeLayersToCache() throws ExecutionException {
    ImageLayers.Builder applicationLayersBuilder = ImageLayers.builder();

    ImmutableList<BuildAndCacheApplicationLayerStep> buildAndCacheApplicationLayerSteps =
        BuildAndCacheApplicationLayerStep.makeList(
            MoreExecutors.newDirectExecutorService(),
            mockBuildConfiguration,
            ProgressEventDispatcher.newRoot(mockEventHandlers, "ignored", 1).newChildProducer());

    for (BuildAndCacheApplicationLayerStep buildAndCacheApplicationLayerStep :
        buildAndCacheApplicationLayerSteps) {
      applicationLayersBuilder.add(NonBlockingSteps.get(buildAndCacheApplicationLayerStep));
    }

    return applicationLayersBuilder.build();
  }

  @Test
  public void testRun()
      throws LayerPropertyNotFoundException, IOException, ExecutionException,
          CacheCorruptedException {
    ImmutableList<LayerConfiguration> fakeLayerConfigurations =
        ImmutableList.of(
            fakeDependenciesLayerConfiguration,
            fakeSnapshotDependenciesLayerConfiguration,
            fakeResourcesLayerConfiguration,
            fakeClassesLayerConfiguration,
            fakeExtraFilesLayerConfiguration);
    Mockito.when(mockBuildConfiguration.getLayerConfigurations())
        .thenReturn(fakeLayerConfigurations);

    // Populates the cache.
    ImageLayers applicationLayers = buildFakeLayersToCache();
    Assert.assertEquals(5, applicationLayers.size());

    ImmutableList<LayerEntry> dependenciesLayerEntries =
        fakeLayerConfigurations.get(0).getLayerEntries();
    ImmutableList<LayerEntry> snapshotDependenciesLayerEntries =
        fakeLayerConfigurations.get(1).getLayerEntries();
    ImmutableList<LayerEntry> resourcesLayerEntries =
        fakeLayerConfigurations.get(2).getLayerEntries();
    ImmutableList<LayerEntry> classesLayerEntries =
        fakeLayerConfigurations.get(3).getLayerEntries();
    ImmutableList<LayerEntry> extraFilesLayerEntries =
        fakeLayerConfigurations.get(4).getLayerEntries();

    CachedLayer dependenciesCachedLayer =
        cache.retrieve(dependenciesLayerEntries).orElseThrow(AssertionError::new);
    CachedLayer snapshotDependenciesCachedLayer =
        cache.retrieve(snapshotDependenciesLayerEntries).orElseThrow(AssertionError::new);
    CachedLayer resourcesCachedLayer =
        cache.retrieve(resourcesLayerEntries).orElseThrow(AssertionError::new);
    CachedLayer classesCachedLayer =
        cache.retrieve(classesLayerEntries).orElseThrow(AssertionError::new);
    CachedLayer extraFilesCachedLayer =
        cache.retrieve(extraFilesLayerEntries).orElseThrow(AssertionError::new);

    // Verifies that the cached layers are up-to-date.
    Assert.assertEquals(
        applicationLayers.get(0).getBlobDescriptor().getDigest(),
        dependenciesCachedLayer.getDigest());
    Assert.assertEquals(
        applicationLayers.get(1).getBlobDescriptor().getDigest(),
        snapshotDependenciesCachedLayer.getDigest());
    Assert.assertEquals(
        applicationLayers.get(2).getBlobDescriptor().getDigest(), resourcesCachedLayer.getDigest());
    Assert.assertEquals(
        applicationLayers.get(3).getBlobDescriptor().getDigest(), classesCachedLayer.getDigest());
    Assert.assertEquals(
        applicationLayers.get(4).getBlobDescriptor().getDigest(),
        extraFilesCachedLayer.getDigest());

    // Verifies that the cache reader gets the same layers as the newest application layers.
    assertBlobsEqual(applicationLayers.get(0).getBlob(), dependenciesCachedLayer.getBlob());
    assertBlobsEqual(applicationLayers.get(1).getBlob(), snapshotDependenciesCachedLayer.getBlob());
    assertBlobsEqual(applicationLayers.get(2).getBlob(), resourcesCachedLayer.getBlob());
    assertBlobsEqual(applicationLayers.get(3).getBlob(), classesCachedLayer.getBlob());
    assertBlobsEqual(applicationLayers.get(4).getBlob(), extraFilesCachedLayer.getBlob());
  }

  @Test
  public void testRun_emptyLayersIgnored()
      throws IOException, ExecutionException, CacheCorruptedException {
    ImmutableList<LayerConfiguration> fakeLayerConfigurations =
        ImmutableList.of(
            fakeDependenciesLayerConfiguration,
            emptyLayerConfiguration,
            fakeResourcesLayerConfiguration,
            fakeClassesLayerConfiguration,
            emptyLayerConfiguration);
    Mockito.when(mockBuildConfiguration.getLayerConfigurations())
        .thenReturn(fakeLayerConfigurations);

    // Populates the cache.
    ImageLayers applicationLayers = buildFakeLayersToCache();
    Assert.assertEquals(3, applicationLayers.size());

    ImmutableList<LayerEntry> dependenciesLayerEntries =
        fakeLayerConfigurations.get(0).getLayerEntries();
    ImmutableList<LayerEntry> resourcesLayerEntries =
        fakeLayerConfigurations.get(2).getLayerEntries();
    ImmutableList<LayerEntry> classesLayerEntries =
        fakeLayerConfigurations.get(3).getLayerEntries();

    CachedLayer dependenciesCachedLayer =
        cache.retrieve(dependenciesLayerEntries).orElseThrow(AssertionError::new);
    CachedLayer resourcesCachedLayer =
        cache.retrieve(resourcesLayerEntries).orElseThrow(AssertionError::new);
    CachedLayer classesCachedLayer =
        cache.retrieve(classesLayerEntries).orElseThrow(AssertionError::new);

    // Verifies that the cached layers are up-to-date.
    Assert.assertEquals(
        applicationLayers.get(0).getBlobDescriptor().getDigest(),
        dependenciesCachedLayer.getDigest());
    Assert.assertEquals(
        applicationLayers.get(1).getBlobDescriptor().getDigest(), resourcesCachedLayer.getDigest());
    Assert.assertEquals(
        applicationLayers.get(2).getBlobDescriptor().getDigest(), classesCachedLayer.getDigest());

    // Verifies that the cache reader gets the same layers as the newest application layers.
    assertBlobsEqual(applicationLayers.get(0).getBlob(), dependenciesCachedLayer.getBlob());
    assertBlobsEqual(applicationLayers.get(1).getBlob(), resourcesCachedLayer.getBlob());
    assertBlobsEqual(applicationLayers.get(2).getBlob(), classesCachedLayer.getBlob());
  }
}
