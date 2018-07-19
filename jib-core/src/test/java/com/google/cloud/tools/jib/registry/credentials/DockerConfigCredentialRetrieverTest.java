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

package com.google.cloud.tools.jib.registry.credentials;

import com.google.cloud.tools.jib.http.Authorization;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
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

  @Mock private Authorization mockAuthorization;
  @Mock private DockerCredentialHelper mockDockerCredentialHelper;
  @Mock private DockerCredentialHelperFactory mockDockerCredentialHelperFactory;

  private Path dockerConfigFile;

  @Before
  public void setUp()
      throws URISyntaxException, NonexistentServerUrlDockerCredentialHelperException,
          NonexistentDockerCredentialHelperException, IOException {
    dockerConfigFile = Paths.get(Resources.getResource("json/dockerconfig.json").toURI());

    Mockito.when(mockDockerCredentialHelper.retrieve()).thenReturn(mockAuthorization);
  }

  @Test
  public void testRetrieve_nonexistentDockerConfigFile() throws IOException {
    DockerConfigCredentialRetriever dockerConfigCredentialRetriever =
        new DockerConfigCredentialRetriever("some registry", Paths.get("fake/path"));

    Assert.assertNull(dockerConfigCredentialRetriever.retrieve());
  }

  @Test
  public void testRetrieve_hasAuth() throws IOException {
    DockerConfigCredentialRetriever dockerConfigCredentialRetriever =
        new DockerConfigCredentialRetriever("some registry", dockerConfigFile, null);

    Authorization authorization = dockerConfigCredentialRetriever.retrieve();
    Assert.assertNotNull(authorization);
    Assert.assertEquals("some auth", authorization.getToken());
  }

  @Test
  public void testRetrieve_useCredsStore() throws IOException {
    Mockito.when(
            mockDockerCredentialHelperFactory.newDockerCredentialHelper(
                "just registry", "some credential store"))
        .thenReturn(mockDockerCredentialHelper);

    DockerConfigCredentialRetriever dockerConfigCredentialRetriever =
        new DockerConfigCredentialRetriever(
            "just registry", dockerConfigFile, mockDockerCredentialHelperFactory);

    Authorization authorization = dockerConfigCredentialRetriever.retrieve();
    Assert.assertEquals(mockAuthorization, authorization);
  }

  @Test
  public void testRetrieve_useCredsStore_withProtocol() throws IOException {
    Mockito.when(
            mockDockerCredentialHelperFactory.newDockerCredentialHelper(
                "https://with.protocol", "some credential store"))
        .thenReturn(mockDockerCredentialHelper);

    DockerConfigCredentialRetriever dockerConfigCredentialRetriever =
        new DockerConfigCredentialRetriever(
            "with.protocol", dockerConfigFile, mockDockerCredentialHelperFactory);

    Authorization authorization = dockerConfigCredentialRetriever.retrieve();
    Assert.assertEquals(mockAuthorization, authorization);
  }

  @Test
  public void testRetrieve_useCredHelper() throws IOException {
    Mockito.when(
            mockDockerCredentialHelperFactory.newDockerCredentialHelper(
                "another registry", "another credential helper"))
        .thenReturn(mockDockerCredentialHelper);

    DockerConfigCredentialRetriever dockerConfigCredentialRetriever =
        new DockerConfigCredentialRetriever(
            "another registry", dockerConfigFile, mockDockerCredentialHelperFactory);

    Authorization authorization = dockerConfigCredentialRetriever.retrieve();
    Assert.assertEquals(mockAuthorization, authorization);
  }

  @Test
  public void testRetrieve_none() throws IOException {
    DockerConfigCredentialRetriever dockerConfigCredentialRetriever =
        new DockerConfigCredentialRetriever("unknown registry", dockerConfigFile);

    Assert.assertNull(dockerConfigCredentialRetriever.retrieve());
  }

  @Test
  public void testRetrieve_credentialFromAlias() throws IOException {
    Mockito.when(
            mockDockerCredentialHelperFactory.newDockerCredentialHelper(
                "index.docker.io", "index.docker.io credential helper"))
        .thenReturn(mockDockerCredentialHelper);

    DockerConfigCredentialRetriever dockerConfigCredentialRetriever =
        new DockerConfigCredentialRetriever(
            "registry.hub.docker.com", dockerConfigFile, mockDockerCredentialHelperFactory);

    Authorization authorization = dockerConfigCredentialRetriever.retrieve();
    Assert.assertEquals(mockAuthorization, authorization);
  }

  @Test
  public void testRetrieve_suffixMatching() throws IOException, URISyntaxException {
    Path dockerConfigFile =
        Paths.get(Resources.getResource("json/dockerconfig_index_docker_io_v1.json").toURI());

    DockerConfigCredentialRetriever dockerConfigCredentialRetriever =
        new DockerConfigCredentialRetriever(
            "index.docker.io", dockerConfigFile, mockDockerCredentialHelperFactory);

    Authorization authorization = dockerConfigCredentialRetriever.retrieve();
    Assert.assertEquals("token for index.docker.io/v1/", authorization.getToken());
  }

  @Test
  public void testRetrieve_suffixMatchingFromAlias() throws IOException, URISyntaxException {
    Path dockerConfigFile =
        Paths.get(Resources.getResource("json/dockerconfig_index_docker_io_v1.json").toURI());

    DockerConfigCredentialRetriever dockerConfigCredentialRetriever =
        new DockerConfigCredentialRetriever(
            "registry.hub.docker.com", dockerConfigFile, mockDockerCredentialHelperFactory);

    Authorization authorization = dockerConfigCredentialRetriever.retrieve();
    Assert.assertEquals("token for index.docker.io/v1/", authorization.getToken());
  }
}
