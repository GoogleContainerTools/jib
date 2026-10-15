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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;

import com.google.cloud.tools.jib.api.Credential;
import com.google.cloud.tools.jib.api.RegistryAuthenticationFailedException;
import com.google.cloud.tools.jib.global.JibSystemProperties;
import com.google.cloud.tools.jib.http.Authorization;
import com.google.cloud.tools.jib.http.FailoverHttpClient;
import com.google.cloud.tools.jib.http.Response;
import com.google.cloud.tools.jib.http.ResponseException;
import com.google.cloud.tools.jib.http.TestWebServer;
import com.google.cloud.tools.jib.json.JsonTemplateMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Collections;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link RegistryAuthenticator}. */
@RunWith(MockitoJUnitRunner.class)
public class RegistryAuthenticatorTest {
  private final RegistryEndpointRequestProperties registryEndpointRequestProperties =
      new RegistryEndpointRequestProperties("someserver", "someimage");

  @Mock private FailoverHttpClient httpClient;
  @Mock private Response response;

  @Captor private ArgumentCaptor<URL> urlCaptor;

  private RegistryAuthenticator registryAuthenticator;

  @Before
  public void setUp() throws RegistryAuthenticationFailedException, IOException {
    registryAuthenticator =
        RegistryAuthenticator.fromAuthenticationMethod(
                "Bearer realm=\"https://somerealm\",service=\"someservice\",scope=\"somescope\"",
                registryEndpointRequestProperties,
                "user-agent",
                httpClient)
            .get();

    ByteArrayInputStream tokenJson =
        new ByteArrayInputStream("{\"token\":\"my_token\"}".getBytes(StandardCharsets.UTF_8));
    Mockito.when(response.getBody()).thenReturn(tokenJson);
    Mockito.when(httpClient.call(Mockito.any(), urlCaptor.capture(), Mockito.any()))
        .thenReturn(response);
  }

  @Test
  public void testFromAuthenticationMethod_bearer()
      throws MalformedURLException, RegistryAuthenticationFailedException {
    RegistryAuthenticator registryAuthenticator =
        RegistryAuthenticator.fromAuthenticationMethod(
                "Bearer realm=\"https://somerealm\",service=\"someservice\",scope=\"somescope\"",
                registryEndpointRequestProperties,
                "user-agent",
                httpClient)
            .get();
    assertThat(
            registryAuthenticator.getAuthenticationUrl(
                null, Collections.singletonMap("someimage", "scope")))
        .isEqualTo(
            new URL("https://somerealm?service=someservice&scope=repository:someimage:scope"));

    registryAuthenticator =
        RegistryAuthenticator.fromAuthenticationMethod(
                "bEaReR realm=\"https://somerealm\",service=\"someservice\",scope=\"somescope\"",
                registryEndpointRequestProperties,
                "user-agent",
                httpClient)
            .get();
    assertThat(
            registryAuthenticator.getAuthenticationUrl(
                null, Collections.singletonMap("someimage", "scope")))
        .isEqualTo(
            new URL("https://somerealm?service=someservice&scope=repository:someimage:scope"));
  }

  @Test
  public void testAuthRequestParameters_basicAuth() {
    Assert.assertEquals(
        "service=someservice&scope=repository:someimage:scope",
        registryAuthenticator.getAuthRequestParameters(
            null, Collections.singletonMap("someimage", "scope")));
  }

  @Test
  public void testAuthRequestParameters_oauth2() {
    Credential credential = Credential.from("<token>", "oauth2_access_token");
    Assert.assertEquals(
        "service=someservice&scope=repository:someimage:scope"
            + "&client_id=jib.da031fe481a93ac107a95a96462358f9"
            + "&grant_type=refresh_token&refresh_token=oauth2_access_token",
        registryAuthenticator.getAuthRequestParameters(
            credential, Collections.singletonMap("someimage", "scope")));
  }

  @Test
  public void isOAuth2Auth_nullCredential() {
    Assert.assertFalse(registryAuthenticator.isOAuth2Auth(null));
  }

  @Test
  public void isOAuth2Auth_basicAuth() {
    Credential credential = Credential.from("name", "password");
    Assert.assertFalse(registryAuthenticator.isOAuth2Auth(credential));
  }

  @Test
  public void isOAuth2Auth_oauth2() {
    Credential credential = Credential.from("<token>", "oauth2_token");
    Assert.assertTrue(registryAuthenticator.isOAuth2Auth(credential));
  }

  @Test
  public void getAuthenticationUrl_basicAuth() throws MalformedURLException {
    Assert.assertEquals(
        new URL("https://somerealm?service=someservice&scope=repository:someimage:scope"),
        registryAuthenticator.getAuthenticationUrl(
            null, Collections.singletonMap("someimage", "scope")));
  }

