/*
 * Copyright 2018 Google LLC. All rights reserved.
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
import com.google.cloud.tools.jib.configuration.ImageConfiguration;
import com.google.cloud.tools.jib.configuration.credentials.Credentials;
import com.google.cloud.tools.jib.http.Authorization;
import com.google.cloud.tools.jib.http.Authorizations;
import com.google.cloud.tools.jib.registry.credentials.DockerConfigCredentialRetriever;
import com.google.cloud.tools.jib.registry.credentials.DockerCredentialHelper;
import com.google.cloud.tools.jib.registry.credentials.DockerCredentialHelperFactory;
import com.google.cloud.tools.jib.registry.credentials.NonexistentDockerCredentialHelperException;
import com.google.cloud.tools.jib.registry.credentials.NonexistentServerUrlDockerCredentialHelperException;
import com.google.cloud.tools.jib.registry.credentials.RegistryCredentials;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.io.IOException;
import java.nio.file.Paths;
import javax.annotation.Nullable;
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

  private static final String FAKE_TARGET_REGISTRY = "someRegistry";
  private static final Credentials FAKE_CREDENTIALS = new Credentials("username", "password");
  private static final Authorization FAKE_AUTHORIZATION =
      Authorizations.withBasicCredentials(
          FAKE_CREDENTIALS.getUsername(), FAKE_CREDENTIALS.getPassword());

  @Mock private ListeningExecutorService mockListeningExecutorService;
  @Mock private ImageConfiguration mockImageConfiguration;
  @Mock private JibLogger mockBuildLogger;

  @Mock private DockerCredentialHelperFactory mockDockerCredentialHelperFactory;
  @Mock private DockerCredentialHelper mockDockerCredentialHelper;
  /**
   * A {@link DockerCredentialHelper} that throws {@link
   * NonexistentServerUrlDockerCredentialHelperException}.
   */
  @Mock private DockerCredentialHelper mockNonexistentServerUrlDockerCredentialHelper;
  /**
   * A {@link DockerCredentialHelper} that throws {@link
   * NonexistentDockerCredentialHelperException}.
   */
  @Mock private DockerCredentialHelper mockNonexistentDockerCredentialHelper;

  @Mock private Authorization mockAuthorization;

  @Mock private DockerConfigCredentialRetriever mockDockerConfigCredentialRetriever;

  @Mock
  private NonexistentServerUrlDockerCredentialHelperException
      mockNonexistentServerUrlDockerCredentialHelperException;

  @Mock
  private NonexistentDockerCredentialHelperException mockNonexistentDockerCredentialHelperException;

  @Before
  public void setUpMocks()
      throws NonexistentServerUrlDockerCredentialHelperException,
          NonexistentDockerCredentialHelperException, IOException {
    Mockito.when(mockDockerCredentialHelper.retrieve()).thenReturn(FAKE_CREDENTIALS);
    Mockito.when(mockNonexistentServerUrlDockerCredentialHelper.retrieve())
        .thenThrow(mockNonexistentServerUrlDockerCredentialHelperException);
    Mockito.when(mockNonexistentDockerCredentialHelper.retrieve())
        .thenThrow(mockNonexistentDockerCredentialHelperException);
  }

  @Test
  public void testCall_useCredentialHelper()
      throws IOException, NonexistentDockerCredentialHelperException {
    Mockito.when(
            mockDockerCredentialHelperFactory.newDockerCredentialHelper(
                "someRegistry", Paths.get("docker-credential-someOtherCredentialHelper")))
        .thenReturn(mockDockerCredentialHelper);

    Assert.assertEquals(
        FAKE_AUTHORIZATION,
        makeRetrieveRegistryCredentialsStep(FAKE_TARGET_REGISTRY, "someOtherCredentialHelper", null)
            .call());

    Mockito.verify(mockBuildLogger)
        .info("Using docker-credential-someOtherCredentialHelper for " + FAKE_TARGET_REGISTRY);
  }

  @Test
  public void testCall_useKnownRegistryCredentials()
      throws IOException, NonexistentDockerCredentialHelperException {
    Assert.assertEquals(
        mockAuthorization,
        makeRetrieveRegistryCredentialsStep(
                FAKE_TARGET_REGISTRY,
                null,
                new RegistryCredentials("credentialSource", mockAuthorization))
            .call());

    Mockito.verify(mockBuildLogger).info("Using credentialSource for " + FAKE_TARGET_REGISTRY);
  }

  @Test
  public void testCall_useDockerConfig()
      throws IOException, NonexistentDockerCredentialHelperException {
    // Credential helper does not have credentials.
    Mockito.when(
            mockDockerCredentialHelperFactory.newDockerCredentialHelper(
                "someRegistry", Paths.get("docker-credential-someCredentialHelper")))
        .thenReturn(mockNonexistentServerUrlDockerCredentialHelper);

    Mockito.when(mockDockerConfigCredentialRetriever.retrieve()).thenReturn(mockAuthorization);

    Assert.assertEquals(
        mockAuthorization,
        makeRetrieveRegistryCredentialsStep(FAKE_TARGET_REGISTRY, "someCredentialHelper", null)
            .call());

    Mockito.verify(mockBuildLogger)
        .info("Using credentials from Docker config for " + FAKE_TARGET_REGISTRY);
  }

  @Test
  public void testCall_inferCommonCredentialHelpers()
      throws IOException, NonexistentDockerCredentialHelperException {
    Mockito.when(
            mockDockerCredentialHelperFactory.newDockerCredentialHelper(
                "something.gcr.io", Paths.get("docker-credential-gcr")))
        .thenReturn(mockDockerCredentialHelper);
    Mockito.when(
            mockDockerCredentialHelperFactory.newDockerCredentialHelper(
                "something.amazonaws.com", Paths.get("docker-credential-ecr-login")))
        .thenReturn(mockNonexistentDockerCredentialHelper);

    Assert.assertEquals(
        mockAuthorization,
        makeRetrieveRegistryCredentialsStep("something.gcr.io", null, null).call());
    Mockito.verify(mockBuildLogger).info("Using docker-credential-gcr for something.gcr.io");

    Mockito.when(mockNonexistentDockerCredentialHelperException.getMessage()).thenReturn("warning");
    Mockito.when(mockNonexistentDockerCredentialHelperException.getCause())
        .thenReturn(new IOException("the root cause"));
    Assert.assertNull(
        makeRetrieveRegistryCredentialsStep("something.amazonaws.com", null, null).call());
    Mockito.verify(mockBuildLogger).warn("warning");
    Mockito.verify(mockBuildLogger).info("  Caused by: the root cause");
  }

  /** Creates a fake {@link RetrieveRegistryCredentialsStep} for {@code registry}. */
  private RetrieveRegistryCredentialsStep makeRetrieveRegistryCredentialsStep(
      String registry,
      @Nullable String credentialHelperSuffix,
      @Nullable RegistryCredentials knownRegistryCredentials) {
    Mockito.when(mockImageConfiguration.getImageRegistry()).thenReturn(FAKE_TARGET_REGISTRY);

    return new RetrieveRegistryCredentialsStep(
        mockListeningExecutorService,
        mockBuildLogger,
        registry,
        credentialHelperSuffix,
        knownRegistryCredentials,
        mockDockerCredentialHelperFactory,
        mockDockerConfigCredentialRetriever);
  }
}
