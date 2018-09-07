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

import com.google.cloud.tools.jib.JibLogger;
import com.google.cloud.tools.jib.blob.BlobDescriptor;
import com.google.cloud.tools.jib.cache.CachedLayer;
import com.google.cloud.tools.jib.cache.CachedLayerWithMetadata;
import com.google.cloud.tools.jib.configuration.BuildConfiguration;
import com.google.cloud.tools.jib.configuration.ContainerConfiguration;
import com.google.cloud.tools.jib.image.DescriptorDigest;
import com.google.cloud.tools.jib.image.Image;
import com.google.cloud.tools.jib.image.Layer;
import com.google.cloud.tools.jib.image.json.HistoryEntry;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import java.nio.file.Paths;
import java.security.DigestException;
import java.time.Instant;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link BuildImageStep}. */
@RunWith(MockitoJUnitRunner.class)
public class BuildImageStepTest {

  @Mock private BuildConfiguration mockBuildConfiguration;
  @Mock private ContainerConfiguration mockContainerConfiguration;
  @Mock private JibLogger mockBuildLogger;
  @Mock private PullBaseImageStep mockPullBaseImageStep;
  @Mock private PullAndCacheBaseImageLayersStep mockPullAndCacheBaseImageLayersStep;
  @Mock private PullAndCacheBaseImageLayerStep mockPullAndCacheBaseImageLayerStep;
  @Mock private BuildAndCacheApplicationLayerStep mockBuildAndCacheApplicationLayerStep;

  private DescriptorDigest testDescriptorDigest;
  private HistoryEntry nonEmptyLayerHistory;
  private HistoryEntry emptyLayerHistory;

  @Before
  public void setUp() throws DigestException {
    testDescriptorDigest =
        DescriptorDigest.fromHash(
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
    CachedLayerWithMetadata testCachedLayer =
        new CachedLayerWithMetadata(
            new CachedLayer(
                Paths.get(""), new BlobDescriptor(testDescriptorDigest), testDescriptorDigest),
            null);

    Mockito.when(mockBuildConfiguration.getBuildLogger()).thenReturn(mockBuildLogger);
    Mockito.when(mockBuildConfiguration.getContainerConfiguration())
        .thenReturn(mockContainerConfiguration);
    Mockito.when(mockBuildConfiguration.getToolName()).thenReturn("jib");
    Mockito.when(mockContainerConfiguration.getCreationTime()).thenReturn(Instant.EPOCH);
    Mockito.when(mockContainerConfiguration.getEnvironmentMap()).thenReturn(ImmutableMap.of());
    Mockito.when(mockContainerConfiguration.getProgramArguments()).thenReturn(ImmutableList.of());
    Mockito.when(mockContainerConfiguration.getExposedPorts()).thenReturn(ImmutableList.of());
    Mockito.when(mockContainerConfiguration.getEntrypoint()).thenReturn(ImmutableList.of());

    nonEmptyLayerHistory =
        HistoryEntry.builder()
            .setCreationTimestamp(Instant.EPOCH)
            .setAuthor("JibBase")
            .setCreatedBy("jib-test")
            .build();
    emptyLayerHistory =
        HistoryEntry.builder()
            .setCreationTimestamp(Instant.EPOCH)
            .setAuthor("JibBase")
            .setCreatedBy("jib-test")
            .setEmptyLayer(true)
            .build();

    Image<Layer> baseImage =
        Image.builder()
            .addEnvironment(ImmutableMap.of("BASE_ENV", "BASE_ENV_VALUE"))
            .addLabel("base.label", "base.label.value")
            .setWorkingDirectory("/base/working/directory")
            .addHistory(nonEmptyLayerHistory)
            .addHistory(emptyLayerHistory)
            .addHistory(emptyLayerHistory)
            .build();
    Mockito.when(mockPullAndCacheBaseImageLayerStep.getFuture())
        .thenReturn(Futures.immediateFuture(testCachedLayer));
    Mockito.when(mockPullAndCacheBaseImageLayersStep.getFuture())
        .thenReturn(
            Futures.immediateFuture(
                ImmutableList.of(
                    mockPullAndCacheBaseImageLayerStep,
                    mockPullAndCacheBaseImageLayerStep,
                    mockPullAndCacheBaseImageLayerStep)));
    Mockito.when(mockPullBaseImageStep.getFuture())
        .thenReturn(
            Futures.immediateFuture(
                new PullBaseImageStep.BaseImageWithAuthorization(baseImage, null)));
    Mockito.when(mockBuildAndCacheApplicationLayerStep.getFuture())
        .thenReturn(Futures.immediateFuture(testCachedLayer));
  }

  @Test
  public void test_validateAsyncDependencies() throws ExecutionException, InterruptedException {
    BuildImageStep buildImageStep =
        new BuildImageStep(
            MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor()),
            mockBuildConfiguration,
            mockPullBaseImageStep,
            mockPullAndCacheBaseImageLayersStep,
            ImmutableList.of(
                mockBuildAndCacheApplicationLayerStep,
                mockBuildAndCacheApplicationLayerStep,
                mockBuildAndCacheApplicationLayerStep));
    Image<CachedLayer> image = buildImageStep.getFuture().get().getFuture().get();
    Assert.assertEquals(
        testDescriptorDigest, image.getLayers().asList().get(0).getBlobDescriptor().getDigest());
  }

