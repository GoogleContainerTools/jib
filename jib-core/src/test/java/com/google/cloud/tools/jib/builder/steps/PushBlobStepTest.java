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

import com.google.cloud.tools.jib.api.ImageReference;
import com.google.cloud.tools.jib.api.RegistryException;
import com.google.cloud.tools.jib.blob.BlobDescriptor;
import com.google.cloud.tools.jib.builder.ProgressEventDispatcher;
import com.google.cloud.tools.jib.configuration.BuildContext;
import com.google.cloud.tools.jib.configuration.ImageConfiguration;
import com.google.cloud.tools.jib.registry.RegistryClient;
import java.io.IOException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** Tests for {@link PushBlobStep}. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PushBlobStepTest {

  @Mock private BlobDescriptor blobDescriptor;
  @Mock private RegistryClient registryClient;

  @Mock(answer = Answers.RETURNS_MOCKS)
  private ProgressEventDispatcher.Factory progressDispatcherFactory;

  @Mock(answer = Answers.RETURNS_MOCKS)
  private BuildContext buildContext;

  @BeforeEach
  void setUp() {
    Mockito.when(buildContext.getTargetImageConfiguration())
        .thenReturn(ImageConfiguration.builder(ImageReference.scratch()).build());
  }

  @Test
  void testCall_doBlobCheckAndBlobExists() throws IOException, RegistryException {
    Mockito.when(registryClient.checkBlob(Mockito.any())).thenReturn(Optional.of(blobDescriptor));

    call(false);

    Mockito.verify(registryClient).checkBlob(Mockito.any());
    Mockito.verify(registryClient, Mockito.never())
        .pushBlob(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
  }

  @Test
  void testCall_doBlobCheckAndBlobDoesNotExist() throws IOException, RegistryException {
    Mockito.when(registryClient.checkBlob(Mockito.any())).thenReturn(Optional.empty());

    call(false);

    Mockito.verify(registryClient).checkBlob(Mockito.any());
    Mockito.verify(registryClient)
        .pushBlob(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
  }

  @Test
  void testCall_forcePushWithNoBlobCheck() throws IOException, RegistryException {
    call(true);

    Mockito.verify(registryClient, Mockito.never()).checkBlob(Mockito.any());
    Mockito.verify(registryClient)
        .pushBlob(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
  }

  private void call(boolean forcePush) throws IOException, RegistryException {
    new PushBlobStep(
            buildContext,
            progressDispatcherFactory,
            registryClient,
            blobDescriptor,
            null,
            forcePush)
        .call();
  }
}
