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
import com.google.common.collect.ImmutableMap;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
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

  @Mock private CredentialRetrieverFactory mockCredentialRetrieverFactory;
  @Mock private CredentialRetriever mockDockerCredentialHelperCredentialRetriever;
  @Mock private CredentialRetriever mockKnownCredentialRetriever;
  @Mock private CredentialRetriever mockInferredCredentialRetriever;
  @Mock private CredentialRetriever mockWellKnownCredentialHelpersCredentialRetriever;
  @Mock private CredentialRetriever mockSystemHomeDockerConfigCredentialRetriever;
  @Mock private CredentialRetriever mockSystemHomeOldDockerConfigCredentialRetriever;
  @Mock private CredentialRetriever mockEnvHomeDockerConfigCredentialRetriever;
  @Mock private CredentialRetriever mockEnvHomeOldDockerConfigCredentialRetriever;
  @Mock private CredentialRetriever mockApplicationDefaultCredentialRetriever;

  private Properties properties;
  private Map<String, String> environment;

  private final Credential knownCredential = Credential.from("username", "password");
  private final Credential inferredCredential = Credential.from("username2", "password2");

  @Before
  public void setUp() {
    properties = new Properties();
    properties.setProperty("user.home", Paths.get("/system/home").toString());
    environment = ImmutableMap.of("HOME", Paths.get("/env/home").toString());

    Mockito.when(mockCredentialRetrieverFactory.dockerCredentialHelper(Mockito.anyString()))
        .thenReturn(mockDockerCredentialHelperCredentialRetriever);
    Mockito.when(mockCredentialRetrieverFactory.known(knownCredential, "credentialSource"))
        .thenReturn(mockKnownCredentialRetriever);
    Mockito.when(
            mockCredentialRetrieverFactory.known(inferredCredential, "inferredCredentialSource"))
        .thenReturn(mockInferredCredentialRetriever);
    Mockito.when(mockCredentialRetrieverFactory.wellKnownCredentialHelpers())
        .thenReturn(mockWellKnownCredentialHelpersCredentialRetriever);
    Mockito.when(
            mockCredentialRetrieverFactory.dockerConfig(
                Paths.get("/system/home/.docker/config.json")))
        .thenReturn(mockSystemHomeDockerConfigCredentialRetriever);
    Mockito.when(
            mockCredentialRetrieverFactory.dockerConfig(
                Paths.get("/system/home/.docker/.dockercfg")))
        .thenReturn(mockSystemHomeOldDockerConfigCredentialRetriever);
    Mockito.when(
            mockCredentialRetrieverFactory.dockerConfig(Paths.get("/env/home/.docker/config.json")))
        .thenReturn(mockEnvHomeDockerConfigCredentialRetriever);
    Mockito.when(
            mockCredentialRetrieverFactory.dockerConfig(Paths.get("/env/home/.docker/.dockercfg")))
        .thenReturn(mockEnvHomeOldDockerConfigCredentialRetriever);
    Mockito.when(mockCredentialRetrieverFactory.googleApplicationDefaultCredentials())
        .thenReturn(mockApplicationDefaultCredentialRetriever);
  }

  @Test
  public void testAsList() throws FileNotFoundException {
    List<CredentialRetriever> credentialRetrievers =
        new DefaultCredentialRetrievers(mockCredentialRetrieverFactory, properties, environment)
            .asList();
    Assert.assertEquals(
        Arrays.asList(
            mockSystemHomeDockerConfigCredentialRetriever,
            mockSystemHomeOldDockerConfigCredentialRetriever,
            mockEnvHomeDockerConfigCredentialRetriever,
            mockEnvHomeOldDockerConfigCredentialRetriever,
            mockWellKnownCredentialHelpersCredentialRetriever,
            mockApplicationDefaultCredentialRetriever),
        credentialRetrievers);
  }

  @Test
  public void testAsList_all() throws FileNotFoundException {
    List<CredentialRetriever> credentialRetrievers =
        new DefaultCredentialRetrievers(mockCredentialRetrieverFactory, properties, environment)
            .setKnownCredential(knownCredential, "credentialSource")
            .setInferredCredential(inferredCredential, "inferredCredentialSource")
            .setCredentialHelper("credentialHelperSuffix")
            .asList();
    Assert.assertEquals(
        Arrays.asList(
            mockKnownCredentialRetriever,
            mockDockerCredentialHelperCredentialRetriever,
            mockInferredCredentialRetriever,
            mockSystemHomeDockerConfigCredentialRetriever,
            mockSystemHomeOldDockerConfigCredentialRetriever,
            mockEnvHomeDockerConfigCredentialRetriever,
            mockEnvHomeOldDockerConfigCredentialRetriever,
            mockWellKnownCredentialHelpersCredentialRetriever,
            mockApplicationDefaultCredentialRetriever),
        credentialRetrievers);

    Mockito.verify(mockCredentialRetrieverFactory).known(knownCredential, "credentialSource");
    Mockito.verify(mockCredentialRetrieverFactory)
        .known(inferredCredential, "inferredCredentialSource");
    Mockito.verify(mockCredentialRetrieverFactory)
        .dockerCredentialHelper("docker-credential-credentialHelperSuffix");
  }

  @Test
  public void testAsList_credentialHelperPath() throws IOException {
    Path fakeCredentialHelperPath = temporaryFolder.newFile("fake-credHelper").toPath();
    DefaultCredentialRetrievers defaultCredentialRetrievers =
        new DefaultCredentialRetrievers(mockCredentialRetrieverFactory, properties, environment)
            .setCredentialHelper(fakeCredentialHelperPath.toString());

    List<CredentialRetriever> credentialRetrievers = defaultCredentialRetrievers.asList();
    Assert.assertEquals(
        Arrays.asList(
            mockDockerCredentialHelperCredentialRetriever,
            mockSystemHomeDockerConfigCredentialRetriever,
            mockSystemHomeOldDockerConfigCredentialRetriever,
            mockEnvHomeDockerConfigCredentialRetriever,
            mockEnvHomeOldDockerConfigCredentialRetriever,
            mockWellKnownCredentialHelpersCredentialRetriever,
            mockApplicationDefaultCredentialRetriever),
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

  @Test
  public void testDockerConfigRetrievers_undefinedHome() throws FileNotFoundException {
    List<CredentialRetriever> credentialRetrievers =
        new DefaultCredentialRetrievers(
                mockCredentialRetrieverFactory, new Properties(), new HashMap<>())
            .asList();
    Assert.assertEquals(
        Arrays.asList(
            mockWellKnownCredentialHelpersCredentialRetriever,
            mockApplicationDefaultCredentialRetriever),
        credentialRetrievers);
  }

  @Test
  public void testDockerConfigRetrievers_noDuplicateRetrivers() throws FileNotFoundException {
    properties.setProperty("user.home", Paths.get("/env/home").toString());
    List<CredentialRetriever> credentialRetrievers =
        new DefaultCredentialRetrievers(mockCredentialRetrieverFactory, properties, environment)
            .asList();
    Assert.assertEquals(
        Arrays.asList(
            mockEnvHomeDockerConfigCredentialRetriever,
            mockEnvHomeOldDockerConfigCredentialRetriever,
            mockWellKnownCredentialHelpersCredentialRetriever,
            mockApplicationDefaultCredentialRetriever),
        credentialRetrievers);
  }
}
