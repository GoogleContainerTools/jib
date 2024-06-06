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

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

import com.google.cloud.tools.jib.api.DescriptorDigest;
import com.google.cloud.tools.jib.builder.ProgressEventDispatcher;
import com.google.cloud.tools.jib.builder.steps.PullBaseImageStep.ImagesAndRegistryClient;
import com.google.cloud.tools.jib.configuration.BuildContext;
import com.google.cloud.tools.jib.global.JibSystemProperties;
import com.google.cloud.tools.jib.image.DigestOnlyLayer;
import com.google.cloud.tools.jib.image.Image;
import com.google.cloud.tools.jib.image.json.ManifestTemplate;
import com.google.cloud.tools.jib.registry.ManifestAndDigest;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ForwardingExecutorService;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.security.DigestException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
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

  // ListeningExecutorService is annotated with @DoNotMock, so define a concrete class.
  private class MockListeningExecutorService extends ForwardingExecutorService
      implements ListeningExecutorService {

    @Override
    public <T> ListenableFuture<T> submit(Callable<T> task) {
      try {
        return Futures.immediateFuture(executorService.submit(task).get());
      } catch (InterruptedException | ExecutionException ex) {
        throw new IllegalStateException(ex);
      }
    }

    @Override
    public ListenableFuture<?> submit(Runnable task) {
      throw new UnsupportedOperationException();
    }

    @Override
    public <T> ListenableFuture<T> submit(Runnable task, T result) {
      throw new UnsupportedOperationException();
    }

    @Override
    protected ExecutorService delegate() {
      throw new UnsupportedOperationException();
    }
  }

  @Mock private BuildContext buildContext;
  @Mock private ProgressEventDispatcher.Factory progressDispatcherFactory;
  @Mock private ProgressEventDispatcher progressDispatcher;
  @Mock private ExecutorService executorService;
  @Mock private Image builtArm64AndLinuxImage;
  @Mock private Image builtAmd64AndWindowsImage;
  @Mock private Image baseImage1;
  @Mock private Image baseImage2;

  private StepsRunner stepsRunner;

  @Before
  public void setup() {
    stepsRunner = new StepsRunner(new MockListeningExecutorService(), buildContext);

    when(progressDispatcherFactory.create(Mockito.anyString(), Mockito.anyLong()))
        .thenReturn(progressDispatcher);
  }

  @Test
  public void testObtainBaseImageLayers_skipObtainingDuplicateLayers()
      throws DigestException, InterruptedException, ExecutionException {
    when(executorService.submit(Mockito.any(PullBaseImageStep.class)))
        .thenReturn(Futures.immediateFuture(new ImagesAndRegistryClient(null, null)));
    // Pretend that a thread pulling base images returned some (meaningless) result.
    stepsRunner.pullBaseImages(progressDispatcherFactory);

    DescriptorDigest digest1 =
        DescriptorDigest.fromHash(
            "1111111111111111111111111111111111111111111111111111111111111111");
    DescriptorDigest digest2 =
        DescriptorDigest.fromHash(
            "2222222222222222222222222222222222222222222222222222222222222222");
    DescriptorDigest digest3 =
        DescriptorDigest.fromHash(
            "3333333333333333333333333333333333333333333333333333333333333333");
    DigestOnlyLayer layer1 = new DigestOnlyLayer(digest1);
    DigestOnlyLayer layer2 = new DigestOnlyLayer(digest2);
    DigestOnlyLayer layer3 = new DigestOnlyLayer(digest3);

    PreparedLayer preparedLayer1 = Mockito.mock(PreparedLayer.class);
    PreparedLayer preparedLayer2 = Mockito.mock(PreparedLayer.class);
    PreparedLayer preparedLayer3 = Mockito.mock(PreparedLayer.class);
    when(executorService.submit(Mockito.any(ObtainBaseImageLayerStep.class)))
        .thenReturn(Futures.immediateFuture(preparedLayer1))
        .thenReturn(Futures.immediateFuture(preparedLayer2))
        .thenReturn(Futures.immediateFuture(preparedLayer3));

    Map<DescriptorDigest, Future<PreparedLayer>> preparedLayersCache = new HashMap<>();

    // 1. Should schedule two threads to obtain new layers.
    Image image = Mockito.mock(Image.class);
    when(image.getLayers()).thenReturn(ImmutableList.of(layer1, layer2));

    stepsRunner.obtainBaseImageLayers(image, true, preparedLayersCache, progressDispatcherFactory);
    Assert.assertEquals(2, preparedLayersCache.size()); // two new layers cached
    Assert.assertEquals(preparedLayer1, preparedLayersCache.get(digest1).get());
    Assert.assertEquals(preparedLayer2, preparedLayersCache.get(digest2).get());

    // 2. Should not schedule threads for existing layers.
    stepsRunner.obtainBaseImageLayers(image, true, preparedLayersCache, progressDispatcherFactory);
    Assert.assertEquals(2, preparedLayersCache.size()); // no new layers cached (still 2)
    Assert.assertEquals(preparedLayer1, preparedLayersCache.get(digest1).get());
    Assert.assertEquals(preparedLayer2, preparedLayersCache.get(digest2).get());

    // 3. Another image with one duplicate layer.
    when(image.getLayers()).thenReturn(ImmutableList.of(layer3, layer2));
    stepsRunner.obtainBaseImageLayers(image, true, preparedLayersCache, progressDispatcherFactory);
    Assert.assertEquals(3, preparedLayersCache.size()); // one new layer cached
    Assert.assertEquals(preparedLayer1, preparedLayersCache.get(digest1).get());
    Assert.assertEquals(preparedLayer2, preparedLayersCache.get(digest2).get());
    Assert.assertEquals(preparedLayer3, preparedLayersCache.get(digest3).get());

    // Total three threads scheduled for the three unique layers.
    Mockito.verify(executorService, Mockito.times(3))
        .submit(Mockito.any(ObtainBaseImageLayerStep.class));
  }

  @Test
  public void testIsImagePushed_skipExistingEnabledAndManifestPresent() {
    Optional<ManifestAndDigest<ManifestTemplate>> manifestResult = Mockito.mock(Optional.class);
    when(manifestResult.isPresent()).thenReturn(true);
    System.setProperty(JibSystemProperties.SKIP_EXISTING_IMAGES, "true");

    Assert.assertFalse(stepsRunner.isImagePushed(manifestResult));
  }

  @Test
  public void testIsImagePushed_skipExistingImageDisabledAndManifestPresent() {
    Optional<ManifestAndDigest<ManifestTemplate>> manifestResult = Mockito.mock(Optional.class);
    System.setProperty(JibSystemProperties.SKIP_EXISTING_IMAGES, "false");

    Assert.assertTrue(stepsRunner.isImagePushed(manifestResult));
  }

  @Test
  public void testIsImagePushed_skipExistingImageEnabledAndManifestNotPresent() {
    Optional<ManifestAndDigest<ManifestTemplate>> manifestResult = Mockito.mock(Optional.class);
    System.setProperty(JibSystemProperties.SKIP_EXISTING_IMAGES, "true");
    when(manifestResult.isPresent()).thenReturn(false);

    Assert.assertTrue(stepsRunner.isImagePushed(manifestResult));
  }

  @Test
  public void testFetchBuildImageForLocalBuild_matchingOsAndArch()
      throws ExecutionException, InterruptedException {
    when(builtArm64AndLinuxImage.getArchitecture()).thenReturn("arm64");
    when(builtAmd64AndWindowsImage.getArchitecture()).thenReturn("amd64");
    when(builtAmd64AndWindowsImage.getOs()).thenReturn("windows");
    when(executorService.submit(Mockito.any(Callable.class)))
        .thenReturn(
            Futures.immediateFuture(
                ImmutableMap.of(
                    baseImage1,
                    Futures.immediateFuture(builtArm64AndLinuxImage),
                    baseImage2,
                    Futures.immediateFuture(builtAmd64AndWindowsImage))));
    stepsRunner.buildImages(progressDispatcherFactory);

    Image expectedImage = stepsRunner.fetchBuiltImageForLocalBuild("windows", "amd64");

    assertThat(expectedImage.getOs()).isEqualTo("windows");
    assertThat(expectedImage.getArchitecture()).isEqualTo("amd64");
  }

  @Test
  public void testFetchBuildImageForLocalBuild_differentOs_buildImageForFirstPlatform()
      throws ExecutionException, InterruptedException {
    when(builtArm64AndLinuxImage.getArchitecture()).thenReturn("arm64");
    when(builtArm64AndLinuxImage.getOs()).thenReturn("linux");
    when(builtAmd64AndWindowsImage.getArchitecture()).thenReturn("amd64");
    when(executorService.submit(Mockito.any(Callable.class)))
        .thenReturn(
            Futures.immediateFuture(
                ImmutableMap.of(
                    baseImage1,
                    Futures.immediateFuture(builtArm64AndLinuxImage),
                    baseImage2,
                    Futures.immediateFuture(builtAmd64AndWindowsImage))));
    stepsRunner.buildImages(progressDispatcherFactory);

    Image expectedImage = stepsRunner.fetchBuiltImageForLocalBuild("os", "arm64");

    assertThat(expectedImage.getOs()).isEqualTo("linux");
    assertThat(expectedImage.getArchitecture()).isEqualTo("arm64");
  }

  @Test
  public void testFetchBuildImageForLocalBuild_differentArch_buildImageForFirstPlatform()
      throws ExecutionException, InterruptedException {
    when(builtArm64AndLinuxImage.getArchitecture()).thenReturn("arm64");
    when(builtAmd64AndWindowsImage.getArchitecture()).thenReturn("amd64");
    when(executorService.submit(Mockito.any(Callable.class)))
        .thenReturn(
            Futures.immediateFuture(
                ImmutableMap.of(
                    baseImage1,
                    Futures.immediateFuture(builtArm64AndLinuxImage),
                    baseImage2,
                    Futures.immediateFuture(builtAmd64AndWindowsImage))));
    stepsRunner.buildImages(progressDispatcherFactory);

    Image expectedImage = stepsRunner.fetchBuiltImageForLocalBuild("linux", "arch");

    assertThat(expectedImage.getArchitecture()).isEqualTo("arm64");
  }

  @Test
  public void testFetchBuildImageForLocalBuild_singleImage_imagePlatformDifferentFromDockerEnv()
      throws ExecutionException, InterruptedException {
    when(builtArm64AndLinuxImage.getArchitecture()).thenReturn("arm64");
    when(builtArm64AndLinuxImage.getOs()).thenReturn("linux");

    when(builtAmd64AndWindowsImage.getOs()).thenReturn("linux");
    when(executorService.submit(Mockito.any(Callable.class)))
        .thenReturn(
            Futures.immediateFuture(
                ImmutableMap.of(baseImage1, Futures.immediateFuture(builtArm64AndLinuxImage))));
    stepsRunner.buildImages(progressDispatcherFactory);

    Image expectedImage = stepsRunner.fetchBuiltImageForLocalBuild("linux", "amd64");

    assertThat(expectedImage.getOs()).isEqualTo("linux");
    assertThat(expectedImage.getArchitecture()).isEqualTo("arm64");
  }

  @Test
  public void testNormalizeArchitecture_aarch64() {
    assertThat(stepsRunner.normalizeArchitecture("aarch64")).isEqualTo("arm64");
  }

  @Test
  public void testNormalizeArchitecture_x86_64() {
    assertThat(stepsRunner.normalizeArchitecture("x86_64")).isEqualTo("amd64");
  }

  @Test
  public void testNormalizeArchitecture_arm() {
    assertThat(stepsRunner.normalizeArchitecture("arm")).isEqualTo("arm");
  }
}
