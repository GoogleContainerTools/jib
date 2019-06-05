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

import com.google.cloud.tools.jib.api.Credential;
import com.google.cloud.tools.jib.api.CredentialRetriever;
import com.google.cloud.tools.jib.api.ImageReference;
import com.google.cloud.tools.jib.api.LogEvent;
import com.google.cloud.tools.jib.builder.ProgressEventDispatcher;
import com.google.cloud.tools.jib.configuration.BuildConfiguration;
import com.google.cloud.tools.jib.configuration.ImageConfiguration;
import com.google.cloud.tools.jib.event.EventHandlers;
import com.google.cloud.tools.jib.event.events.ProgressEvent;
import com.google.cloud.tools.jib.registry.credentials.CredentialRetrievalException;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link RetrieveRegistryCredentialsStep}. */
@RunWith(MockitoJUnitRunner.class)
public class RetrieveRegistryCredentialsStepTest {

  @Mock private EventHandlers mockEventHandlers;
  @Mock private ListeningExecutorService mockListeningExecutorService;

  @Test
  public void testCall_retrieved() throws CredentialRetrievalException, IOException {
    BuildConfiguration buildConfiguration =
        makeFakeBuildConfiguration(
            Arrays.asList(
                Optional::empty,
                () -> Optional.of(Credential.from("baseusername", "basepassword"))),
            Arrays.asList(
                () -> Optional.of(Credential.from("targetusername", "targetpassword")),
                () -> Optional.of(Credential.from("ignored", "ignored"))));

    Assert.assertEquals(
        Credential.from("baseusername", "basepassword"),
        RetrieveRegistryCredentialsStep.forBaseImage(
                mockListeningExecutorService,
                buildConfiguration,
                ProgressEventDispatcher.newRoot(mockEventHandlers, "ignored", 1).newChildProducer())
            .call());
    Assert.assertEquals(
        Credential.from("targetusername", "targetpassword"),
        RetrieveRegistryCredentialsStep.forTargetImage(
                mockListeningExecutorService,
                buildConfiguration,
                ProgressEventDispatcher.newRoot(mockEventHandlers, "ignored", 1).newChildProducer())
            .call());
  }

  @Test
  public void testCall_none() throws CredentialRetrievalException, IOException {
    BuildConfiguration buildConfiguration =
        makeFakeBuildConfiguration(
            Arrays.asList(Optional::empty, Optional::empty), Collections.emptyList());
    Assert.assertNull(
        RetrieveRegistryCredentialsStep.forBaseImage(
                mockListeningExecutorService,
                buildConfiguration,
                ProgressEventDispatcher.newRoot(mockEventHandlers, "ignored", 1).newChildProducer())
            .call());

    Mockito.verify(mockEventHandlers, Mockito.atLeastOnce())
        .dispatch(Mockito.any(ProgressEvent.class));
    Mockito.verify(mockEventHandlers)
        .dispatch(LogEvent.info("No credentials could be retrieved for registry baseregistry"));

    Assert.assertNull(
        RetrieveRegistryCredentialsStep.forTargetImage(
                mockListeningExecutorService,
                buildConfiguration,
                ProgressEventDispatcher.newRoot(mockEventHandlers, "ignored", 1).newChildProducer())
            .call());

    Mockito.verify(mockEventHandlers)
        .dispatch(LogEvent.info("No credentials could be retrieved for registry baseregistry"));
  }

  @Test
  public void testCall_exception() throws IOException {
    CredentialRetrievalException credentialRetrievalException =
        Mockito.mock(CredentialRetrievalException.class);
    BuildConfiguration buildConfiguration =
        makeFakeBuildConfiguration(
            Collections.singletonList(
                () -> {
                  throw credentialRetrievalException;
                }),
            Collections.emptyList());
    try {
      RetrieveRegistryCredentialsStep.forBaseImage(
              mockListeningExecutorService,
              buildConfiguration,
              ProgressEventDispatcher.newRoot(mockEventHandlers, "ignored", 1).newChildProducer())
          .call();
      Assert.fail("Should have thrown exception");

    } catch (CredentialRetrievalException ex) {
      Assert.assertSame(credentialRetrievalException, ex);
    }
  }

  private BuildConfiguration makeFakeBuildConfiguration(
      List<CredentialRetriever> baseCredentialRetrievers,
      List<CredentialRetriever> targetCredentialRetrievers)
      throws IOException {
    ImageReference baseImage = ImageReference.of("baseregistry", "ignored", null);
    ImageReference targetImage = ImageReference.of("targetregistry", "ignored", null);
    return BuildConfiguration.builder()
        .setEventHandlers(mockEventHandlers)
        .setBaseImageConfiguration(
            ImageConfiguration.builder(baseImage)
                .setCredentialRetrievers(baseCredentialRetrievers)
                .build())
        .setTargetImageConfiguration(
            ImageConfiguration.builder(targetImage)
                .setCredentialRetrievers(targetCredentialRetrievers)
                .build())
        .setBaseImageLayersCacheDirectory(Paths.get("ignored"))
        .setApplicationLayersCacheDirectory(Paths.get("ignored"))
        .setExecutorService(MoreExecutors.newDirectExecutorService())
        .build();
  }
}