  @Test
  public void istAuthenticationUrl_oauth2() throws MalformedURLException {
    Credential credential = Credential.from("<token>", "oauth2_token");
    Assert.assertEquals(
        new URL("https://somerealm"),
        registryAuthenticator.getAuthenticationUrl(credential, Collections.emptyMap()));
  }

  @Test
  public void testFromAuthenticationMethod_basic() throws RegistryAuthenticationFailedException {
    assertThat(
            RegistryAuthenticator.fromAuthenticationMethod(
                "Basic", registryEndpointRequestProperties, "user-agent", httpClient))
        .isEmpty();

    assertThat(
            RegistryAuthenticator.fromAuthenticationMethod(
                "Basic realm=\"https://somerealm\",service=\"someservice\",scope=\"somescope\"",
                registryEndpointRequestProperties,
                "user-agent",
                httpClient))
        .isEmpty();

    assertThat(
            RegistryAuthenticator.fromAuthenticationMethod(
                "BASIC realm=\"https://somerealm\",service=\"someservice\",scope=\"somescope\"",
                registryEndpointRequestProperties,
                "user-agent",
                httpClient))
        .isEmpty();

    assertThat(
            RegistryAuthenticator.fromAuthenticationMethod(
                "bASIC realm=\"https://somerealm\",service=\"someservice\",scope=\"somescope\"",
                registryEndpointRequestProperties,
                "user-agent",
                httpClient))
        .isEmpty();
  }

  @Test
  public void testFromAuthenticationMethod_noBearer() {
    try {
      RegistryAuthenticator.fromAuthenticationMethod(
          "realm=\"https://somerealm\",service=\"someservice\",scope=\"somescope\"",
          registryEndpointRequestProperties,
          "user-agent",
          httpClient);
      Assert.fail("Authentication method without 'Bearer ' or 'Basic ' should fail");

    } catch (RegistryAuthenticationFailedException ex) {
      Assert.assertEquals(
          "Failed to authenticate with registry someserver/someimage because: 'Bearer' was not found in the 'WWW-Authenticate' header, tried to parse: realm=\"https://somerealm\",service=\"someservice\",scope=\"somescope\"",
          ex.getMessage());
    }
  }

  @Test
  public void testFromAuthenticationMethod_noRealm() {
    try {
      RegistryAuthenticator.fromAuthenticationMethod(
          "Bearer scope=\"somescope\"",
          registryEndpointRequestProperties,
          "user-agent",
          httpClient);
      Assert.fail("Authentication method without 'realm' should fail");

    } catch (RegistryAuthenticationFailedException ex) {
      Assert.assertEquals(
          "Failed to authenticate with registry someserver/someimage because: 'realm' was not found in the 'WWW-Authenticate' header, tried to parse: Bearer scope=\"somescope\"",
          ex.getMessage());
    }
  }

  @Test
  public void testFromAuthenticationMethod_noService()
      throws MalformedURLException, RegistryAuthenticationFailedException {
    RegistryAuthenticator registryAuthenticator =
        RegistryAuthenticator.fromAuthenticationMethod(
                "Bearer realm=\"https://somerealm\"",
                registryEndpointRequestProperties,
                "user-agent",
                httpClient)
            .get();

    Assert.assertEquals(
        new URL("https://somerealm?service=someserver&scope=repository:someimage:scope"),
        registryAuthenticator.getAuthenticationUrl(
            null, Collections.singletonMap("someimage", "scope")));
  }

  @Test
  public void testUserAgent()
      throws IOException, InterruptedException, GeneralSecurityException, URISyntaxException,
          RegistryCredentialsNotSentException {
    try (TestWebServer server = new TestWebServer(false)) {
      try {
        RegistryAuthenticator authenticator =
            RegistryAuthenticator.fromAuthenticationMethod(
                    "Bearer realm=\"" + server.getEndpoint() + "\"",
                    registryEndpointRequestProperties,
                    "Competent-Agent",
                    new FailoverHttpClient(true, false, ignored -> {}))
                .get();
        authenticator.authenticatePush(null);
      } catch (RegistryAuthenticationFailedException ex) {
        // Doesn't matter if auth fails. We only examine what we sent.
      }
      MatcherAssert.assertThat(
          server.getInputRead(), CoreMatchers.containsString("User-Agent: Competent-Agent"));
    }
  }

  @Test
  public void testSourceImage_differentSourceRepository()
      throws RegistryCredentialsNotSentException, RegistryAuthenticationFailedException {
    RegistryAuthenticator authenticator =
        RegistryAuthenticator.fromAuthenticationMethod(
                "Bearer realm=\"https://1.2.3.4:5\"",
                new RegistryEndpointRequestProperties("someserver", "someimage", "anotherimage"),
                "Competent-Agent",
                httpClient)
            .get();
    authenticator.authenticatePush(null);
    MatcherAssert.assertThat(
        urlCaptor.getValue().toString(),
        CoreMatchers.endsWith(
            "scope=repository:someimage:pull,push&scope=repository:anotherimage:pull"));
  }

