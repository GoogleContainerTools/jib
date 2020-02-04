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

package com.google.cloud.tools.jib.registry;

import com.google.cloud.tools.jib.api.Credential;
import com.google.cloud.tools.jib.api.DescriptorDigest;
import com.google.cloud.tools.jib.api.LogEvent;
import com.google.cloud.tools.jib.api.RegistryException;
import com.google.cloud.tools.jib.api.RegistryUnauthorizedException;
import com.google.cloud.tools.jib.blob.BlobDescriptor;
import com.google.cloud.tools.jib.event.EventHandlers;
import com.google.cloud.tools.jib.global.JibSystemProperties;
import com.google.cloud.tools.jib.http.TestWebServer;
import java.io.IOException;
import java.net.URISyntaxException;
import java.security.DigestException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import org.hamcrest.CoreMatchers;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.RestoreSystemProperties;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Tests for {@link RegistryClient}. More comprehensive tests can be found in the integration tests.
 */
@RunWith(MockitoJUnitRunner.class)
public class RegistryClientTest {

  @Rule public final RestoreSystemProperties systemPropertyRestorer = new RestoreSystemProperties();

  @Mock private EventHandlers eventHandlers;

  private RegistryClient.Factory testRegistryClientFactory;
  private DescriptorDigest digest;

  private TestWebServer registry;
  private TestWebServer authServer;

