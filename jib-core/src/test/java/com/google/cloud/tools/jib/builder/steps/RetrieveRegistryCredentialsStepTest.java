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
import com.google.cloud.tools.jib.configuration.BuildConfiguration;
import com.google.cloud.tools.jib.configuration.ImageConfiguration;
import com.google.cloud.tools.jib.configuration.credentials.Credential;
import com.google.cloud.tools.jib.configuration.credentials.CredentialRetriever;
import com.google.cloud.tools.jib.image.ImageReference;
import com.google.cloud.tools.jib.registry.credentials.CredentialRetrievalException;
import com.google.common.util.concurrent.ListeningExecutorService;
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

  @Mock private ListeningExecutorService mockListeningExecutorService;
  @Mock private JibLogger mockJibLogger;

  @Test
  public void testCall_retrieved() throws CredentialRetrievalException {
    BuildConfiguration buildConfiguration =
        makeFakeBuildConfiguration(
            Arrays.asList(
                Optional::empty,
                () -> Optional.of(Credential.basic("baseusername", "basepassword"))),
            Arrays.asList(
                () -> Optional.of(Credential.basic("targetusername", "targetpassword")),
                () -> Optional.of(Credential.basic("ignored", "ignored"))));

    Assert.assertEquals(
        Credential.basic("baseusername", "basepassword"),
        RetrieveRegistryCredentialsStep.forBaseImage(
                mockListeningExecutorService, buildConfiguration)
            .call());
    Assert.assertEquals(
        Credential.basic("targetusername", "targetpassword"),
        RetrieveRegistryCredentialsStep.forTargetImage(
                mockListeningExecutorService, buildConfiguration)
            .call());
  }

  @Test
  public void testCall_none() throws CredentialRetrievalException {
    BuildConfiguration buildConfiguration =
        makeFakeBuildConfiguration(
            Arrays.asList(Optional::empty, Optional::empty), Collections.emptyList());
    Assert.assertNull(
        RetrieveRegistryCredentialsStep.forBaseImage(
                mockListeningExecutorService, buildConfiguration)
            .call());
    Mockito.verify(mockJibLogger)
        .info("No credentials could be retrieved for registry baseregistry");
    Assert.assertNull(
        RetrieveRegistryCredentialsStep.forTargetImage(
                mockListeningExecutorService, buildConfiguration)
            .call());
    Mockito.verify(mockJibLogger)
        .info("No credentials could be retrieved for registry targetregistry");
  }

  @Test
  public void testCall_exception() {
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
      RetrieveRegistryCredentialsStep.forBaseImage(mockListeningExecutorService, buildConfiguration)
          .call();
      Assert.fail("Should have thrown exception");

    } catch (CredentialRetrievalException ex) {
      Assert.assertSame(credentialRetrievalException, ex);
    }
  }

  private BuildConfiguration makeFakeBuildConfiguration(
      List<CredentialRetriever> baseCredentialRetrievers,
      List<CredentialRetriever> targetCredentialRetrievers) {
    ImageReference baseImage = ImageReference.of("baseregistry", "ignored", null);
    ImageReference targetImage = ImageReference.of("targetregistry", "ignored", null);
    return BuildConfiguration.builder(mockJibLogger)
        .setBaseImageConfiguration(
            ImageConfiguration.builder(baseImage)
                .setCredentialRetrievers(baseCredentialRetrievers)
                .build())
        .setTargetImageConfiguration(
            ImageConfiguration.builder(targetImage)
                .setCredentialRetrievers(targetCredentialRetrievers)
                .build())
        .build();
  }
}