  @Test
  public void testSourceImage_sameSourceRepository()
      throws RegistryCredentialsNotSentException, RegistryAuthenticationFailedException {
    RegistryAuthenticator authenticator =
        RegistryAuthenticator.fromAuthenticationMethod(
                "Bearer realm=\"https://1.2.3.4:5\"",
                new RegistryEndpointRequestProperties("someserver", "someimage", "someimage"),
                "Competent-Agent",
                httpClient)
            .get();
    authenticator.authenticatePush(null);
    MatcherAssert.assertThat(
        urlCaptor.getValue().toString(),
        CoreMatchers.endsWith("service=someserver&scope=repository:someimage:pull,push"));
  }

  @Test
  public void testAuthorizationCleared() throws RegistryAuthenticationFailedException, IOException {
    ResponseException responseException = Mockito.mock(ResponseException.class);
    Mockito.when(responseException.getStatusCode()).thenReturn(401);
    Mockito.when(responseException.requestAuthorizationCleared()).thenReturn(true);
    Mockito.when(httpClient.call(Mockito.any(), Mockito.any(), Mockito.any()))
        .thenThrow(responseException);

    try {
      registryAuthenticator.authenticatePush(null);
      Assert.fail();
    } catch (RegistryCredentialsNotSentException ex) {
      Assert.assertEquals(
          "Required credentials for someserver/someimage were not sent because the connection was "
              + "over HTTP",
          ex.getMessage());
    }
  }

  @Test
  public void testAuthenticationResponseTemplate_readsToken() throws IOException {
    String input = "{\"token\":\"test_value\"}";
    RegistryAuthenticator.AuthenticationResponseTemplate template =
        JsonTemplateMapper.readJson(
            input, RegistryAuthenticator.AuthenticationResponseTemplate.class);
    Assert.assertEquals("test_value", template.getToken());
  }

  @Test
  public void testAuthenticationResponseTemplate_readsAccessToken() throws IOException {
    String input = "{\"access_token\":\"test_value\"}";
    RegistryAuthenticator.AuthenticationResponseTemplate template =
        JsonTemplateMapper.readJson(
            input, RegistryAuthenticator.AuthenticationResponseTemplate.class);
    Assert.assertEquals("test_value", template.getToken());
  }

  @Test
  public void testAuthenticationResponseTemplate_prefersToken() throws IOException {
    String input = "{\"token\":\"test_value\",\"access_token\":\"wrong_value\"}";
    RegistryAuthenticator.AuthenticationResponseTemplate template =
        JsonTemplateMapper.readJson(
            input, RegistryAuthenticator.AuthenticationResponseTemplate.class);
    Assert.assertEquals("test_value", template.getToken());
  }

  @Test
  public void testAuthenticationResponseTemplate_acceptsNull() throws IOException {
    String input = "{}";
    RegistryAuthenticator.AuthenticationResponseTemplate template =
        JsonTemplateMapper.readJson(
            input, RegistryAuthenticator.AuthenticationResponseTemplate.class);
    Assert.assertNull(template.getToken());
  }

  @Test
  public void testIsRealmTrusted() throws MalformedURLException {
    Assert.assertTrue(
        RegistryAuthenticator.isRealmTrusted(
            new URL("https://auth.docker.io/token"), "registry-1.docker.io"));
    Assert.assertTrue(
        RegistryAuthenticator.isRealmTrusted(new URL("https://gcr.io/v2/token"), "gcr.io"));
    Assert.assertTrue(
        RegistryAuthenticator.isRealmTrusted(
            new URL("https://reg.example.com:5001/auth"), "reg.example.com:5000"));
    Assert.assertTrue(
        RegistryAuthenticator.isRealmTrusted(new URL("http://localhost:5001/t"), "localhost:5000"));
    Assert.assertFalse(
        RegistryAuthenticator.isRealmTrusted(new URL("https://evil.com/steal"), "gcr.io"));
    Assert.assertFalse(
        RegistryAuthenticator.isRealmTrusted(
            new URL("https://docker.io.evil.com/t"), "registry-1.docker.io"));
    Assert.assertFalse(
        RegistryAuthenticator.isRealmTrusted(new URL("https://a.co.uk/t"), "b.co.uk"));
    Assert.assertFalse(
        RegistryAuthenticator.isRealmTrusted(new URL("https://1.2.3.4/t"), "gcr.io"));
  }

