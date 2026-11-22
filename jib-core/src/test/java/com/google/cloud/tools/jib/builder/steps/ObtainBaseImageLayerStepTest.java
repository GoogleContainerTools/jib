/*
 * Copyright 2019 Google LLC.
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
import com.google.cloud.tools.jib.api.RegistryException;
import com.google.cloud.tools.jib.blob.Blob;
import com.google.cloud.tools.jib.blob.BlobDescriptor;
import com.google.cloud.tools.jib.blob.Blobs;
import com.google.cloud.tools.jib.builder.ProgressEventDispatcher;
import com.google.cloud.tools.jib.builder.steps.PreparedLayer.StateInTarget;
import com.google.cloud.tools.jib.cache.Cache;
import com.google.cloud.tools.jib.cache.CacheCorruptedException;
import com.google.cloud.tools.jib.configuration.BuildContext;
import com.google.cloud.tools.jib.image.Layer;
import com.google.cloud.tools.jib.image.ReferenceLayer;
import com.google.cloud.tools.jib.registry.RegistryClient;
import com.google.common.io.ByteStreams;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.DigestException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.zip.GZIPOutputStream;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.AdditionalAnswers;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer3;

/** Tests for {@link ObtainBaseImageLayerStep}. */
@RunWith(MockitoJUnitRunner.class)
public class ObtainBaseImageLayerStepTest {

  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  private DescriptorDigest existingLayerDigest;
  private DescriptorDigest freshLayerDigest;

  @Mock private Layer existingLayer;
  @Mock private Layer freshLayer;
  @Mock private RegistryClient registryClient;

  @Mock(answer = Answers.RETURNS_MOCKS)
  private BuildContext buildContext;

  @Mock(answer = Answers.RETURNS_MOCKS)
  private ProgressEventDispatcher.Factory progressDispatcherFactory;

