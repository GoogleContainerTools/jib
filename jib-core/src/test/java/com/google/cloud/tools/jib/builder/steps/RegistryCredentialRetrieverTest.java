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

import com.google.cloud.tools.jib.api.CacheDirectoryCreationException;
import com.google.cloud.tools.jib.api.Credential;
import com.google.cloud.tools.jib.api.CredentialRetriever;
import com.google.cloud.tools.jib.api.ImageReference;
import com.google.cloud.tools.jib.api.LogEvent;
import com.google.cloud.tools.jib.configuration.BuildContext;
import com.google.cloud.tools.jib.configuration.ContainerConfiguration;
import com.google.cloud.tools.jib.configuration.ImageConfiguration;
import com.google.cloud.tools.jib.event.EventHandlers;
import com.google.cloud.tools.jib.registry.credentials.CredentialRetrievalException;
import com.google.common.util.concurrent.MoreExecutors;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.Assert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** Tests for {@link RegistryCredentialRetriever}. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RegistryCredentialRetrieverTest {

  @Mock private EventHandlers mockEventHandlers;

  @Test
  void testCall_retrieved() throws CredentialRetrievalException, CacheDirectoryCreationException {
    BuildContext buildContext =
        makeFakeBuildContext(
            Arrays.asList(
                Optional::empty,
                () -> Optional.of(Credential.from("baseusername", "basepassword"))),
            Arrays.asList(
                () -> Optional.of(Credential.from("targetusername", "targetpassword")),
                () -> Optional.of(Credential.from("ignored", "ignored"))));

    Assert.assertEquals(
        Optional.of(Credential.from("baseusername", "basepassword")),
        RegistryCredentialRetriever.getBaseImageCredential(buildContext));
    Assert.assertEquals(
        Optional.of(Credential.from("targetusername", "targetpassword")),
        RegistryCredentialRetriever.getTargetImageCredential(buildContext));
  }

  @Test
  void testCall_none() throws CredentialRetrievalException, CacheDirectoryCreationException {
    BuildContext buildContext =
        makeFakeBuildContext(
            Arrays.asList(Optional::empty, Optional::empty), Collections.emptyList());
    Assert.assertFalse(
        RegistryCredentialRetriever.getBaseImageCredential(buildContext).isPresent());

    Mockito.verify(mockEventHandlers)
        .dispatch(LogEvent.info("No credentials could be retrieved for baseregistry/baserepo"));

    Assert.assertFalse(
        RegistryCredentialRetriever.getTargetImageCredential(buildContext).isPresent());

    Mockito.verify(mockEventHandlers)
        .dispatch(LogEvent.info("No credentials could be retrieved for targetregistry/targetrepo"));
  }

  @Test
  void testCall_exception() throws CacheDirectoryCreationException {
    CredentialRetrievalException credentialRetrievalException =
        Mockito.mock(CredentialRetrievalException.class);
    BuildContext buildContext =
        makeFakeBuildContext(
            Collections.singletonList(
                () -> {
                  throw credentialRetrievalException;
                }),
            Collections.emptyList());
    try {
      RegistryCredentialRetriever.getBaseImageCredential(buildContext);
      Assert.fail("Should have thrown exception");

    } catch (CredentialRetrievalException ex) {
      Assert.assertSame(credentialRetrievalException, ex);
    }
  }

  private BuildContext makeFakeBuildContext(
      List<CredentialRetriever> baseCredentialRetrievers,
      List<CredentialRetriever> targetCredentialRetrievers)
      throws CacheDirectoryCreationException {
    ImageReference baseImage = ImageReference.of("baseregistry", "baserepo", null);
    ImageReference targetImage = ImageReference.of("targetregistry", "targetrepo", null);
    return BuildContext.builder()
        .setEventHandlers(mockEventHandlers)
        .setBaseImageConfiguration(
            ImageConfiguration.builder(baseImage)
                .setCredentialRetrievers(baseCredentialRetrievers)
                .build())
        .setTargetImageConfiguration(
            ImageConfiguration.builder(targetImage)
                .setCredentialRetrievers(targetCredentialRetrievers)
                .build())
        .setContainerConfiguration(ContainerConfiguration.builder().build())
        .setBaseImageLayersCacheDirectory(Paths.get("ignored"))
        .setApplicationLayersCacheDirectory(Paths.get("ignored"))
        .setExecutorService(MoreExecutors.newDirectExecutorService())
        .build();
  }
}
