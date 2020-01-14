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
import com.google.cloud.tools.jib.builder.ProgressEventDispatcher;
import com.google.cloud.tools.jib.builder.steps.PreparedLayer.StateInTarget;
import com.google.cloud.tools.jib.cache.CacheCorruptedException;
import com.google.cloud.tools.jib.configuration.BuildContext;
import com.google.cloud.tools.jib.image.Image;
import com.google.cloud.tools.jib.image.Layer;
import com.google.cloud.tools.jib.image.ReferenceLayer;
import com.google.cloud.tools.jib.registry.RegistryClient;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.security.DigestException;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
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

  private DescriptorDigest existingLayerDigest;
  private DescriptorDigest freshLayerDigest;

  @Mock private Image image;
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
    Layer existingLayer = new ReferenceLayer(new BlobDescriptor(existingLayerDigest), diffId);
    Layer freshLayer = new ReferenceLayer(new BlobDescriptor(freshLayerDigest), diffId);
    Mockito.when(image.getLayers()).thenReturn(ImmutableList.of(existingLayer, freshLayer));

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
  public void testMakeListForSelectiveDownload()
      throws IOException, CacheCorruptedException, RegistryException {
    ImmutableList<ObtainBaseImageLayerStep> pullers =
        ObtainBaseImageLayerStep.makeListForSelectiveDownload(
            buildContext, progressDispatcherFactory, image, registryClient, registryClient);

    Assert.assertEquals(2, pullers.size());
    PreparedLayer preparedExistingLayer = pullers.get(0).call();
    PreparedLayer preparedFreshLayer = pullers.get(1).call();

    Assert.assertEquals(StateInTarget.EXISTING, preparedExistingLayer.getStateInTarget());
    Assert.assertEquals(StateInTarget.MISSING, preparedFreshLayer.getStateInTarget());

    // Should have queried all blobs.
    Mockito.verify(registryClient).checkBlob(existingLayerDigest);
    Mockito.verify(registryClient).checkBlob(freshLayerDigest);

    // Only the missing layer should be pulled.
    Mockito.verify(registryClient, Mockito.never())
        .pullBlob(Mockito.eq(existingLayerDigest), Mockito.any(), Mockito.any());
    Mockito.verify(registryClient)
        .pullBlob(Mockito.eq(freshLayerDigest), Mockito.any(), Mockito.any());
  }

  @Test
  public void testMakeListForForcedDownload()
      throws IOException, CacheCorruptedException, RegistryException {
    ImmutableList<ObtainBaseImageLayerStep> pullers =
        ObtainBaseImageLayerStep.makeListForForcedDownload(
            buildContext, progressDispatcherFactory, image, registryClient);

    Assert.assertEquals(2, pullers.size());
    PreparedLayer preparedExistingLayer = pullers.get(0).call();
    PreparedLayer preparedFreshLayer = pullers.get(1).call();

    // existence unknown
    Assert.assertEquals(StateInTarget.UNKNOWN, preparedExistingLayer.getStateInTarget());
    Assert.assertEquals(StateInTarget.UNKNOWN, preparedFreshLayer.getStateInTarget());

    // No blob checking should happen.
    Mockito.verify(registryClient, Mockito.never()).checkBlob(existingLayerDigest);
    Mockito.verify(registryClient, Mockito.never()).checkBlob(freshLayerDigest);

    // All layers should be pulled.
    Mockito.verify(registryClient)
        .pullBlob(Mockito.eq(existingLayerDigest), Mockito.any(), Mockito.any());
    Mockito.verify(registryClient)
        .pullBlob(Mockito.eq(freshLayerDigest), Mockito.any(), Mockito.any());
  }

  @Test
  public void testLayerMissingInCacheInOfflineMode()
      throws CacheCorruptedException, RegistryException {
    Mockito.when(buildContext.isOffline()).thenReturn(true);

    ImmutableList<ObtainBaseImageLayerStep> pullers =
        ObtainBaseImageLayerStep.makeListForForcedDownload(
            buildContext, progressDispatcherFactory, image, registryClient);
    try {
      pullers.get(1).call();
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
}