  @Test
  public void test_propagateBaseImageConfiguration()
      throws ExecutionException, InterruptedException {
    Mockito.when(mockContainerConfiguration.getEnvironmentMap())
        .thenReturn(ImmutableMap.of("MY_ENV", "MY_ENV_VALUE"));
    Mockito.when(mockContainerConfiguration.getLabels())
        .thenReturn(ImmutableMap.of("my.label", "my.label.value"));
    BuildImageStep buildImageStep =
        new BuildImageStep(
            MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor()),
            mockBuildConfiguration,
            mockPullBaseImageStep,
            mockPullAndCacheBaseImageLayersStep,
            ImmutableList.of(
                mockBuildAndCacheApplicationLayerStep,
                mockBuildAndCacheApplicationLayerStep,
                mockBuildAndCacheApplicationLayerStep));
    Image<CachedLayer> image = buildImageStep.getFuture().get().getFuture().get();
    Assert.assertEquals(
        ImmutableMap.of("BASE_ENV", "BASE_ENV_VALUE", "MY_ENV", "MY_ENV_VALUE"),
        image.getEnvironment());
    Assert.assertEquals(
        ImmutableMap.of("base.label", "base.label.value", "my.label", "my.label.value"),
        image.getLabels());
    Assert.assertEquals("/base/working/directory", image.getWorkingDirectory());

    Assert.assertEquals(image.getHistory().get(0), nonEmptyLayerHistory);
    Assert.assertEquals(image.getHistory().get(1), emptyLayerHistory);
    Assert.assertEquals(image.getHistory().get(2), emptyLayerHistory);
  }

  @Test
  public void test_generateHistoryObjects() throws ExecutionException, InterruptedException {
    BuildImageStep buildImageStep =
        new BuildImageStep(
            MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor()),
            mockBuildConfiguration,
            mockPullBaseImageStep,
            mockPullAndCacheBaseImageLayersStep,
            ImmutableList.of(
                mockBuildAndCacheApplicationLayerStep,
                mockBuildAndCacheApplicationLayerStep,
                mockBuildAndCacheApplicationLayerStep));
    Image<CachedLayer> image = buildImageStep.getFuture().get().getFuture().get();

    // Make sure history is as expected
    HistoryEntry expectedAddedBaseLayerHistory =
        HistoryEntry.builder()
            .setCreationTimestamp(Instant.EPOCH)
            .setComment("auto-generated by Jib")
            .build();
    HistoryEntry expectedApplicationLayerHistory =
        HistoryEntry.builder()
            .setCreationTimestamp(Instant.EPOCH)
            .setAuthor("Jib")
            .setCreatedBy("jib")
            .build();

    // Base layers (1 non-empty propagated, 2 empty propagated, 2 non-empty generated)
    Assert.assertEquals(image.getHistory().get(0), nonEmptyLayerHistory);
    Assert.assertEquals(image.getHistory().get(1), emptyLayerHistory);
    Assert.assertEquals(image.getHistory().get(2), emptyLayerHistory);
    Assert.assertEquals(image.getHistory().get(3), expectedAddedBaseLayerHistory);
    Assert.assertEquals(image.getHistory().get(4), expectedAddedBaseLayerHistory);

    // Application layers (3 generated)
    Assert.assertEquals(image.getHistory().get(5), expectedApplicationLayerHistory);
    Assert.assertEquals(image.getHistory().get(6), expectedApplicationLayerHistory);
    Assert.assertEquals(image.getHistory().get(7), expectedApplicationLayerHistory);

    // Should be exactly 8 total
    Assert.assertEquals(8, image.getHistory().size());
  }
}
