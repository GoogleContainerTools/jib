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

import com.google.cloud.tools.jib.api.Credential;
import com.google.cloud.tools.jib.api.CredentialRetriever;
import com.google.cloud.tools.jib.frontend.CredentialRetrieverFactory;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link DefaultCredentialRetrievers}. */
@RunWith(MockitoJUnitRunner.class)
public class DefaultCredentialRetrieversTest {

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Mock private CredentialRetrieverFactory mockCredentialRetrieverFactory;
  @Mock private CredentialRetriever mockDockerCredentialHelperCredentialRetriever;
  @Mock private CredentialRetriever mockKnownCredentialRetriever;
  @Mock private CredentialRetriever mockInferredCredentialRetriever;
  @Mock private CredentialRetriever mockInferCredentialHelperCredentialRetriever;
  @Mock private CredentialRetriever mockDockerConfigCredentialRetriever;

  private final Credential knownCredential = Credential.from("username", "password");
  private final Credential inferredCredential = Credential.from("username2", "password2");

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
  public void testInitAsList() throws FileNotFoundException {
    List<CredentialRetriever> credentialRetrievers =
        DefaultCredentialRetrievers.init(mockCredentialRetrieverFactory).asList();
    Assert.assertEquals(
        Arrays.asList(
            mockDockerConfigCredentialRetriever, mockInferCredentialHelperCredentialRetriever),
        credentialRetrievers);
  }

  @Test
  public void testInitAsList_all() throws FileNotFoundException {
    List<CredentialRetriever> credentialRetrievers =
        DefaultCredentialRetrievers.init(mockCredentialRetrieverFactory)
            .setKnownCredential(knownCredential, "credentialSource")
            .setInferredCredential(inferredCredential, "inferredCredentialSource")
            .setCredentialHelper("credentialHelperSuffix")
            .asList();
    Assert.assertEquals(
        Arrays.asList(
            mockKnownCredentialRetriever,
            mockDockerCredentialHelperCredentialRetriever,
            mockInferredCredentialRetriever,
            mockDockerConfigCredentialRetriever,
            mockInferCredentialHelperCredentialRetriever),
        credentialRetrievers);

    Mockito.verify(mockCredentialRetrieverFactory).known(knownCredential, "credentialSource");
    Mockito.verify(mockCredentialRetrieverFactory)
        .known(inferredCredential, "inferredCredentialSource");
    Mockito.verify(mockCredentialRetrieverFactory)
        .dockerCredentialHelper("docker-credential-credentialHelperSuffix");
  }

  @Test
  public void testInitAsList_credentialHelperPath() throws IOException {
    Path fakeCredentialHelperPath = temporaryFolder.newFile("fake-credHelper").toPath();
    DefaultCredentialRetrievers defaultCredentialRetrievers =
        DefaultCredentialRetrievers.init(mockCredentialRetrieverFactory)
            .setCredentialHelper(fakeCredentialHelperPath.toString());

    List<CredentialRetriever> credentialRetrievers = defaultCredentialRetrievers.asList();
    Assert.assertEquals(
        Arrays.asList(
            mockDockerCredentialHelperCredentialRetriever,
            mockDockerConfigCredentialRetriever,
            mockInferCredentialHelperCredentialRetriever),
        credentialRetrievers);
    Mockito.verify(mockCredentialRetrieverFactory)
        .dockerCredentialHelper(fakeCredentialHelperPath.toString());

    Files.delete(fakeCredentialHelperPath);
    try {
      defaultCredentialRetrievers.asList();
      Assert.fail("Expected FileNotFoundException");
    } catch (FileNotFoundException ex) {
      Assert.assertEquals(
          "Specified credential helper was not found: " + fakeCredentialHelperPath,
          ex.getMessage());
    }
  }
}
