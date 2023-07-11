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

package com.google.cloud.tools.jib.frontend;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.tools.jib.api.Credential;
import com.google.cloud.tools.jib.api.ImageReference;
import com.google.cloud.tools.jib.api.LogEvent;
import com.google.cloud.tools.jib.frontend.CredentialRetrieverFactory.DockerCredentialHelperFactory;
import com.google.cloud.tools.jib.registry.credentials.CredentialHelperNotFoundException;
import com.google.cloud.tools.jib.registry.credentials.CredentialHelperUnhandledServerUrlException;
import com.google.cloud.tools.jib.registry.credentials.CredentialRetrievalException;
import com.google.cloud.tools.jib.registry.credentials.DockerConfigCredentialRetriever;
import com.google.cloud.tools.jib.registry.credentials.DockerCredentialHelper;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** Tests for {@link CredentialRetrieverFactory}. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CredentialRetrieverFactoryTest {

  private static final Credential FAKE_CREDENTIALS = Credential.from("username", "password");

  @Mock private Consumer<LogEvent> mockLogger;
  @Mock private DockerCredentialHelper mockDockerCredentialHelper;
  @Mock private DockerCredentialHelperFactory mockDockerCredentialHelperFactory;
  @Mock private GoogleCredentials mockGoogleCredentials;

  @BeforeEach
  void setUp()
      throws CredentialHelperUnhandledServerUrlException, CredentialHelperNotFoundException,
          IOException {
    Mockito.when(
            mockDockerCredentialHelperFactory.create(
                Mockito.anyString(), Mockito.any(Path.class), Mockito.anyMap()))
        .thenReturn(mockDockerCredentialHelper);
    Mockito.when(mockDockerCredentialHelper.retrieve()).thenReturn(FAKE_CREDENTIALS);
    Mockito.when(mockGoogleCredentials.getAccessToken())
        .thenReturn(new AccessToken("my-token", null));
  }

  @Test
  void testDockerCredentialHelper() throws CredentialRetrievalException {
    CredentialRetrieverFactory credentialRetrieverFactory =
        createCredentialRetrieverFactory("registry", "repo");

    Assert.assertEquals(
        Optional.of(FAKE_CREDENTIALS),
        credentialRetrieverFactory
            .dockerCredentialHelper(Paths.get("docker-credential-foo"))
            .retrieve());

    Mockito.verify(mockDockerCredentialHelperFactory)
        .create("registry", Paths.get("docker-credential-foo"), Collections.emptyMap());
    Mockito.verify(mockLogger)
        .accept(
            LogEvent.lifecycle("Using credential helper docker-credential-foo for registry/repo"));
  }

  @Test
  void testDockerCredentialHelperWithEnvironment() throws CredentialRetrievalException {
    Map<String, String> environment = Collections.singletonMap("ENV_VARIABLE", "Value");
    CredentialRetrieverFactory credentialRetrieverFactory =
        createCredentialRetrieverFactory("registry", "repo", environment);

    Assert.assertEquals(
        Optional.of(FAKE_CREDENTIALS),
        credentialRetrieverFactory
            .dockerCredentialHelper(Paths.get("docker-credential-foo"))
            .retrieve());

    Mockito.verify(mockDockerCredentialHelperFactory)
        .create("registry", Paths.get("docker-credential-foo"), environment);
    Mockito.verify(mockLogger)
        .accept(
            LogEvent.lifecycle("Using credential helper docker-credential-foo for registry/repo"));
  }

  @Test
  void testWellKnownCredentialHelpers() throws CredentialRetrievalException {
    CredentialRetrieverFactory credentialRetrieverFactory =
        createCredentialRetrieverFactory("something.gcr.io", "repo");

    Assert.assertEquals(
        Optional.of(FAKE_CREDENTIALS),
        credentialRetrieverFactory.wellKnownCredentialHelpers().retrieve());

    Mockito.verify(mockDockerCredentialHelperFactory)
        .create("something.gcr.io", Paths.get("docker-credential-gcr"), Collections.emptyMap());
    Mockito.verify(mockLogger)
        .accept(
            LogEvent.lifecycle(
                "Using credential helper docker-credential-gcr for something.gcr.io/repo"));
  }

  @Test
  void testWellKnownCredentialHelpers_info() throws CredentialRetrievalException, IOException {
    CredentialHelperNotFoundException notFoundException =
        Mockito.mock(CredentialHelperNotFoundException.class);
    Mockito.when(notFoundException.getMessage()).thenReturn("warning");
    Mockito.when(notFoundException.getCause()).thenReturn(new IOException("the root cause"));
    Mockito.when(mockDockerCredentialHelper.retrieve()).thenThrow(notFoundException);

    CredentialRetrieverFactory credentialRetrieverFactory =
        createCredentialRetrieverFactory("something.amazonaws.com", "repo");

    Assert.assertFalse(
        credentialRetrieverFactory.wellKnownCredentialHelpers().retrieve().isPresent());

    Mockito.verify(mockDockerCredentialHelperFactory)
        .create(
            "something.amazonaws.com",
            Paths.get("docker-credential-ecr-login"),
            Collections.emptyMap());
    Mockito.verify(mockLogger).accept(LogEvent.info("warning"));
    Mockito.verify(mockLogger).accept(LogEvent.info("  Caused by: the root cause"));
  }

  @Test
  void testDockerConfig() throws IOException, CredentialRetrievalException {
    CredentialRetrieverFactory credentialRetrieverFactory =
        createCredentialRetrieverFactory("registry", "repo");

    Path dockerConfig = Paths.get("/foo/config.json");
    DockerConfigCredentialRetriever dockerConfigCredentialRetriever =
        Mockito.mock(DockerConfigCredentialRetriever.class);
    Mockito.when(dockerConfigCredentialRetriever.retrieve(mockLogger))
        .thenReturn(Optional.of(FAKE_CREDENTIALS));
    Mockito.when(dockerConfigCredentialRetriever.getDockerConfigFile()).thenReturn(dockerConfig);

    Assert.assertEquals(
        Optional.of(FAKE_CREDENTIALS),
        credentialRetrieverFactory.dockerConfig(dockerConfigCredentialRetriever).retrieve());

    Mockito.verify(mockLogger)
        .accept(
            LogEvent.lifecycle(
                "Using credentials from Docker config (" + dockerConfig + ") for registry/repo"));
  }

  @Test
  void testGoogleApplicationDefaultCredentials_notGoogleContainerRegistry()
      throws CredentialRetrievalException {
    CredentialRetrieverFactory credentialRetrieverFactory =
        createCredentialRetrieverFactory("non.gcr.registry", "repository");

    Assert.assertFalse(
        credentialRetrieverFactory.googleApplicationDefaultCredentials().retrieve().isPresent());

    Mockito.verifyNoInteractions(mockLogger);
  }

  @Test
  void testGoogleApplicationDefaultCredentials_adcNotPresent() throws CredentialRetrievalException {
    CredentialRetrieverFactory credentialRetrieverFactory =
        new CredentialRetrieverFactory(
            ImageReference.of("awesome.gcr.io", "repository", null),
            mockLogger,
            mockDockerCredentialHelperFactory,
            () -> {
              throw new IOException("ADC not present");
            },
            Collections.emptyMap());

    Assert.assertFalse(
        credentialRetrieverFactory.googleApplicationDefaultCredentials().retrieve().isPresent());

    Mockito.verify(mockLogger)
        .accept(LogEvent.info("ADC not present or error fetching access token: ADC not present"));
  }

  @Test
  void testGoogleApplicationDefaultCredentials_refreshFailure()
      throws CredentialRetrievalException, IOException {
    Mockito.doThrow(new IOException("refresh failed"))
        .when(mockGoogleCredentials)
        .refreshIfExpired();

    CredentialRetrieverFactory credentialRetrieverFactory =
        createCredentialRetrieverFactory("awesome.gcr.io", "repository");

    Assert.assertFalse(
        credentialRetrieverFactory.googleApplicationDefaultCredentials().retrieve().isPresent());

    Mockito.verify(mockLogger).accept(LogEvent.info("Google ADC found"));
    Mockito.verify(mockLogger)
        .accept(LogEvent.info("ADC not present or error fetching access token: refresh failed"));
    Mockito.verifyNoMoreInteractions(mockLogger);
  }

  @Test
  void testGoogleApplicationDefaultCredentials_endUserCredentials()
      throws CredentialRetrievalException {
    CredentialRetrieverFactory credentialRetrieverFactory =
        createCredentialRetrieverFactory("awesome.gcr.io", "repo");

    Credential credential =
        credentialRetrieverFactory.googleApplicationDefaultCredentials().retrieve().get();
    Assert.assertEquals("oauth2accesstoken", credential.getUsername());
    Assert.assertEquals("my-token", credential.getPassword());

    Mockito.verify(mockGoogleCredentials, Mockito.never()).createScoped(Mockito.anyString());

    Mockito.verify(mockLogger).accept(LogEvent.info("Google ADC found"));
    Mockito.verify(mockLogger)
        .accept(
            LogEvent.lifecycle(
                "Using Google Application Default Credentials for awesome.gcr.io/repo"));
    Mockito.verifyNoMoreInteractions(mockLogger);
  }

  @Test
  void testGoogleApplicationDefaultCredentials_endUserCredentials_artifactRegistry()
      throws CredentialRetrievalException {
    CredentialRetrieverFactory credentialRetrieverFactory =
        createCredentialRetrieverFactory("us-docker.pkg.dev", "my-project/repo/my-app");

    Credential credential =
        credentialRetrieverFactory.googleApplicationDefaultCredentials().retrieve().get();
    Assert.assertEquals("oauth2accesstoken", credential.getUsername());
    Assert.assertEquals("my-token", credential.getPassword());

    Mockito.verify(mockGoogleCredentials, Mockito.never()).createScoped(Mockito.anyString());

    Mockito.verify(mockLogger).accept(LogEvent.info("Google ADC found"));
    Mockito.verify(mockLogger)
        .accept(
            LogEvent.lifecycle(
                "Using Google Application Default Credentials for "
                    + "us-docker.pkg.dev/my-project/repo/my-app"));
    Mockito.verifyNoMoreInteractions(mockLogger);
  }

  @Test
  void testGoogleApplicationDefaultCredentials_serviceAccount()
      throws CredentialRetrievalException {
    Mockito.when(mockGoogleCredentials.createScopedRequired()).thenReturn(true);
    Mockito.when(mockGoogleCredentials.createScoped(Mockito.anyCollection()))
        .thenReturn(mockGoogleCredentials);

    CredentialRetrieverFactory credentialRetrieverFactory =
        createCredentialRetrieverFactory("gcr.io", "repo");

    Credential credential =
        credentialRetrieverFactory.googleApplicationDefaultCredentials().retrieve().get();
    Assert.assertEquals("oauth2accesstoken", credential.getUsername());
    Assert.assertEquals("my-token", credential.getPassword());

    Mockito.verify(mockGoogleCredentials)
        .createScoped(
            Collections.singletonList("https://www.googleapis.com/auth/devstorage.read_write"));

    Mockito.verify(mockLogger).accept(LogEvent.info("Google ADC found"));
    Mockito.verify(mockLogger)
        .accept(LogEvent.info("ADC is a service account. Setting GCS read-write scope"));
    Mockito.verify(mockLogger)
        .accept(LogEvent.lifecycle("Using Google Application Default Credentials for gcr.io/repo"));
    Mockito.verifyNoMoreInteractions(mockLogger);
  }

  private CredentialRetrieverFactory createCredentialRetrieverFactory(
      String registry, String repository) {
    return createCredentialRetrieverFactory(registry, repository, Collections.emptyMap());
  }

  private CredentialRetrieverFactory createCredentialRetrieverFactory(
      String registry, String repository, Map<String, String> environment) {
    ImageReference imageReference = ImageReference.of(registry, repository, null);
    return new CredentialRetrieverFactory(
        imageReference,
        mockLogger,
        mockDockerCredentialHelperFactory,
        () -> mockGoogleCredentials,
        environment);
  }
}
