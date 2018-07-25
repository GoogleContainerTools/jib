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

import com.google.cloud.tools.jib.blob.BlobDescriptor;
import com.google.cloud.tools.jib.builder.BuildLogger;
import com.google.cloud.tools.jib.cache.CachedLayer;
import com.google.cloud.tools.jib.cache.CachedLayerWithMetadata;
import com.google.cloud.tools.jib.configuration.BuildConfiguration;
import com.google.cloud.tools.jib.image.DescriptorDigest;
import com.google.cloud.tools.jib.image.Image;
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
  @Mock private BuildLogger mockBuildLogger;
  @Mock private PullAndCacheBaseImageLayersStep mockPullAndCacheBaseImageLayersStep;
  @Mock private PullAndCacheBaseImageLayerStep mockPullAndCacheBaseImageLayerStep;
  @Mock private BuildAndCacheApplicationLayerStep mockBuildAndCacheApplicationLayerStep;

  private DescriptorDigest testDescriptorDigest;

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
    Mockito.when(mockBuildConfiguration.getCreationTime()).thenReturn(Instant.EPOCH);
    Mockito.when(mockBuildConfiguration.getEnvironment()).thenReturn(ImmutableMap.of());
    Mockito.when(mockBuildConfiguration.getJavaArguments()).thenReturn(ImmutableList.of());
    Mockito.when(mockBuildConfiguration.getExposedPorts()).thenReturn(ImmutableList.of());
    Mockito.when(mockBuildConfiguration.getEntrypoint()).thenReturn(ImmutableList.of());

    Mockito.when(mockPullAndCacheBaseImageLayersStep.getFuture())
        .thenReturn(
            Futures.immediateFuture(
                ImmutableList.of(
                    mockPullAndCacheBaseImageLayerStep,
                    mockPullAndCacheBaseImageLayerStep,
                    mockPullAndCacheBaseImageLayerStep)));
    Mockito.when(mockPullAndCacheBaseImageLayerStep.getFuture())
        .thenReturn(Futures.immediateFuture(testCachedLayer));
    Mockito.when(mockBuildAndCacheApplicationLayerStep.getFuture())
        .thenReturn(Futures.immediateFuture(testCachedLayer));
  }

  @Test
  public void test_validateAsyncDependencies() throws ExecutionException, InterruptedException {
    BuildImageStep buildImageStep =
        new BuildImageStep(
            MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor()),
            mockBuildConfiguration,
            mockPullAndCacheBaseImageLayersStep,
            ImmutableList.of(
                mockBuildAndCacheApplicationLayerStep,
                mockBuildAndCacheApplicationLayerStep,
                mockBuildAndCacheApplicationLayerStep));
    Image<CachedLayer> image = buildImageStep.getFuture().get().getFuture().get();
    Assert.assertEquals(
        testDescriptorDigest, image.getLayers().asList().get(0).getBlobDescriptor().getDigest());
  }
}
