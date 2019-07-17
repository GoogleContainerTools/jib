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
import com.google.cloud.tools.jib.api.ImageReference;
import com.google.cloud.tools.jib.api.RegistryException;
import com.google.cloud.tools.jib.blob.BlobDescriptor;
import com.google.cloud.tools.jib.builder.ProgressEventDispatcher;
import com.google.cloud.tools.jib.configuration.BuildConfiguration;
import com.google.cloud.tools.jib.configuration.ImageConfiguration;
import com.google.cloud.tools.jib.event.EventHandlers;
import com.google.cloud.tools.jib.registry.RegistryClient;
import java.io.IOException;
import java.security.DigestException;
import java.util.Optional;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link PushBlobStep}. */
@RunWith(MockitoJUnitRunner.class)
public class PushBlobStepTest {

  private final ProgressEventDispatcher progressDispatcher =
      ProgressEventDispatcher.newRoot(EventHandlers.NONE, "", 1);
  private final ProgressEventDispatcher.Factory progressDispatcherFactory =
      (ignored1, ignored2) -> progressDispatcher;
  private BlobDescriptor blobDescriptor;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private BuildConfiguration buildConfiguration;

  @Mock private RegistryClient registryClient;

  @Before
  public void setUp() throws DigestException {
    String sha256Hash = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    blobDescriptor = new BlobDescriptor(154, DescriptorDigest.fromHash(sha256Hash));

    RegistryClient.Factory registryClientFactory =
        Mockito.mock(RegistryClient.Factory.class, Answers.RETURNS_SELF);
    Mockito.when(registryClientFactory.newRegistryClient()).thenReturn(registryClient);

    Mockito.when(buildConfiguration.newTargetImageRegistryClientFactory())
        .thenReturn(registryClientFactory);
    Mockito.when(buildConfiguration.getTargetImageConfiguration())
        .thenReturn(ImageConfiguration.builder(ImageReference.scratch()).build());
  }

  @After
  public void tearDown() {
    progressDispatcher.close();
  }

  @Test
  public void testCall_doBlobCheckAndBlobExists() throws IOException, RegistryException {
    Mockito.when(registryClient.checkBlob(Mockito.any())).thenReturn(Optional.of(blobDescriptor));

    call(true);

    Mockito.verify(registryClient).checkBlob(Mockito.any());
    Mockito.verify(registryClient, Mockito.never())
        .pushBlob(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
  }

  @Test
  public void testCall_doBlobCheckAndBlobDoesNotExist() throws IOException, RegistryException {
    Mockito.when(registryClient.checkBlob(Mockito.any())).thenReturn(Optional.empty());

    call(true);

    Mockito.verify(registryClient).checkBlob(Mockito.any());
    Mockito.verify(registryClient)
        .pushBlob(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
  }

  @Test
  public void testCall_noBlobCheck() throws IOException, RegistryException {
    call(false);

    Mockito.verify(registryClient, Mockito.never()).checkBlob(Mockito.any());
    Mockito.verify(registryClient)
        .pushBlob(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
  }

  private void call(boolean doBlobCheck) throws IOException, RegistryException {
    new PushBlobStep(
            buildConfiguration, progressDispatcherFactory, null, blobDescriptor, null, doBlobCheck)
        .call();
  }
}
