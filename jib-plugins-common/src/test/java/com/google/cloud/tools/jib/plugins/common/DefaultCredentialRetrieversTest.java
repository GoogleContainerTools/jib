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

package com.google.cloud.tools.jib.plugins.common;

import com.google.cloud.tools.jib.configuration.credentials.Credential;
import com.google.cloud.tools.jib.configuration.credentials.CredentialRetriever;
import com.google.cloud.tools.jib.frontend.CredentialRetrieverFactory;
import java.util.Arrays;
import java.util.List;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link DefaultCredentialRetrievers}. */
@RunWith(MockitoJUnitRunner.class)
public class DefaultCredentialRetrieversTest {

  @Mock private CredentialRetrieverFactory mockCredentialRetrieverFactory;
  @Mock private CredentialRetriever mockDockerCredentialHelperCredentialRetriever;
  @Mock private CredentialRetriever mockKnownCredentialRetriever;
  @Mock private CredentialRetriever mockInferredCredentialRetriever;
  @Mock private CredentialRetriever mockInferCredentialHelperCredentialRetriever;
  @Mock private CredentialRetriever mockDockerConfigCredentialRetriever;

  private final Credential knownCredential = Credential.basic("username", "password");
  private final Credential inferredCredential = Credential.basic("username2", "password2");

  @Before
  public void setUp() {
    Mockito.when(mockCredentialRetrieverFactory.dockerCredentialHelper(Mockito.anyString()))
        .thenReturn(mockDockerCredentialHelperCredentialRetriever);
    Mockito.when(mockCredentialRetrieverFactory.known(knownCredential, "credentialSource"))
        .thenReturn(mockKnownCredentialRetriever);
    Mockito.when(
            mockCredentialRetrieverFactory.known(inferredCredential, "inferredCredentialSource"))
        .thenReturn(mockInferredCredentialRetriever);
    Mockito.when(mockCredentialRetrieverFactory.inferCredentialHelper())
        .thenReturn(mockInferCredentialHelperCredentialRetriever);
    Mockito.when(mockCredentialRetrieverFactory.dockerConfig())
        .thenReturn(mockDockerConfigCredentialRetriever);
  }

  @Test
  public void testInitAsList() {
    List<CredentialRetriever> credentialRetrievers =
        DefaultCredentialRetrievers.init(mockCredentialRetrieverFactory).asList();
    Assert.assertEquals(
        Arrays.asList(
            mockInferCredentialHelperCredentialRetriever, mockDockerConfigCredentialRetriever),
        credentialRetrievers);
  }

  @Test
  public void testInitAsList_all() {
    List<CredentialRetriever> credentialRetrievers =
        DefaultCredentialRetrievers.init(mockCredentialRetrieverFactory)
            .setKnownCredential(knownCredential, "credentialSource")
            .setInferredCredential(inferredCredential, "inferredCredentialSource")
            .setCredentialHelperSuffix("credentialHelperSuffix")
            .asList();
    Assert.assertEquals(
        Arrays.asList(
            mockKnownCredentialRetriever,
            mockDockerCredentialHelperCredentialRetriever,
            mockInferredCredentialRetriever,
            mockInferCredentialHelperCredentialRetriever,
            mockDockerConfigCredentialRetriever),
        credentialRetrievers);

    Mockito.verify(mockCredentialRetrieverFactory).known(knownCredential, "credentialSource");
    Mockito.verify(mockCredentialRetrieverFactory)
        .known(inferredCredential, "inferredCredentialSource");
    Mockito.verify(mockCredentialRetrieverFactory)
        .dockerCredentialHelper("docker-credential-credentialHelperSuffix");
  }
}
