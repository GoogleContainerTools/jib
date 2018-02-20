/*
 * Copyright 2018 Google Inc.
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

package com.google.cloud.tools.jib.builder;

import com.google.cloud.tools.jib.http.Authorization;
import com.google.cloud.tools.jib.registry.credentials.DockerConfigCredentialRetriever;
import com.google.cloud.tools.jib.registry.credentials.DockerCredentialRetriever;
import com.google.cloud.tools.jib.registry.credentials.DockerCredentialRetrieverFactory;
import com.google.cloud.tools.jib.registry.credentials.NonexistentDockerCredentialHelperException;
import com.google.cloud.tools.jib.registry.credentials.NonexistentServerUrlDockerCredentialHelperException;
import com.google.cloud.tools.jib.registry.credentials.RegistryCredentials;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link RetrieveRegistryCredentialsStep}. */
@RunWith(MockitoJUnitRunner.class)
public class RetrieveRegistryCredentialsStepTest {

  @Mock private BuildConfiguration mockBuildConfiguration;
  @Mock private BuildLogger mockBuildLogger;

  @Mock private DockerCredentialRetrieverFactory mockDockerCredentialRetrieverFactory;
  @Mock private DockerCredentialRetriever mockDockerCredentialRetriever;
  /**
   * A {@link DockerCredentialRetriever} that throws {@link
   * NonexistentServerUrlDockerCredentialHelperException}.
   */
  @Mock private DockerCredentialRetriever mockNonexistentServerUrlDockerCredentialRetriever;
  /**
   * A {@link DockerCredentialRetriever} that throws {@link
   * NonexistentDockerCredentialHelperException}.
   */
  @Mock private DockerCredentialRetriever mockNonexistentDockerCredentialRetriever;

  @Mock private Authorization mockAuthorization;

  @Mock private DockerConfigCredentialRetriever mockDockerConfigCredentialRetriever;

  @Mock
  private NonexistentServerUrlDockerCredentialHelperException
      mockNonexistentServerUrlDockerCredentialHelperException;

  @Mock
  private NonexistentDockerCredentialHelperException mockNonexistentDockerCredentialHelperException;

  private static final String fakeTargetRegistry = "someRegistry";

  @Before
  public void setUpMocks()
      throws NonexistentServerUrlDockerCredentialHelperException,
          NonexistentDockerCredentialHelperException, IOException {
    Mockito.when(mockBuildConfiguration.getBuildLogger()).thenReturn(mockBuildLogger);

    Mockito.when(mockDockerCredentialRetriever.retrieve()).thenReturn(mockAuthorization);
    Mockito.when(mockNonexistentServerUrlDockerCredentialRetriever.retrieve())
        .thenThrow(mockNonexistentServerUrlDockerCredentialHelperException);
    Mockito.when(mockNonexistentDockerCredentialRetriever.retrieve())
        .thenThrow(mockNonexistentDockerCredentialHelperException);
  }

  @Test
  public void testCall_useCredentialHelper()
      throws IOException, NonexistentDockerCredentialHelperException,
          NonexistentServerUrlDockerCredentialHelperException {
    Mockito.when(mockBuildConfiguration.getCredentialHelperNames())
        .thenReturn(Arrays.asList("someCredentialHelper", "someOtherCredentialHelper"));
    Mockito.when(mockDockerCredentialRetrieverFactory.withSuffix("someCredentialHelper"))
        .thenReturn(mockNonexistentServerUrlDockerCredentialRetriever);
    Mockito.when(mockDockerCredentialRetrieverFactory.withSuffix("someOtherCredentialHelper"))
        .thenReturn(mockDockerCredentialRetriever);

    Assert.assertEquals(
        mockAuthorization, makeRetrieveRegistryCredentialsStep(fakeTargetRegistry).call());

    Mockito.verify(mockBuildLogger)
        .info("Using docker-credential-someOtherCredentialHelper for " + fakeTargetRegistry);
  }

  @Test
  public void testCall_useKnownRegistryCredentials()
      throws IOException, NonexistentDockerCredentialHelperException,
          NonexistentServerUrlDockerCredentialHelperException {
    // Has no credential helpers be defined.
    Mockito.when(mockBuildConfiguration.getCredentialHelperNames())
        .thenReturn(Collections.emptyList());

    Mockito.when(mockBuildConfiguration.getKnownRegistryCredentials())
        .thenReturn(
            RegistryCredentials.of(fakeTargetRegistry, "credentialSource", mockAuthorization));

    Assert.assertEquals(
        mockAuthorization, makeRetrieveRegistryCredentialsStep(fakeTargetRegistry).call());

    Mockito.verify(mockBuildLogger).info("Using credentialSource for " + fakeTargetRegistry);
  }

  @Test
  public void testCall_useDockerConfig()
      throws IOException, NonexistentDockerCredentialHelperException,
          NonexistentServerUrlDockerCredentialHelperException {
    // Has no credential helpers be defined.
    Mockito.when(mockBuildConfiguration.getCredentialHelperNames())
        .thenReturn(Collections.emptyList());
    // Has known credentials be empty.
    Mockito.when(mockBuildConfiguration.getKnownRegistryCredentials())
        .thenReturn(RegistryCredentials.none());

    Mockito.when(mockDockerConfigCredentialRetriever.retrieve()).thenReturn(mockAuthorization);

    Assert.assertEquals(
        mockAuthorization, makeRetrieveRegistryCredentialsStep(fakeTargetRegistry).call());

    Mockito.verify(mockBuildLogger)
        .info("Using credentials from Docker config for " + fakeTargetRegistry);
  }

  @Test
  public void testCall_inferCommonCredentialHelpers()
      throws IOException, NonexistentDockerCredentialHelperException,
          NonexistentServerUrlDockerCredentialHelperException {
    // Has no credential helpers be defined.
    Mockito.when(mockBuildConfiguration.getCredentialHelperNames())
        .thenReturn(Collections.emptyList());
    // Has known credentials be empty.
    Mockito.when(mockBuildConfiguration.getKnownRegistryCredentials())
        .thenReturn(RegistryCredentials.none());
    // Has no Docker config.
    Mockito.when(mockDockerConfigCredentialRetriever.retrieve()).thenReturn(null);

    Mockito.when(mockDockerCredentialRetrieverFactory.withSuffix("gcr"))
        .thenReturn(mockDockerCredentialRetriever);
    Mockito.when(mockDockerCredentialRetrieverFactory.withSuffix("ecr-login"))
        .thenReturn(mockNonexistentDockerCredentialRetriever);

    Assert.assertEquals(
        mockAuthorization, makeRetrieveRegistryCredentialsStep("something.gcr.io").call());
    Mockito.verify(mockBuildLogger).info("Using docker-credential-gcr for something.gcr.io");

    Mockito.when(mockNonexistentDockerCredentialHelperException.getMessage()).thenReturn("warning");
    Assert.assertEquals(
        null, makeRetrieveRegistryCredentialsStep("something.amazonaws.com").call());
    Mockito.verify(mockBuildLogger).warn("warning");
  }

  /** Creates a fake {@link RetrieveRegistryCredentialsStep} for {@code registry}. */
  private RetrieveRegistryCredentialsStep makeRetrieveRegistryCredentialsStep(String registry) {
    Mockito.when(mockBuildConfiguration.getTargetRegistry()).thenReturn(fakeTargetRegistry);

    return new RetrieveRegistryCredentialsStep(
        mockBuildConfiguration,
        registry,
        mockDockerCredentialRetrieverFactory,
        mockDockerConfigCredentialRetriever);
  }
}
