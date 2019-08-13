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
import java.util.Optional;
import java.util.function.Consumer;
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

  private static final Credential FAKE_CREDENTIALS = Credential.from("username", "password");

  @Mock private Consumer<LogEvent> logger;
  @Mock private DockerCredentialHelper dockerCredentialHelper;
  @Mock private DockerCredentialHelperFactory dockerCredentialHelperFactory;
  @Mock private GoogleCredentials googleCredentials;

  @Before
  public void setUp()
      throws CredentialHelperUnhandledServerUrlException, CredentialHelperNotFoundException,
          IOException {
    Mockito.when(dockerCredentialHelperFactory.create(Mockito.anyString(), Mockito.any(Path.class)))
        .thenReturn(dockerCredentialHelper);
    Mockito.when(dockerCredentialHelper.retrieve()).thenReturn(FAKE_CREDENTIALS);
    Mockito.when(googleCredentials.getAccessToken()).thenReturn(new AccessToken("my-token", null));
  }

  @Test
  public void testDockerCredentialHelper() throws CredentialRetrievalException {
    CredentialRetrieverFactory credentialRetrieverFactory =
        createCredentialRetrieverFactory("registry", "repository");

    Assert.assertEquals(
        Optional.of(FAKE_CREDENTIALS),
        credentialRetrieverFactory
            .dockerCredentialHelper(Paths.get("docker-credential-helper"))
            .retrieve());

    Mockito.verify(dockerCredentialHelperFactory)
        .create("registry", Paths.get("docker-credential-helper"));
    Mockito.verify(logger)
        .accept(LogEvent.info("Using credentials from docker-credential-helper for registry"));
  }

  @Test
  public void testInferCredentialHelper() throws CredentialRetrievalException {
    CredentialRetrieverFactory credentialRetrieverFactory =
        createCredentialRetrieverFactory("something.gcr.io", "repository");

    Assert.assertEquals(
        Optional.of(FAKE_CREDENTIALS),
        credentialRetrieverFactory.wellKnownCredentialHelper().retrieve());

    Mockito.verify(dockerCredentialHelperFactory)
        .create("something.gcr.io", Paths.get("docker-credential-gcr"));
    Mockito.verify(logger)
        .accept(LogEvent.info("Using credentials from docker-credential-gcr for something.gcr.io"));
  }

  @Test
  public void testInferCredentialHelper_info() throws CredentialRetrievalException, IOException {
    CredentialHelperNotFoundException notFoundException =
        Mockito.mock(CredentialHelperNotFoundException.class);
    Mockito.when(notFoundException.getMessage()).thenReturn("warning");
    Mockito.when(notFoundException.getCause()).thenReturn(new IOException("the root cause"));
    Mockito.when(dockerCredentialHelper.retrieve()).thenThrow(notFoundException);

    CredentialRetrieverFactory credentialRetrieverFactory =
        createCredentialRetrieverFactory("something.amazonaws.com", "repository");

    Assert.assertFalse(
        credentialRetrieverFactory.wellKnownCredentialHelper().retrieve().isPresent());

    Mockito.verify(dockerCredentialHelperFactory)
        .create("something.amazonaws.com", Paths.get("docker-credential-ecr-login"));
    Mockito.verify(logger).accept(LogEvent.info("warning"));
    Mockito.verify(logger).accept(LogEvent.info("  Caused by: the root cause"));
  }

  @Test
  public void testDockerConfig() throws IOException, CredentialRetrievalException {
    CredentialRetrieverFactory credentialRetrieverFactory =
        createCredentialRetrieverFactory("registry", "repository");

    DockerConfigCredentialRetriever dockerConfigCredentialRetriever =
        Mockito.mock(DockerConfigCredentialRetriever.class);
    Mockito.when(dockerConfigCredentialRetriever.retrieve(logger))
        .thenReturn(Optional.of(FAKE_CREDENTIALS));

    Assert.assertEquals(
        Optional.of(FAKE_CREDENTIALS),
        credentialRetrieverFactory.dockerConfig(dockerConfigCredentialRetriever).retrieve());

    Mockito.verify(logger)
        .accept(LogEvent.info("Using credentials from Docker config for registry"));
  }

  @Test
  public void testGoogleApplicationDefaultCredentials_notGoogleContainerRegistry()
      throws CredentialRetrievalException {
    CredentialRetrieverFactory credentialRetrieverFactory =
        createCredentialRetrieverFactory("non.gcr.registry", "repository");

    Assert.assertFalse(
        credentialRetrieverFactory.googleApplicationDefaultCredentials().retrieve().isPresent());

    Mockito.verifyZeroInteractions(logger);
  }

  @Test
  public void testGoogleApplicationDefaultCredentials_adcNotPresent()
      throws CredentialRetrievalException {
    CredentialRetrieverFactory credentialRetrieverFactory =
        new CredentialRetrieverFactory(
            ImageReference.of("awesome.gcr.io", "repository", null),
            logger,
            dockerCredentialHelperFactory,
            () -> {
              throw new IOException("ADC not present");
            });

    Assert.assertFalse(
        credentialRetrieverFactory.googleApplicationDefaultCredentials().retrieve().isPresent());

    Mockito.verify(logger)
        .accept(LogEvent.info("ADC not present or error fetching access token: ADC not present"));
  }

  @Test
  public void testGoogleApplicationDefaultCredentials_refreshFailure()
      throws CredentialRetrievalException, IOException {
    Mockito.doThrow(new IOException("refresh failed")).when(googleCredentials).refresh();

    CredentialRetrieverFactory credentialRetrieverFactory =
        createCredentialRetrieverFactory("awesome.gcr.io", "repository");

    Assert.assertFalse(
        credentialRetrieverFactory.googleApplicationDefaultCredentials().retrieve().isPresent());

    Mockito.verify(logger).accept(LogEvent.info("Google ADC found"));
    Mockito.verify(logger)
        .accept(LogEvent.info("ADC not present or error fetching access token: refresh failed"));
    Mockito.verifyNoMoreInteractions(logger);
  }

  @Test
  public void testGoogleApplicationDefaultCredentials_endUserCredentials()
      throws CredentialRetrievalException {
    CredentialRetrieverFactory credentialRetrieverFactory =
        createCredentialRetrieverFactory("awesome.gcr.io", "repository");

    Credential credential =
        credentialRetrieverFactory.googleApplicationDefaultCredentials().retrieve().get();
    Assert.assertEquals("oauth2accesstoken", credential.getUsername());
    Assert.assertEquals("my-token", credential.getPassword());

    Mockito.verify(googleCredentials, Mockito.never()).createScoped(Mockito.anyString());

    Mockito.verify(logger).accept(LogEvent.info("Google ADC found"));
    Mockito.verify(logger)
        .accept(LogEvent.info("Using Google Application Default Credentials for awesome.gcr.io"));
    Mockito.verifyNoMoreInteractions(logger);
  }

  @Test
  public void testGoogleApplicationDefaultCredentials_serviceAccount()
      throws CredentialRetrievalException {
    Mockito.when(googleCredentials.createScopedRequired()).thenReturn(true);
    Mockito.when(googleCredentials.createScoped(Mockito.anyCollection()))
        .thenReturn(googleCredentials);

    CredentialRetrieverFactory credentialRetrieverFactory =
        createCredentialRetrieverFactory("gcr.io", "repository");

    Credential credential =
        credentialRetrieverFactory.googleApplicationDefaultCredentials().retrieve().get();
    Assert.assertEquals("oauth2accesstoken", credential.getUsername());
    Assert.assertEquals("my-token", credential.getPassword());

    Mockito.verify(googleCredentials)
        .createScoped(
            Collections.singletonList("https://www.googleapis.com/auth/devstorage.read_write"));

    Mockito.verify(logger).accept(LogEvent.info("Google ADC found"));
    Mockito.verify(logger)
        .accept(LogEvent.info("ADC is a service account. Set GCS read-write scope"));
    Mockito.verify(logger)
        .accept(LogEvent.info("Using Google Application Default Credentials for gcr.io"));
    Mockito.verifyNoMoreInteractions(logger);
  }

  private CredentialRetrieverFactory createCredentialRetrieverFactory(
      String registry, String repository) {
    ImageReference imageReference = ImageReference.of(registry, repository, null);
    return new CredentialRetrieverFactory(
        imageReference, logger, dockerCredentialHelperFactory, () -> googleCredentials);
  }
}