  @Test
  public void testAuthenticate_untrustedRealmWithCredentials_notSent() throws Exception {
    RegistryAuthenticator authenticator =
        RegistryAuthenticator.fromAuthenticationMethod(
                "Bearer realm=\"https://evil.example.net/steal\",service=\"s\"",
                new RegistryEndpointRequestProperties("registry.example.com", "someimage"),
                "user-agent",
                httpClient)
            .get();
    try {
      authenticator.authenticatePull(Credential.from("user", "secret"));
      Assert.fail();
    } catch (RegistryAuthenticationFailedException ex) {
      MatcherAssert.assertThat(
          ex.getMessage(), CoreMatchers.containsString("Refusing to send credentials"));
    }
    Mockito.verify(httpClient, Mockito.never()).call(Mockito.any(), Mockito.any(), Mockito.any());
  }

  @Test
  public void testIsRealmTrusted_configuredTrustedHost() throws MalformedURLException {
    URL realm = new URL("https://sso.other.net/token");
    Assert.assertFalse(RegistryAuthenticator.isRealmTrusted(realm, "registry.example.com"));
    System.setProperty(JibSystemProperties.TRUSTED_AUTH_HOSTS, "foo.org, SSO.other.net");
    try {
      Assert.assertTrue(RegistryAuthenticator.isRealmTrusted(realm, "registry.example.com"));
    } finally {
      System.clearProperty(JibSystemProperties.TRUSTED_AUTH_HOSTS);
    }
  }

  private RegistryAuthenticator authenticatorFor(String realm, String registry) throws Exception {
    return RegistryAuthenticator.fromAuthenticationMethod(
            "Bearer realm=\"" + realm + "\",service=\"s\"",
            new RegistryEndpointRequestProperties(registry, "someimage"),
            "user-agent",
            httpClient)
        .get();
  }

  @Test
  public void testAuthenticate_untrustedRealmWithRefreshToken_notSent() throws Exception {
    RegistryAuthenticator authenticator =
        authenticatorFor("https://evil.example.net/steal", "registry.example.com");
    try {
      authenticator.authenticatePull(Credential.from("<token>", "refresh_token"));
      Assert.fail();
    } catch (RegistryAuthenticationFailedException ex) {
      MatcherAssert.assertThat(
          ex.getMessage(), CoreMatchers.containsString("Refusing to send credentials"));
    }
    Mockito.verify(httpClient, Mockito.never()).call(Mockito.any(), Mockito.any(), Mockito.any());
  }

  @Test
  public void testAuthenticate_untrustedRealmAnonymous_stillRequested() throws Exception {
    RegistryAuthenticator authenticator =
        authenticatorFor("https://other.example.net/token", "registry.example.com");
    Authorization authorization = authenticator.authenticatePull(null);
    Assert.assertEquals("Bearer my_token", authorization.toString());
    Assert.assertEquals("other.example.net", urlCaptor.getValue().getHost());
  }

  @Test
  public void testAuthenticate_trustedRealmWithCredentials_succeeds() throws Exception {
    RegistryAuthenticator authenticator =
        authenticatorFor("https://auth.docker.io/token", "registry-1.docker.io");
    Authorization authorization =
        authenticator.authenticatePull(Credential.from("user", "password"));
    Assert.assertEquals("Bearer my_token", authorization.toString());
    Assert.assertEquals("auth.docker.io", urlCaptor.getValue().getHost());
  }

  @Test
  public void testAuthenticate_configuredTrustedHostWithCredentials_succeeds() throws Exception {
    String previous = System.getProperty(JibSystemProperties.TRUSTED_AUTH_HOSTS);
    System.setProperty(JibSystemProperties.TRUSTED_AUTH_HOSTS, "sso.other.net");
    try {
      RegistryAuthenticator authenticator =
          authenticatorFor("https://sso.other.net/token", "registry.example.com");
      Assert.assertEquals(
          "Bearer my_token",
          authenticator.authenticatePull(Credential.from("user", "password")).toString());
    } finally {
      if (previous == null) {
        System.clearProperty(JibSystemProperties.TRUSTED_AUTH_HOSTS);
      } else {
        System.setProperty(JibSystemProperties.TRUSTED_AUTH_HOSTS, previous);
      }
    }
  }

  @Test
  public void testIsRealmTrusted_privateSuffixSiblingsRejected() throws MalformedURLException {
    // github.io and herokuapp.com are public suffixes: sibling sites belong to different owners.
    Assert.assertFalse(
        RegistryAuthenticator.isRealmTrusted(new URL("https://a.github.io/t"), "b.github.io"));
    Assert.assertFalse(
        RegistryAuthenticator.isRealmTrusted(
            new URL("https://x.herokuapp.com/t"), "y.herokuapp.com"));
  }
}
