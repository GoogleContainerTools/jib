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

package com.google.cloud.tools.jib.registry.credentials;

import com.google.cloud.tools.jib.api.Credential;
import com.google.cloud.tools.jib.api.LogEvent;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link DockerConfigCredentialRetriever}. */
@RunWith(MockitoJUnitRunner.class)
public class DockerConfigCredentialRetrieverTest {

  private static final Credential FAKE_CREDENTIAL = Credential.from("username", "password");

  @Mock private DockerCredentialHelper mockDockerCredentialHelper;
  @Mock private DockerConfig mockDockerConfig;
  @Mock private Consumer<LogEvent> mockLogger;

  private Path dockerConfigFile;

  @Before
  public void setUp()
      throws URISyntaxException, CredentialHelperUnhandledServerUrlException,
          CredentialHelperNotFoundException, IOException {
    dockerConfigFile = Paths.get(Resources.getResource("core/json/dockerconfig.json").toURI());
    Mockito.when(mockDockerCredentialHelper.retrieve()).thenReturn(FAKE_CREDENTIAL);
  }

  @Test
  public void testRetrieve_nonexistentDockerConfigFile() throws IOException {
    DockerConfigCredentialRetriever dockerConfigCredentialRetriever =
        new DockerConfigCredentialRetriever("some registry", Paths.get("fake/path"));

    Assert.assertFalse(dockerConfigCredentialRetriever.retrieve(mockLogger).isPresent());
  }

  @Test
  public void testRetrieve_hasAuth() throws IOException {
    DockerConfigCredentialRetriever dockerConfigCredentialRetriever =
        new DockerConfigCredentialRetriever("some registry", dockerConfigFile);

    Optional<Credential> credentials = dockerConfigCredentialRetriever.retrieve(mockLogger);
    Assert.assertTrue(credentials.isPresent());
    Assert.assertEquals("some", credentials.get().getUsername());
    Assert.assertEquals("auth", credentials.get().getPassword());
  }

  @Test
  public void testRetrieve_useCredsStore() {
    Mockito.when(mockDockerConfig.getCredentialHelperFor("just registry"))
        .thenReturn(mockDockerCredentialHelper);
    DockerConfigCredentialRetriever dockerConfigCredentialRetriever =
        new DockerConfigCredentialRetriever("just registry", dockerConfigFile);

    Assert.assertEquals(
        FAKE_CREDENTIAL,
        dockerConfigCredentialRetriever
            .retrieve(mockDockerConfig, mockLogger)
            .orElseThrow(AssertionError::new));
  }

  @Test
  public void testRetrieve_useCredsStore_withProtocol() {
    Mockito.when(mockDockerConfig.getCredentialHelperFor("with.protocol"))
        .thenReturn(mockDockerCredentialHelper);
    DockerConfigCredentialRetriever dockerConfigCredentialRetriever =
        new DockerConfigCredentialRetriever("with.protocol", dockerConfigFile);

    Assert.assertEquals(
        FAKE_CREDENTIAL,
        dockerConfigCredentialRetriever
            .retrieve(mockDockerConfig, mockLogger)
            .orElseThrow(AssertionError::new));
  }

  @Test
  public void testRetrieve_useCredHelper() {
    Mockito.when(mockDockerConfig.getCredentialHelperFor("another registry"))
        .thenReturn(mockDockerCredentialHelper);
    DockerConfigCredentialRetriever dockerConfigCredentialRetriever =
        new DockerConfigCredentialRetriever("another registry", dockerConfigFile);

    Assert.assertEquals(
        FAKE_CREDENTIAL,
        dockerConfigCredentialRetriever
            .retrieve(mockDockerConfig, mockLogger)
            .orElseThrow(AssertionError::new));
  }

  @Test
  public void testRetrieve_useCredHelper_warn()
      throws CredentialHelperUnhandledServerUrlException, CredentialHelperNotFoundException,
          IOException {
    Mockito.when(mockDockerConfig.getCredentialHelperFor("another registry"))
        .thenReturn(mockDockerCredentialHelper);
    Mockito.when(mockDockerCredentialHelper.retrieve())
        .thenThrow(
            new CredentialHelperNotFoundException(
                Paths.get("docker-credential-path"), new Throwable("cause")));

    new DockerConfigCredentialRetriever("another registry", dockerConfigFile)
        .retrieve(mockDockerConfig, mockLogger);

    Mockito.verify(mockLogger)
        .accept(LogEvent.warn("The system does not have docker-credential-path CLI"));
    Mockito.verify(mockLogger).accept(LogEvent.warn("  Caused by: cause"));
  }

  @Test
  public void testRetrieve_none() throws IOException {
    DockerConfigCredentialRetriever dockerConfigCredentialRetriever =
        new DockerConfigCredentialRetriever("unknown registry", dockerConfigFile);

    Assert.assertFalse(dockerConfigCredentialRetriever.retrieve(mockLogger).isPresent());
  }

  @Test
  public void testRetrieve_credentialFromAlias() {
    Mockito.when(mockDockerConfig.getCredentialHelperFor("registry.hub.docker.com"))
        .thenReturn(mockDockerCredentialHelper);
    DockerConfigCredentialRetriever dockerConfigCredentialRetriever =
        new DockerConfigCredentialRetriever("registry.hub.docker.com", dockerConfigFile);

    Assert.assertEquals(
        FAKE_CREDENTIAL,
        dockerConfigCredentialRetriever
            .retrieve(mockDockerConfig, mockLogger)
            .orElseThrow(AssertionError::new));
  }

  @Test
  public void testRetrieve_suffixMatching() throws IOException, URISyntaxException {
    Path dockerConfigFile =
        Paths.get(Resources.getResource("core/json/dockerconfig_index_docker_io_v1.json").toURI());

    DockerConfigCredentialRetriever dockerConfigCredentialRetriever =
        new DockerConfigCredentialRetriever("index.docker.io", dockerConfigFile);

    Optional<Credential> credentials = dockerConfigCredentialRetriever.retrieve(mockLogger);
    Assert.assertTrue(credentials.isPresent());
    Assert.assertEquals("token for", credentials.get().getUsername());
    Assert.assertEquals(" index.docker.io/v1/", credentials.get().getPassword());
  }

  @Test
  public void testRetrieve_suffixMatchingFromAlias() throws IOException, URISyntaxException {
    Path dockerConfigFile =
        Paths.get(Resources.getResource("core/json/dockerconfig_index_docker_io_v1.json").toURI());

    DockerConfigCredentialRetriever dockerConfigCredentialRetriever =
        new DockerConfigCredentialRetriever("registry.hub.docker.com", dockerConfigFile);

    Optional<Credential> credentials = dockerConfigCredentialRetriever.retrieve(mockLogger);
    Assert.assertTrue(credentials.isPresent());
    Assert.assertEquals("token for", credentials.get().getUsername());
    Assert.assertEquals(" index.docker.io/v1/", credentials.get().getPassword());
  }
}
