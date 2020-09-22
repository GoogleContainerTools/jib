/*
 * Copyright 2020 Google LLC.
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

import com.google.cloud.tools.jib.api.DescriptorDigest;
import com.google.cloud.tools.jib.builder.ProgressEventDispatcher;
import com.google.cloud.tools.jib.builder.steps.PullBaseImageStep.ImagesAndRegistryClient;
import com.google.cloud.tools.jib.configuration.BuildContext;
import com.google.cloud.tools.jib.image.DigestOnlyLayer;
import com.google.cloud.tools.jib.image.Image;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.security.DigestException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link StepsRunner}. */
@RunWith(MockitoJUnitRunner.class)
public class StepsRunnerTest {

  @Mock private BuildContext buildContext;
  @Mock private ProgressEventDispatcher.Factory progressDispatcherFactory;
  @Mock private ProgressEventDispatcher progressDispatcher;
  @Mock private ListeningExecutorService executorService;

  private StepsRunner stepsRunner;

  @Before
  public void setup() {
    stepsRunner = new StepsRunner(executorService, buildContext);
    Mockito.when(progressDispatcherFactory.create(Mockito.anyString(), Mockito.anyLong()))
        .thenReturn(progressDispatcher);
  }

  @Test
  @SuppressWarnings("unchecked") // by Mockito.mock() on generic types
  public void testObtainBaseImageLayers_skipObtainingDuplicateLayers()
      throws DigestException, InterruptedException, ExecutionException {
    // Setup: pretend you have retrieved a registry client.
    ListenableFuture<ImagesAndRegistryClient> future = Mockito.mock(ListenableFuture.class);
    Mockito.when(future.get()).thenReturn(new ImagesAndRegistryClient(null, null));
    Mockito.when(executorService.submit(Mockito.any(PullBaseImageStep.class))).thenReturn(future);
    stepsRunner.pullBaseImages(progressDispatcherFactory);

    DigestOnlyLayer layer1 =
        new DigestOnlyLayer(
            DescriptorDigest.fromHash(
                "1111111111111111111111111111111111111111111111111111111111111111"));
    DigestOnlyLayer layer2 =
        new DigestOnlyLayer(
            DescriptorDigest.fromHash(
                "2222222222222222222222222222222222222222222222222222222222222222"));
    DigestOnlyLayer layer3 =
        new DigestOnlyLayer(
            DescriptorDigest.fromHash(
                "3333333333333333333333333333333333333333333333333333333333333333"));

    Mockito.when(executorService.submit(Mockito.any(ObtainBaseImageLayerStep.class)))
        .thenReturn(Mockito.mock(ListenableFuture.class));

    Map<DescriptorDigest, Future<PreparedLayer>> preparedLayersCache = new HashMap<>();

    // 1. Should schedule two threads to obtain new layers.
    Image image = Mockito.mock(Image.class);
    Mockito.when(image.getLayers()).thenReturn(ImmutableList.of(layer1, layer2));

    stepsRunner.obtainBaseImageLayers(image, true, preparedLayersCache, progressDispatcherFactory);
    Assert.assertEquals(2, preparedLayersCache.size()); // two new layers cached

    // 2. Should not schedule threads for existing layers.
    stepsRunner.obtainBaseImageLayers(image, true, preparedLayersCache, progressDispatcherFactory);
    Assert.assertEquals(2, preparedLayersCache.size()); // no new layers cached (still 2)

    // 3. Another image with one duplicate layer.
    Mockito.when(image.getLayers()).thenReturn(ImmutableList.of(layer3, layer2));
    stepsRunner.obtainBaseImageLayers(image, true, preparedLayersCache, progressDispatcherFactory);
    Assert.assertEquals(3, preparedLayersCache.size()); // one new layer cached

    // Total three threads scheduled for the three unique layers.
    Mockito.verify(executorService, Mockito.times(3))
        .submit(Mockito.any(ObtainBaseImageLayerStep.class));
  }
}
