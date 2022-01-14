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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.cloud.tools.jib.api.Credential;
import com.google.cloud.tools.jib.api.CredentialRetriever;
import com.google.cloud.tools.jib.frontend.CredentialRetrieverFactory;
import com.google.common.collect.ImmutableMap;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
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
  @Mock private CredentialRetriever mockXdgPrimaryCredentialRetriever;
  @Mock private CredentialRetriever mockEnvHomeXdgCredentialRetriever;
  @Mock private CredentialRetriever mockSystemHomeXdgCredentialRetriever;
  @Mock private CredentialRetriever mockDockerConfigEnvDockerConfigCredentialRetriever;
  @Mock private CredentialRetriever mockDockerConfigEnvKubernetesDockerConfigCredentialRetriever;
  @Mock private CredentialRetriever mockDockerConfigEnvLegacyDockerConfigCredentialRetriever;
  @Mock private CredentialRetriever mockSystemHomeDockerConfigCredentialRetriever;
  @Mock private CredentialRetriever mockSystemHomeKubernetesDockerConfigCredentialRetriever;
  @Mock private CredentialRetriever mockSystemHomeLegacyDockerConfigCredentialRetriever;
  @Mock private CredentialRetriever mockEnvHomeDockerConfigCredentialRetriever;
  @Mock private CredentialRetriever mockEnvHomeKubernetesDockerConfigCredentialRetriever;
  @Mock private CredentialRetriever mockEnvHomeLegacyDockerConfigCredentialRetriever;
  @Mock private CredentialRetriever mockApplicationDefaultCredentialRetriever;

  private Properties properties;
  private Map<String, String> environment;

  private final Credential knownCredential = Credential.from("username", "password");
  private final Credential inferredCredential = Credential.from("username2", "password2");

  @Before
  public void setUp() {
    properties = new Properties();
    properties.setProperty("os.name", "unknown");
    properties.setProperty("user.home", Paths.get("/system/home").toString());
    environment =
        ImmutableMap.of(
            "HOME",
            Paths.get("/env/home").toString(),
            "DOCKER_CONFIG",
            Paths.get("/docker_config").toString(),
            "XDG_RUNTIME_DIR",
            Paths.get("/run/user/1000").toString(),
            "XDG_CONFIG_HOME",
            Paths.get("/env/home/.config").toString());

    when(mockCredentialRetrieverFactory.dockerCredentialHelper(anyString()))
        .thenReturn(mockDockerCredentialHelperCredentialRetriever);
    when(mockCredentialRetrieverFactory.known(knownCredential, "credentialSource"))
        .thenReturn(mockKnownCredentialRetriever);
    when(mockCredentialRetrieverFactory.known(inferredCredential, "inferredCredentialSource"))
        .thenReturn(mockInferredCredentialRetriever);
    when(mockCredentialRetrieverFactory.wellKnownCredentialHelpers())
        .thenReturn(mockWellKnownCredentialHelpersCredentialRetriever);

    when(mockCredentialRetrieverFactory.dockerConfig(
            Paths.get("/run/user/1000/containers/auth.json")))
        .thenReturn(mockXdgPrimaryCredentialRetriever);
    when(mockCredentialRetrieverFactory.dockerConfig(
            Paths.get("/env/home/.config/containers/auth.json")))
        .thenReturn(mockEnvHomeXdgCredentialRetriever);

    when(mockCredentialRetrieverFactory.dockerConfig(
            Paths.get("/system/home/.config/containers/auth.json")))
        .thenReturn(mockSystemHomeXdgCredentialRetriever);

    when(mockCredentialRetrieverFactory.dockerConfig(Paths.get("/docker_config/config.json")))
        .thenReturn(mockDockerConfigEnvDockerConfigCredentialRetriever);
    when(mockCredentialRetrieverFactory.dockerConfig(Paths.get("/docker_config/.dockerconfigjson")))
        .thenReturn(mockDockerConfigEnvKubernetesDockerConfigCredentialRetriever);
    when(mockCredentialRetrieverFactory.legacyDockerConfig(Paths.get("/docker_config/.dockercfg")))
        .thenReturn(mockDockerConfigEnvLegacyDockerConfigCredentialRetriever);
    when(mockCredentialRetrieverFactory.dockerConfig(Paths.get("/system/home/.docker/config.json")))
        .thenReturn(mockSystemHomeDockerConfigCredentialRetriever);
    when(mockCredentialRetrieverFactory.dockerConfig(
            Paths.get("/system/home/.docker/.dockerconfigjson")))
        .thenReturn(mockSystemHomeKubernetesDockerConfigCredentialRetriever);
    when(mockCredentialRetrieverFactory.legacyDockerConfig(
            Paths.get("/system/home/.docker/.dockercfg")))
        .thenReturn(mockSystemHomeLegacyDockerConfigCredentialRetriever);
    when(mockCredentialRetrieverFactory.dockerConfig(Paths.get("/env/home/.docker/config.json")))
        .thenReturn(mockEnvHomeDockerConfigCredentialRetriever);
    when(mockCredentialRetrieverFactory.dockerConfig(
            Paths.get("/env/home/.docker/.dockerconfigjson")))
        .thenReturn(mockEnvHomeKubernetesDockerConfigCredentialRetriever);
    when(mockCredentialRetrieverFactory.legacyDockerConfig(
            Paths.get("/env/home/.docker/.dockercfg")))
        .thenReturn(mockEnvHomeLegacyDockerConfigCredentialRetriever);
    when(mockCredentialRetrieverFactory.googleApplicationDefaultCredentials())
        .thenReturn(mockApplicationDefaultCredentialRetriever);
  }

  @Test
  public void testAsList() throws FileNotFoundException {
    List<CredentialRetriever> retriever =
        new DefaultCredentialRetrievers(mockCredentialRetrieverFactory, properties, environment)
            .asList();
    assertThat(retriever)
        .containsExactly(
            mockXdgPrimaryCredentialRetriever,
            mockEnvHomeXdgCredentialRetriever,
            mockSystemHomeXdgCredentialRetriever,
            mockDockerConfigEnvDockerConfigCredentialRetriever,
            mockDockerConfigEnvKubernetesDockerConfigCredentialRetriever,
            mockDockerConfigEnvLegacyDockerConfigCredentialRetriever,
            mockSystemHomeDockerConfigCredentialRetriever,
            mockSystemHomeKubernetesDockerConfigCredentialRetriever,
            mockSystemHomeLegacyDockerConfigCredentialRetriever,
            mockEnvHomeDockerConfigCredentialRetriever,
            mockEnvHomeKubernetesDockerConfigCredentialRetriever,
            mockEnvHomeLegacyDockerConfigCredentialRetriever,
            mockWellKnownCredentialHelpersCredentialRetriever,
            mockApplicationDefaultCredentialRetriever)
        .inOrder();
  }

  @Test
  public void testAsList_all() throws FileNotFoundException {
    List<CredentialRetriever> retrievers =
        new DefaultCredentialRetrievers(mockCredentialRetrieverFactory, properties, environment)
            .setKnownCredential(knownCredential, "credentialSource")
            .setInferredCredential(inferredCredential, "inferredCredentialSource")
            .setCredentialHelper("credentialHelperSuffix")
            .asList();
    assertThat(retrievers)
        .containsExactly(
            mockKnownCredentialRetriever,
            mockDockerCredentialHelperCredentialRetriever,
            mockInferredCredentialRetriever,
            mockXdgPrimaryCredentialRetriever,
            mockEnvHomeXdgCredentialRetriever,
            mockSystemHomeXdgCredentialRetriever,
            mockDockerConfigEnvDockerConfigCredentialRetriever,
            mockDockerConfigEnvKubernetesDockerConfigCredentialRetriever,
            mockDockerConfigEnvLegacyDockerConfigCredentialRetriever,
            mockSystemHomeDockerConfigCredentialRetriever,
            mockSystemHomeKubernetesDockerConfigCredentialRetriever,
            mockSystemHomeLegacyDockerConfigCredentialRetriever,
            mockEnvHomeDockerConfigCredentialRetriever,
            mockEnvHomeKubernetesDockerConfigCredentialRetriever,
            mockEnvHomeLegacyDockerConfigCredentialRetriever,
            mockWellKnownCredentialHelpersCredentialRetriever,
            mockApplicationDefaultCredentialRetriever)
        .inOrder();

    verify(mockCredentialRetrieverFactory).known(knownCredential, "credentialSource");
    verify(mockCredentialRetrieverFactory).known(inferredCredential, "inferredCredentialSource");
    verify(mockCredentialRetrieverFactory)
        .dockerCredentialHelper("docker-credential-credentialHelperSuffix");
  }

  @Test
  public void testAsList_credentialHelperPath() throws IOException {
    Path fakeCredentialHelperPath = temporaryFolder.newFile("fake-credHelper").toPath();
    DefaultCredentialRetrievers credentialRetrievers =
        new DefaultCredentialRetrievers(mockCredentialRetrieverFactory, properties, environment)
            .setCredentialHelper(fakeCredentialHelperPath.toString());

    List<CredentialRetriever> retrievers = credentialRetrievers.asList();
    assertThat(retrievers)
        .containsExactly(
            mockDockerCredentialHelperCredentialRetriever,
            mockXdgPrimaryCredentialRetriever,
            mockEnvHomeXdgCredentialRetriever,
            mockSystemHomeXdgCredentialRetriever,
            mockDockerConfigEnvDockerConfigCredentialRetriever,
            mockDockerConfigEnvKubernetesDockerConfigCredentialRetriever,
            mockDockerConfigEnvLegacyDockerConfigCredentialRetriever,
            mockSystemHomeDockerConfigCredentialRetriever,
            mockSystemHomeKubernetesDockerConfigCredentialRetriever,
            mockSystemHomeLegacyDockerConfigCredentialRetriever,
            mockEnvHomeDockerConfigCredentialRetriever,
            mockEnvHomeKubernetesDockerConfigCredentialRetriever,
            mockEnvHomeLegacyDockerConfigCredentialRetriever,
            mockWellKnownCredentialHelpersCredentialRetriever,
            mockApplicationDefaultCredentialRetriever)
        .inOrder();
    verify(mockCredentialRetrieverFactory)
        .dockerCredentialHelper(fakeCredentialHelperPath.toString());

    Files.delete(fakeCredentialHelperPath);
    Exception ex = assertThrows(FileNotFoundException.class, () -> credentialRetrievers.asList());
    assertThat(ex)
        .hasMessageThat()
        .isEqualTo("Specified credential helper was not found: " + fakeCredentialHelperPath);
  }

  @Test
  public void testDockerConfigRetrievers_undefinedHome() throws FileNotFoundException {
    List<CredentialRetriever> retrievers =
        new DefaultCredentialRetrievers(
                mockCredentialRetrieverFactory, new Properties(), new HashMap<>())
            .asList();
    assertThat(retrievers)
        .containsExactly(
            mockWellKnownCredentialHelpersCredentialRetriever,
            mockApplicationDefaultCredentialRetriever)
        .inOrder();
  }

  @Test
  public void testDockerConfigRetrievers_noDuplicateRetrievers() throws FileNotFoundException {
    properties.setProperty("user.home", Paths.get("/env/home").toString());
    List<CredentialRetriever> retrievers =
        new DefaultCredentialRetrievers(mockCredentialRetrieverFactory, properties, environment)
            .asList();
    assertThat(retrievers)
        .containsExactly(
            mockXdgPrimaryCredentialRetriever,
            mockEnvHomeXdgCredentialRetriever,
            mockDockerConfigEnvDockerConfigCredentialRetriever,
            mockDockerConfigEnvKubernetesDockerConfigCredentialRetriever,
            mockDockerConfigEnvLegacyDockerConfigCredentialRetriever,
            mockEnvHomeDockerConfigCredentialRetriever,
            mockEnvHomeKubernetesDockerConfigCredentialRetriever,
            mockEnvHomeLegacyDockerConfigCredentialRetriever,
            mockWellKnownCredentialHelpersCredentialRetriever,
            mockApplicationDefaultCredentialRetriever)
        .inOrder();

    environment =
        ImmutableMap.of(
            "HOME",
            Paths.get("/env/home").toString(),
            "DOCKER_CONFIG",
            Paths.get("/env/home/.docker").toString());
    retrievers =
        new DefaultCredentialRetrievers(mockCredentialRetrieverFactory, properties, environment)
            .asList();
    assertThat(retrievers)
        .containsExactly(
            mockEnvHomeXdgCredentialRetriever,
            mockEnvHomeDockerConfigCredentialRetriever,
            mockEnvHomeKubernetesDockerConfigCredentialRetriever,
            mockEnvHomeLegacyDockerConfigCredentialRetriever,
            mockWellKnownCredentialHelpersCredentialRetriever,
            mockApplicationDefaultCredentialRetriever)
        .inOrder();
  }

  @Test
  public void testCredentialHelper_cmdExtension() throws IOException {
    Path credHelper = temporaryFolder.newFile("foo.cmd").toPath();
    Path pathWithoutCmd = credHelper.getParent().resolve("foo");
    assertThat(credHelper).isEqualTo(pathWithoutCmd.getParent().resolve("foo.cmd"));

    DefaultCredentialRetrievers credentialRetrievers =
        new DefaultCredentialRetrievers(mockCredentialRetrieverFactory, properties, environment)
            .setCredentialHelper(pathWithoutCmd.toString());
    Exception ex = assertThrows(FileNotFoundException.class, () -> credentialRetrievers.asList());
    assertThat(ex).hasMessageThat().startsWith("Specified credential helper was not found:");
    assertThat(ex).hasMessageThat().endsWith("foo");

    properties.setProperty("os.name", "winDOWs");
    List<CredentialRetriever> retrievers =
        new DefaultCredentialRetrievers(mockCredentialRetrieverFactory, properties, environment)
            .setCredentialHelper(pathWithoutCmd.toString())
            .asList();

    assertThat(retrievers)
        .containsExactly(
            mockDockerCredentialHelperCredentialRetriever,
            mockXdgPrimaryCredentialRetriever,
            mockEnvHomeXdgCredentialRetriever,
            mockSystemHomeXdgCredentialRetriever,
            mockDockerConfigEnvDockerConfigCredentialRetriever,
            mockDockerConfigEnvKubernetesDockerConfigCredentialRetriever,
            mockDockerConfigEnvLegacyDockerConfigCredentialRetriever,
            mockSystemHomeDockerConfigCredentialRetriever,
            mockSystemHomeKubernetesDockerConfigCredentialRetriever,
            mockSystemHomeLegacyDockerConfigCredentialRetriever,
            mockEnvHomeDockerConfigCredentialRetriever,
            mockEnvHomeKubernetesDockerConfigCredentialRetriever,
            mockEnvHomeLegacyDockerConfigCredentialRetriever,
            mockWellKnownCredentialHelpersCredentialRetriever,
            mockApplicationDefaultCredentialRetriever)
        .inOrder();
  }

  @Test
  public void testCredentialHelper_exeExtension() throws IOException {
    Path credHelper = temporaryFolder.newFile("foo.exe").toPath();
    Path pathWithoutExe = credHelper.getParent().resolve("foo");
    assertThat(credHelper).isEqualTo(pathWithoutExe.getParent().resolve("foo.exe"));

    DefaultCredentialRetrievers credentialRetrievers =
        new DefaultCredentialRetrievers(mockCredentialRetrieverFactory, properties, environment)
            .setCredentialHelper(pathWithoutExe.toString());
    Exception ex = assertThrows(FileNotFoundException.class, () -> credentialRetrievers.asList());
    assertThat(ex).hasMessageThat().startsWith("Specified credential helper was not found:");
    assertThat(ex).hasMessageThat().endsWith("foo");

    properties.setProperty("os.name", "winDOWs");
    List<CredentialRetriever> retrievers =
        new DefaultCredentialRetrievers(mockCredentialRetrieverFactory, properties, environment)
            .setCredentialHelper(pathWithoutExe.toString())
            .asList();

    assertThat(retrievers)
        .containsExactly(
            mockDockerCredentialHelperCredentialRetriever,
            mockXdgPrimaryCredentialRetriever,
            mockEnvHomeXdgCredentialRetriever,
            mockSystemHomeXdgCredentialRetriever,
            mockDockerConfigEnvDockerConfigCredentialRetriever,
            mockDockerConfigEnvKubernetesDockerConfigCredentialRetriever,
            mockDockerConfigEnvLegacyDockerConfigCredentialRetriever,
            mockSystemHomeDockerConfigCredentialRetriever,
            mockSystemHomeKubernetesDockerConfigCredentialRetriever,
            mockSystemHomeLegacyDockerConfigCredentialRetriever,
            mockEnvHomeDockerConfigCredentialRetriever,
            mockEnvHomeKubernetesDockerConfigCredentialRetriever,
            mockEnvHomeLegacyDockerConfigCredentialRetriever,
            mockWellKnownCredentialHelpersCredentialRetriever,
            mockApplicationDefaultCredentialRetriever)
        .inOrder();
  }
}
