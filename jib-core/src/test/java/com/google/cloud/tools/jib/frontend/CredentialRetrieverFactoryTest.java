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

package com.google.cloud.tools.jib.frontend;

import com.google.cloud.tools.jib.JibLogger;
import com.google.cloud.tools.jib.configuration.credentials.Credential;
import com.google.cloud.tools.jib.image.ImageReference;
import com.google.cloud.tools.jib.registry.credentials.DockerConfigCredentialRetriever;
import com.google.cloud.tools.jib.registry.credentials.DockerCredentialHelper;
import com.google.cloud.tools.jib.registry.credentials.DockerCredentialHelperFactory;
import com.google.cloud.tools.jib.registry.credentials.NonexistentDockerCredentialHelperException;
import com.google.cloud.tools.jib.registry.credentials.NonexistentServerUrlDockerCredentialHelperException;
import java.io.IOException;
import java.nio.file.Paths;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link CredentialRetrieverFactory}. */
@RunWith(MockitoJUnitRunner.class)
public class CredentialRetrieverFactoryTest {

  private static final Credential FAKE_CREDENTIALS = new Credential("username", "password");

  @Mock private JibLogger mockJibLogger;
  @Mock private DockerCredentialHelperFactory mockDockerCredentialHelperFactory;
  @Mock private DockerCredentialHelper mockDockerCredentialHelper;
  @Mock private DockerConfigCredentialRetriever mockDockerConfigCredentialRetriever;

  /**
   * A {@link DockerCredentialHelper} that throws {@link
   * NonexistentDockerCredentialHelperException}.
   */
  @Mock private DockerCredentialHelper mockNonexistentDockerCredentialHelper;

  @Mock
  private NonexistentDockerCredentialHelperException mockNonexistentDockerCredentialHelperException;

  @Before
  public void setUp()
      throws NonexistentServerUrlDockerCredentialHelperException,
          NonexistentDockerCredentialHelperException, IOException {
    Mockito.when(mockDockerCredentialHelper.retrieve()).thenReturn(FAKE_CREDENTIALS);
    Mockito.when(mockNonexistentDockerCredentialHelper.retrieve())
        .thenThrow(mockNonexistentDockerCredentialHelperException);
  }

  @Test
  public void testDockerCredentialHelper() throws Exception {
    CredentialRetrieverFactory credentialRetrieverFactory =
        CredentialRetrieverFactory.forImage(
            ImageReference.of("registry", null, null), mockJibLogger);

    Mockito.when(
            mockDockerCredentialHelperFactory.newDockerCredentialHelper(
                "registry", Paths.get("docker-credential-helper")))
        .thenReturn(mockDockerCredentialHelper);

    Assert.assertEquals(
        FAKE_CREDENTIALS,
        credentialRetrieverFactory
            .dockerCredentialHelper(
                Paths.get("docker-credential-helper"), mockDockerCredentialHelperFactory)
            .retrieve());

    Mockito.verify(mockJibLogger).info("Using docker-credential-helper for registry");
  }

  @Test
  public void testInferCredentialHelper() throws Exception {
    CredentialRetrieverFactory credentialRetrieverFactory =
        CredentialRetrieverFactory.forImage(
            ImageReference.of("something.gcr.io", null, null), mockJibLogger);
    Mockito.when(
            mockDockerCredentialHelperFactory.newDockerCredentialHelper(
                "something.gcr.io", Paths.get("docker-credential-gcr")))
        .thenReturn(mockDockerCredentialHelper);
    Mockito.when(
            mockDockerCredentialHelperFactory.newDockerCredentialHelper(
                "something.amazonaws.com", Paths.get("docker-credential-ecr-login")))
        .thenReturn(mockNonexistentDockerCredentialHelper);

    Assert.assertEquals(
        FAKE_CREDENTIALS,
        credentialRetrieverFactory
            .inferCredentialHelper(mockDockerCredentialHelperFactory)
            .retrieve());
    Mockito.verify(mockJibLogger).info("Using docker-credential-gcr for something.gcr.io");

    Mockito.when(mockNonexistentDockerCredentialHelperException.getMessage()).thenReturn("warning");
    Mockito.when(mockNonexistentDockerCredentialHelperException.getCause())
        .thenReturn(new IOException("the root cause"));
    Assert.assertNull(
        credentialRetrieverFactory
            .setImageReference(ImageReference.of("something.amazonaws.com", null, null))
            .inferCredentialHelper(mockDockerCredentialHelperFactory)
            .retrieve());
    Mockito.verify(mockJibLogger).warn("warning");
    Mockito.verify(mockJibLogger).info("  Caused by: the root cause");
  }

  @Test
  public void testDockerConfig() throws Exception {
    CredentialRetrieverFactory credentialRetrieverFactory =
        CredentialRetrieverFactory.forImage(
            ImageReference.of("registry", null, null), mockJibLogger);

    Mockito.when(mockDockerConfigCredentialRetriever.retrieve()).thenReturn(FAKE_CREDENTIALS);

    Assert.assertEquals(
        FAKE_CREDENTIALS,
        credentialRetrieverFactory.dockerConfig(mockDockerConfigCredentialRetriever).retrieve());

    Mockito.verify(mockJibLogger).info("Using credentials from Docker config for registry");
  }
}