  @Before
  public void setUp() throws IOException, RegistryException, DigestException {
    existingLayerDigest =
        DescriptorDigest.fromHash(
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
    freshLayerDigest =
        DescriptorDigest.fromHash(
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");

    DescriptorDigest diffId = Mockito.mock(DescriptorDigest.class);
    existingLayer = new ReferenceLayer(new BlobDescriptor(existingLayerDigest), diffId);
    freshLayer = new ReferenceLayer(new BlobDescriptor(freshLayerDigest), diffId);

    Mockito.when(registryClient.checkBlob(existingLayerDigest))
        .thenReturn(Optional.of(Mockito.mock(BlobDescriptor.class)));
    Mockito.when(registryClient.checkBlob(freshLayerDigest)).thenReturn(Optional.empty());

    // necessary to prevent error from classes dealing with progress report
    Answer3<Blob, DescriptorDigest, Consumer<Long>, Consumer<Long>> progressSizeSetter =
        (ignored1, progressSizeConsumer, ignored2) -> {
          progressSizeConsumer.accept(Long.valueOf(12345));
          return null;
        };
    Mockito.when(registryClient.pullBlob(Mockito.any(), Mockito.any(), Mockito.any()))
        .thenAnswer(AdditionalAnswers.answer(progressSizeSetter));
  }

  @Test
  public void testForSelectiveDownload_existingLayer()
      throws IOException, CacheCorruptedException, RegistryException {
    ObtainBaseImageLayerStep puller =
        ObtainBaseImageLayerStep.forSelectiveDownload(
            buildContext, progressDispatcherFactory, existingLayer, registryClient, registryClient);

    PreparedLayer preparedLayer = puller.call();

    Assert.assertEquals(StateInTarget.EXISTING, preparedLayer.getStateInTarget());
    // Should have queried the blob.
    Mockito.verify(registryClient).checkBlob(existingLayerDigest);
    // The layer should not be pulled.
    Mockito.verify(registryClient, Mockito.never())
        .pullBlob(Mockito.eq(existingLayerDigest), Mockito.any(), Mockito.any());
    Mockito.verifyNoMoreInteractions(registryClient);
  }

  @Test
  public void testForSelectiveDownload_freshLayer()
      throws IOException, CacheCorruptedException, RegistryException {
    ObtainBaseImageLayerStep puller =
        ObtainBaseImageLayerStep.forSelectiveDownload(
            buildContext, progressDispatcherFactory, freshLayer, registryClient, registryClient);

    PreparedLayer preparedLayer = puller.call();

    Assert.assertEquals(StateInTarget.MISSING, preparedLayer.getStateInTarget());
    // Should have queried the blob.
    Mockito.verify(registryClient).checkBlob(freshLayerDigest);
    // The layer should not be pulled.
    Mockito.verify(registryClient)
        .pullBlob(Mockito.eq(freshLayerDigest), Mockito.any(), Mockito.any());
    Mockito.verifyNoMoreInteractions(registryClient);
  }

  @Test
  public void testForForcedDownload_existingLayer()
      throws IOException, CacheCorruptedException, RegistryException {
    ObtainBaseImageLayerStep puller =
        ObtainBaseImageLayerStep.forForcedDownload(
            buildContext, progressDispatcherFactory, existingLayer, registryClient);
    PreparedLayer preparedLayer = puller.call();

    // existence unknown
    Assert.assertEquals(StateInTarget.UNKNOWN, preparedLayer.getStateInTarget());
    // No blob checking should happen.
    Mockito.verify(registryClient, Mockito.never()).checkBlob(Mockito.any());
    // The layer should be pulled.
    Mockito.verify(registryClient)
        .pullBlob(Mockito.eq(existingLayerDigest), Mockito.any(), Mockito.any());
    Mockito.verifyNoMoreInteractions(registryClient);
  }

  @Test
  public void testForForcedDownload_freshLayer()
      throws IOException, CacheCorruptedException, RegistryException {
    ObtainBaseImageLayerStep puller =
        ObtainBaseImageLayerStep.forForcedDownload(
            buildContext, progressDispatcherFactory, freshLayer, registryClient);
    PreparedLayer preparedLayer = puller.call();

    // existence unknown
    Assert.assertEquals(StateInTarget.UNKNOWN, preparedLayer.getStateInTarget());
    // No blob checking should happen.
    Mockito.verify(registryClient, Mockito.never()).checkBlob(Mockito.any());
    // The layer should be pulled.
    Mockito.verify(registryClient)
        .pullBlob(Mockito.eq(freshLayerDigest), Mockito.any(), Mockito.any());
    Mockito.verifyNoMoreInteractions(registryClient);
  }

  @Test
  public void testLayerMissingInCacheInOfflineMode()
      throws CacheCorruptedException, RegistryException {
    Mockito.when(buildContext.isOffline()).thenReturn(true);

    ObtainBaseImageLayerStep puller =
        ObtainBaseImageLayerStep.forForcedDownload(
            buildContext, progressDispatcherFactory, freshLayer, registryClient);
    try {
      puller.call();
      Assert.fail();
    } catch (IOException ex) {
      Assert.assertEquals(
          "Cannot run Jib in offline mode; local Jib cache for base image is missing image layer "
              + "sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb. Rerun "
              + "Jib in online mode with \"-Djib.alwaysCacheBaseImage=true\" to re-download the "
              + "base image layers.",
          ex.getMessage());
    }
  }

  /**
   * Simulates parallel builds (e.g., {@code mvn -T 1C}) whose modules share a base image and a
   * cache: both builds start pulling the same base image layer at the same time.
   */
  @Test
  public void testParallelBuilds_sameLayerIsPulledOnlyOnce() throws Exception {
    ByteArrayOutputStream compressed = new ByteArrayOutputStream();
    try (GZIPOutputStream gzip = new GZIPOutputStream(compressed)) {
      gzip.write("layer contents".getBytes(StandardCharsets.UTF_8));
    }
    byte[] layerBytes = compressed.toByteArray();
    DescriptorDigest layerDigest =
        Blobs.from(new ByteArrayInputStream(layerBytes))
            .writeTo(ByteStreams.nullOutputStream())
            .getDigest();
    Layer layer = new ReferenceLayer(new BlobDescriptor(layerDigest), layerDigest);

    Cache cache = Cache.withDirectory(temporaryFolder.newFolder().toPath());
    Mockito.when(buildContext.getBaseImageLayersCache()).thenReturn(cache);

    // Each pull waits (briefly) for the other build to also start pulling the same layer.
    AtomicInteger pullCount = new AtomicInteger();
    CountDownLatch bothPulling = new CountDownLatch(2);
    Mockito.doAnswer(
            invocation -> {
              Consumer<Long> progressSizeConsumer = invocation.getArgument(1);
              progressSizeConsumer.accept((long) layerBytes.length);
              pullCount.incrementAndGet();
              bothPulling.countDown();
              bothPulling.await(2, TimeUnit.SECONDS);
              return Blobs.from(new ByteArrayInputStream(layerBytes));
            })
        .when(registryClient)
        .pullBlob(Mockito.eq(layerDigest), Mockito.any(), Mockito.any());

    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      List<Future<PreparedLayer>> results = new ArrayList<>();
      for (int i = 0; i < 2; i++) {
        Callable<PreparedLayer> build =
            ObtainBaseImageLayerStep.forForcedDownload(
                buildContext, progressDispatcherFactory, layer, registryClient);
        results.add(executor.submit(build));
      }
      for (Future<PreparedLayer> result : results) {
        try {
          Assert.assertNotNull(result.get(30, TimeUnit.SECONDS));
        } catch (ExecutionException ex) {
          throw new AssertionError(ex.getCause());
        }
      }
    } finally {
      executor.shutdownNow();
    }

    Assert.assertEquals(
        "the same base image layer should be downloaded only once", 1, pullCount.get());
  }
}
