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
import com.google.cloud.tools.jib.http.TestWebServer;
import com.google.cloud.tools.jib.image.json.V22ManifestListTemplate;
import com.google.cloud.tools.jib.image.json.V22ManifestTemplate;
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
import org.hamcrest.MatcherAssert;
import org.junit.Assert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatcher;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Tests for {@link RegistryClient}. More comprehensive tests can be found in the integration tests.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RegistryClientTest {

  @Mock private EventHandlers eventHandlers;

  private DescriptorDigest digest;

  private TestWebServer registry;
  private TestWebServer authServer;

  @BeforeEach
  void setUp() throws DigestException {
    digest =
        DescriptorDigest.fromHash(
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
  }

  @AfterEach
  void tearDown() throws IOException {
    if (registry != null) {
      registry.close();
    }
    if (authServer != null) {
      authServer.close();
    }
  }

  @Test
  void testDoBearerAuth_returnsFalseOnBasicAuth()
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
  void testDoBearerAuth()
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
  void testAutomaticTokenRefresh()
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
  void testAutomaticTokenRefresh_badWwwAuthenticateResponse()
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
  void testAutomaticTokenRefresh_refreshLimit()
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
  void testConfigureBasicAuth()
      throws IOException, InterruptedException, GeneralSecurityException, URISyntaxException,
          RegistryException {
    String basicAuth = "HTTP/1.1 200 OK\nContent-Length: 56789\n\n";
    registry = new TestWebServer(false, Arrays.asList(basicAuth), 1);
    RegistryClient registryClient = createRegistryClient(Credential.from("user", "pass"));
    registryClient.configureBasicAuth();

    Optional<BlobDescriptor> digestAndSize = registryClient.checkBlob(digest);
    Assert.assertEquals(56789, digestAndSize.get().getSize());
    MatcherAssert.assertThat(
        registry.getInputRead(), CoreMatchers.containsString("Authorization: Basic dXNlcjpwYXNz"));
  }

  @Test
  void testAuthPullByWwwAuthenticate_bearerAuth()
      throws IOException, InterruptedException, GeneralSecurityException, URISyntaxException,
          RegistryException {
    String tokenResponse = "HTTP/1.1 200 OK\nContent-Length: 26\n\n{\"token\":\"awesome-token!\"}";
    authServer = new TestWebServer(false, Arrays.asList(tokenResponse), 1);

    String blobResponse = "HTTP/1.1 200 OK\nContent-Length: 5678\n\n";
    registry = new TestWebServer(false, Arrays.asList(blobResponse), 1);

    RegistryClient registryClient = createRegistryClient(Credential.from("user", "pass"));
    registryClient.authPullByWwwAuthenticate("Bearer realm=\"" + authServer.getEndpoint() + "\"");

    Optional<BlobDescriptor> digestAndSize = registryClient.checkBlob(digest);
    Assert.assertEquals(5678, digestAndSize.get().getSize());

    Mockito.verify(eventHandlers).dispatch(logContains("bearer auth succeeded"));
  }

  @Test
  void testAuthPullByWwwAuthenticate_basicAuth()
      throws IOException, InterruptedException, GeneralSecurityException, URISyntaxException,
          RegistryException {
    String blobResponse = "HTTP/1.1 200 OK\nContent-Length: 5678\n\n";
    registry = new TestWebServer(false, Arrays.asList(blobResponse), 1);

    RegistryClient registryClient = createRegistryClient(Credential.from("user", "pass"));
    registryClient.authPullByWwwAuthenticate("Basic foo");

    Optional<BlobDescriptor> digestAndSize = registryClient.checkBlob(digest);
    Assert.assertEquals(5678, digestAndSize.get().getSize());

    MatcherAssert.assertThat(
        registry.getInputRead(), CoreMatchers.containsString("Authorization: Basic dXNlcjpwYXNz"));
  }

  @Test
  void testAuthPullByWwwAuthenticate_basicAuthRequestedButNullCredential()
      throws IOException, InterruptedException, GeneralSecurityException, URISyntaxException,
          RegistryException {
    String blobResponse = "HTTP/1.1 200 OK\nContent-Length: 5678\n\n";
    registry = new TestWebServer(false, Arrays.asList(blobResponse), 1);

    RegistryClient registryClient = createRegistryClient(null);
    registryClient.authPullByWwwAuthenticate("Basic foo");

    Optional<BlobDescriptor> digestAndSize = registryClient.checkBlob(digest);
    Assert.assertEquals(5678, digestAndSize.get().getSize());

    MatcherAssert.assertThat(
        registry.getInputRead(), CoreMatchers.not(CoreMatchers.containsString("Authorization:")));
  }

  @Test
  void testAuthPullByWwwAuthenticate_basicAuthRequestedButOAuth2Credential()
      throws IOException, InterruptedException, GeneralSecurityException, URISyntaxException,
          RegistryException {
    String blobResponse = "HTTP/1.1 200 OK\nContent-Length: 5678\n\n";
    registry = new TestWebServer(false, Arrays.asList(blobResponse), 1);

    Credential credential = Credential.from(Credential.OAUTH2_TOKEN_USER_NAME, "pass");
    Assert.assertTrue(credential.isOAuth2RefreshToken());
    RegistryClient registryClient = createRegistryClient(credential);
    registryClient.authPullByWwwAuthenticate("Basic foo");

    Optional<BlobDescriptor> digestAndSize = registryClient.checkBlob(digest);
    Assert.assertEquals(5678, digestAndSize.get().getSize());

    MatcherAssert.assertThat(
        registry.getInputRead(), CoreMatchers.not(CoreMatchers.containsString("Authorization:")));
  }

  @Test
  void testAuthPullByWwwAuthenticate_invalidAuthMethod() {
    RegistryClient registryClient =
        RegistryClient.factory(eventHandlers, "server", "foo/bar", null).newRegistryClient();
    try {
      registryClient.authPullByWwwAuthenticate("invalid WWW-Authenticate");
      Assert.fail();
    } catch (RegistryException ex) {
      Assert.assertEquals(
          "Failed to authenticate with registry server/foo/bar because: 'Bearer' was not found in "
              + "the 'WWW-Authenticate' header, tried to parse: invalid WWW-Authenticate",
          ex.getMessage());
    }
  }

  @Test
  void testPullManifest()
      throws IOException, InterruptedException, GeneralSecurityException, URISyntaxException,
          RegistryException {
    String manifestResponse =
        "HTTP/1.1 200 OK\nContent-Length: 307\n\n{\n"
            + "    \"schemaVersion\": 2,\n"
            + "    \"mediaType\": \"application/vnd.docker.distribution.manifest.v2+json\",\n"
            + "    \"config\": {\n"
            + "        \"mediaType\": \"application/vnd.docker.container.image.v1+json\",\n"
            + "        \"size\": 7023,\n"
            + "        \"digest\": \"sha256:b5b2b2c507a0944348e0303114d8d93aaaa081732b86451d9bce1f432a537bc7\"\n"
            + "    }\n"
            + "}";

    registry = new TestWebServer(false, Arrays.asList(manifestResponse), 1);
    RegistryClient registryClient = createRegistryClient(null);
    ManifestAndDigest<?> manifestAndDigest = registryClient.pullManifest("image-tag");

    Assert.assertEquals(
        "sha256:6b61466eabab6e5ffb68ae2bd9b85c789225540c2ac54ea1f71eb327588e8946",
        manifestAndDigest.getDigest().toString());

    Assert.assertTrue(manifestAndDigest.getManifest() instanceof V22ManifestTemplate);
    V22ManifestTemplate manifest = (V22ManifestTemplate) manifestAndDigest.getManifest();
    Assert.assertEquals(2, manifest.getSchemaVersion());
    Assert.assertEquals(
        "application/vnd.docker.distribution.manifest.v2+json", manifest.getManifestMediaType());
    Assert.assertEquals(
        "sha256:b5b2b2c507a0944348e0303114d8d93aaaa081732b86451d9bce1f432a537bc7",
        manifest.getContainerConfiguration().getDigest().toString());
    Assert.assertEquals(7023, manifest.getContainerConfiguration().getSize());

    MatcherAssert.assertThat(
        registry.getInputRead(),
        CoreMatchers.containsString("GET /v2/foo/bar/manifests/image-tag "));
  }

  @Test
  void testPullManifest_manifestList()
      throws IOException, InterruptedException, GeneralSecurityException, URISyntaxException,
          RegistryException {
    String manifestResponse =
        "HTTP/1.1 200 OK\nContent-Length: 403\n\n{\n"
            + "  \"schemaVersion\": 2,\n"
            + "  \"mediaType\": \"application/vnd.docker.distribution.manifest.list.v2+json\",\n"
            + "  \"manifests\": [\n"
            + "    {\n"
            + "      \"mediaType\": \"application/vnd.docker.distribution.manifest.v2+json\",\n"
            + "      \"size\": 7143,\n"
            + "      \"digest\": \"sha256:e692418e4cbaf90ca69d05a66403747baa33ee08806650b51fab815ad7fc331f\",\n"
            + "      \"platform\": {\n"
            + "        \"architecture\": \"amd64\",\n"
            + "        \"os\": \"linux\"\n"
            + "      }\n"
            + "    }\n"
            + "  ]\n"
            + "}";

    registry = new TestWebServer(false, Arrays.asList(manifestResponse), 1);
    RegistryClient registryClient = createRegistryClient(null);
    ManifestAndDigest<?> manifestAndDigest = registryClient.pullManifest("image-tag");

    Assert.assertEquals(
        "sha256:a340fa38667f765f8cfd79d4bc684ec8a6f48cdd63abfe36e109f4125ee38488",
        manifestAndDigest.getDigest().toString());

    Assert.assertTrue(manifestAndDigest.getManifest() instanceof V22ManifestListTemplate);
    V22ManifestListTemplate manifestList =
        (V22ManifestListTemplate) manifestAndDigest.getManifest();
    Assert.assertEquals(2, manifestList.getSchemaVersion());
    Assert.assertEquals(
        Arrays.asList("sha256:e692418e4cbaf90ca69d05a66403747baa33ee08806650b51fab815ad7fc331f"),
        manifestList.getDigestsForPlatform("amd64", "linux"));

    MatcherAssert.assertThat(
        registry.getInputRead(),
        CoreMatchers.containsString("GET /v2/foo/bar/manifests/image-tag "));
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
