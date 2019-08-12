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

  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Mock private CredentialRetrieverFactory credentialRetrieverFactory;
  @Mock private CredentialRetriever dockerCredentialHelperCredentialRetriever;
  @Mock private CredentialRetriever knownCredentialRetriever;
  @Mock private CredentialRetriever inferredCredentialRetriever;
  @Mock private CredentialRetriever inferCredentialHelperCredentialRetriever;
  @Mock private CredentialRetriever dockerConfigCredentialRetriever;
  @Mock private CredentialRetriever applicationDefaultCredentialRetriever;

  private final Credential knownCredential = Credential.from("username", "password");
  private final Credential inferredCredential = Credential.from("username2", "password2");

  @Before
  public void setUp() {
    Mockito.when(credentialRetrieverFactory.dockerCredentialHelper(Mockito.anyString()))
        .thenReturn(dockerCredentialHelperCredentialRetriever);
    Mockito.when(credentialRetrieverFactory.known(knownCredential, "credentialSource"))
        .thenReturn(knownCredentialRetriever);
    Mockito.when(credentialRetrieverFactory.known(inferredCredential, "inferredCredentialSource"))
        .thenReturn(inferredCredentialRetriever);
    Mockito.when(credentialRetrieverFactory.wellKnownCredentialHelper())
        .thenReturn(inferCredentialHelperCredentialRetriever);
    Mockito.when(credentialRetrieverFactory.dockerConfig())
        .thenReturn(dockerConfigCredentialRetriever);
    Mockito.when(credentialRetrieverFactory.googleApplicationDefaultCredentials())
        .thenReturn(applicationDefaultCredentialRetriever);
  }

  @Test
  public void testInitAsList() throws FileNotFoundException {
    List<CredentialRetriever> credentialRetrievers =
        DefaultCredentialRetrievers.init(credentialRetrieverFactory).asList();
    Assert.assertEquals(
        Arrays.asList(
            dockerConfigCredentialRetriever,
            inferCredentialHelperCredentialRetriever,
            applicationDefaultCredentialRetriever),
        credentialRetrievers);
  }

  @Test
  public void testInitAsList_all() throws FileNotFoundException {
    List<CredentialRetriever> credentialRetrievers =
        DefaultCredentialRetrievers.init(credentialRetrieverFactory)
            .setKnownCredential(knownCredential, "credentialSource")
            .setInferredCredential(inferredCredential, "inferredCredentialSource")
            .setCredentialHelper("credentialHelperSuffix")
            .asList();
    Assert.assertEquals(
        Arrays.asList(
            knownCredentialRetriever,
            dockerCredentialHelperCredentialRetriever,
            inferredCredentialRetriever,
            dockerConfigCredentialRetriever,
            inferCredentialHelperCredentialRetriever,
            applicationDefaultCredentialRetriever),
        credentialRetrievers);

    Mockito.verify(credentialRetrieverFactory).known(knownCredential, "credentialSource");
    Mockito.verify(credentialRetrieverFactory)
        .known(inferredCredential, "inferredCredentialSource");
    Mockito.verify(credentialRetrieverFactory)
        .dockerCredentialHelper("docker-credential-credentialHelperSuffix");
  }

  @Test
  public void testInitAsList_credentialHelperPath() throws IOException {
    Path fakeCredentialHelperPath = temporaryFolder.newFile("fake-credHelper").toPath();
    DefaultCredentialRetrievers defaultCredentialRetrievers =
        DefaultCredentialRetrievers.init(credentialRetrieverFactory)
            .setCredentialHelper(fakeCredentialHelperPath.toString());

    List<CredentialRetriever> credentialRetrievers = defaultCredentialRetrievers.asList();
    Assert.assertEquals(
        Arrays.asList(
            dockerCredentialHelperCredentialRetriever,
            dockerConfigCredentialRetriever,
            inferCredentialHelperCredentialRetriever,
            applicationDefaultCredentialRetriever),
        credentialRetrievers);
    Mockito.verify(credentialRetrieverFactory)
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