  @Before
  public void setUp() throws DigestException {
    testRegistryClientFactory =
        RegistryClient.factory(EventHandlers.NONE, "some.server.url", "some image name", null);
    digest =
        DescriptorDigest.fromHash(
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
  }

  @After
  public void tearDown() throws IOException {
    if (registry != null) {
      registry.close();
    }
    if (authServer != null) {
      authServer.close();
    }
  }

  @Test
  public void testGetUserAgent_null() {
    Assert.assertTrue(
        testRegistryClientFactory.newRegistryClient().getUserAgent().startsWith("jib"));

    Assert.assertTrue(
        testRegistryClientFactory
            .setUserAgentSuffix(null)
            .newRegistryClient()
            .getUserAgent()
            .startsWith("jib"));
  }

  @Test
  public void testGetUserAgent() {
    RegistryClient registryClient =
        testRegistryClientFactory.setUserAgentSuffix("some user agent suffix").newRegistryClient();

    Assert.assertTrue(registryClient.getUserAgent().startsWith("jib "));
    Assert.assertTrue(registryClient.getUserAgent().endsWith(" some user agent suffix"));
  }

  @Test
  public void testGetUserAgentWithUpstreamClient() {
    System.setProperty(JibSystemProperties.UPSTREAM_CLIENT, "skaffold/0.34.0");

    RegistryClient registryClient =
        testRegistryClientFactory.setUserAgentSuffix("foo").newRegistryClient();
    Assert.assertTrue(registryClient.getUserAgent().startsWith("jib "));
    Assert.assertTrue(registryClient.getUserAgent().endsWith(" skaffold/0.34.0"));
  }

  @Test
  public void testDoBearerAuth_returnsFalseOnBasicAuth()
      throws IOException, InterruptedException, GeneralSecurityException, URISyntaxException,
          RegistryException {
    String basicAuth =
        "HTTP/1.1 401 Unauthorized\nContent-Length: 0\nWWW-Authenticate: Basic foo\n\n";
    registry = new TestWebServer(false, Arrays.asList(basicAuth), 1);

    RegistryClient registryClient = createRegistryClient(null);
    Assert.assertFalse(registryClient.doPullBearerAuth());

    Mockito.verify(eventHandlers).dispatch(logContains("attempting bearer auth"));
    Mockito.verify(eventHandlers).dispatch(logContains("server requires basic auth"));
  }

  @Test
  public void testDoBearerAuth()
      throws IOException, InterruptedException, GeneralSecurityException, URISyntaxException,
          RegistryException {
    setUpAuthServerAndRegistry(1, "HTTP/1.1 200 OK\nContent-Length: 1234\n\n");

    RegistryClient registryClient = createRegistryClient(null);
    Assert.assertTrue(registryClient.doPushBearerAuth());

    Optional<BlobDescriptor> digestAndSize = registryClient.checkBlob(digest);
    Assert.assertEquals(1234, digestAndSize.get().getSize());

    Mockito.verify(eventHandlers).dispatch(logContains("attempting bearer auth"));
    Mockito.verify(eventHandlers).dispatch(logContains("bearer auth succeeded"));
  }

  @Test
  public void testAutomaticTokenRefresh()
      throws IOException, InterruptedException, GeneralSecurityException, URISyntaxException,
          RegistryException {
    setUpAuthServerAndRegistry(3, "HTTP/1.1 200 OK\nContent-Length: 5678\n\n");

    RegistryClient registryClient = createRegistryClient(null);
    Assert.assertTrue(registryClient.doPushBearerAuth());

    Optional<BlobDescriptor> digestAndSize = registryClient.checkBlob(digest);
    Assert.assertEquals(5678, digestAndSize.get().getSize());

    // Verify authServer returned bearer token three times (i.e., refreshed twice)
    Assert.assertEquals(3, authServer.getTotalResponsesServed());
    Assert.assertEquals(4, registry.getTotalResponsesServed());

    Mockito.verify(eventHandlers).dispatch(logContains("attempting bearer auth"));
    Mockito.verify(eventHandlers).dispatch(logContains("bearer auth succeeded"));
    Mockito.verify(eventHandlers, Mockito.times(2))
        .dispatch(logContains("refreshing bearer auth token"));
  }

  @Test
  public void testAutomaticTokenRefresh_badWwwAuthenticateResponse()
      throws IOException, InterruptedException, GeneralSecurityException, URISyntaxException,
          RegistryException {
    String tokenResponse = "HTTP/1.1 200 OK\nContent-Length: 26\n\n{\"token\":\"awesome-token!\"}";
    authServer = new TestWebServer(false, Arrays.asList(tokenResponse), 3);

    List<String> responses =
        Arrays.asList(
            "HTTP/1.1 401 Unauthorized\nContent-Length: 0\nWWW-Authenticate: Bearer realm=\""
                + authServer.getEndpoint()
                + "\"\n\n",
            "HTTP/1.1 401 Unauthorized\nContent-Length: 0\nWWW-Authenticate: Basic realm=foo\n\n",
            "HTTP/1.1 401 Unauthorized\nContent-Length: 0\n\n",
            "HTTP/1.1 200 OK\nContent-Length: 5678\n\n");
    registry = new TestWebServer(false, responses, responses.size(), true);

    RegistryClient registryClient = createRegistryClient(null);
    Assert.assertTrue(registryClient.doPushBearerAuth());

    Optional<BlobDescriptor> digestAndSize = registryClient.checkBlob(digest);
    Assert.assertEquals(5678, digestAndSize.get().getSize());

    // Verify authServer returned bearer token three times (i.e., refreshed twice)
    Assert.assertEquals(3, authServer.getTotalResponsesServed());
    Assert.assertEquals(4, registry.getTotalResponsesServed());

    Mockito.verify(eventHandlers)
        .dispatch(
            logContains("server did not return 'WWW-Authenticate: Bearer' header. Actual: Basic"));
    Mockito.verify(eventHandlers)
        .dispatch(
            logContains("server did not return 'WWW-Authenticate: Bearer' header. Actual: null"));
  }

  @Test
  public void testAutomaticTokenRefresh_refreshLimit()
      throws IOException, InterruptedException, GeneralSecurityException, URISyntaxException,
          RegistryException {
    String tokenResponse = "HTTP/1.1 200 OK\nContent-Length: 26\n\n{\"token\":\"awesome-token!\"}";
    authServer = new TestWebServer(false, Arrays.asList(tokenResponse), 5);

    String bearerAuth =
        "HTTP/1.1 401 Unauthorized\nContent-Length: 0\nWWW-Authenticate: Bearer realm=\""
            + authServer.getEndpoint()
            + "\"\n\n";
    String unauthorized = "HTTP/1.1 401 Unauthorized\nContent-Length: 0\n\n";
    List<String> responses =
        Arrays.asList(
            bearerAuth, unauthorized, unauthorized, unauthorized, unauthorized, unauthorized);
    registry = new TestWebServer(false, responses, responses.size(), true);

    RegistryClient registryClient = createRegistryClient(null);
    Assert.assertTrue(registryClient.doPushBearerAuth());

    try {
      registryClient.checkBlob(digest);
      Assert.fail("Should have given up refreshing after 4 attempts");
    } catch (RegistryUnauthorizedException ex) {
      Assert.assertEquals(401, ex.getHttpResponseException().getStatusCode());
      Assert.assertEquals(5, authServer.getTotalResponsesServed());
      // 1 response asking to do bearer auth + 4 unauth responses for 4 refresh attempts + 1 final
      // unauth response propagated as RegistryUnauthorizedException here
      Assert.assertEquals(6, registry.getTotalResponsesServed());
    }
  }

  @Test
  public void testConfigureBasicAuth()
      throws IOException, InterruptedException, GeneralSecurityException, URISyntaxException,
          RegistryException {
    String basicAuth = "HTTP/1.1 200 OK\nContent-Length: 56789\n\n";
    registry = new TestWebServer(false, Arrays.asList(basicAuth), 1);
    RegistryClient registryClient = createRegistryClient(Credential.from("user", "pass"));
    registryClient.configureBasicAuth();

    Optional<BlobDescriptor> digestAndSize = registryClient.checkBlob(digest);
    Assert.assertEquals(56789, digestAndSize.get().getSize());
    Assert.assertThat(
        registry.getInputRead(), CoreMatchers.containsString("Authorization: Basic dXNlcjpwYXNz"));
  }

  /**
   * Sets up an auth server and a registry. The auth server can return a bearer token up to {@code
   * maxAuthTokens} times. The registry will initially return "401 Unauthorized" for {@code
   * maxTokenResponses} times. (Therefore, a registry client has to get auth tokens from the auth
   * server {@code maxAuthTokens} times. After that, the registry returns {@code finalResponse}.
   */
  private void setUpAuthServerAndRegistry(int maxAuthTokens, @Nullable String finalResponse)
      throws IOException, InterruptedException, GeneralSecurityException, URISyntaxException {
    String tokenResponse = "HTTP/1.1 200 OK\nContent-Length: 26\n\n{\"token\":\"awesome-token!\"}";
    authServer = new TestWebServer(false, Arrays.asList(tokenResponse), maxAuthTokens);

    String bearerAuth =
        "HTTP/1.1 401 Unauthorized\nContent-Length: 0\nWWW-Authenticate: Bearer realm=\""
            + authServer.getEndpoint()
            + "\"\n\n";
    List<String> responses = new ArrayList<>(Collections.nCopies(maxAuthTokens, bearerAuth));
    if (finalResponse != null) {
      responses.add(finalResponse);
    }

    registry = new TestWebServer(false, responses, responses.size(), true);
  }

  private RegistryClient createRegistryClient(@Nullable Credential credential) {
    return RegistryClient.factory(
            eventHandlers, "localhost:" + registry.getLocalPort(), "foo/bar", new PlainHttpClient())
        .setCredential(credential)
        .newRegistryClient();
  }

  private LogEvent logContains(String substring) {
    ArgumentMatcher<LogEvent> matcher = event -> event.getMessage().contains(substring);
    return ArgumentMatchers.argThat(matcher);
  }
}
