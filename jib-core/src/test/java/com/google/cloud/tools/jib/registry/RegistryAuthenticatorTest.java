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
import com.google.cloud.tools.jib.api.RegistryAuthenticationFailedException;
import com.google.cloud.tools.jib.http.TestWebServer;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.GeneralSecurityException;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link RegistryAuthenticator}. */
public class RegistryAuthenticatorTest {
  private final RegistryEndpointRequestProperties registryEndpointRequestProperties =
      new RegistryEndpointRequestProperties("someserver", "someimage");

  private RegistryAuthenticator registryAuthenticator;

  @Before
  public void setUp() throws RegistryAuthenticationFailedException {
    registryAuthenticator =
        RegistryAuthenticator.fromAuthenticationMethod(
            "Bearer realm=\"https://somerealm\",service=\"someservice\",scope=\"somescope\"",
            registryEndpointRequestProperties,
            "user-agent");
  }

  @Test
  public void testFromAuthenticationMethod_bearer()
      throws MalformedURLException, RegistryAuthenticationFailedException {
    RegistryAuthenticator registryAuthenticator =
        RegistryAuthenticator.fromAuthenticationMethod(
            "Bearer realm=\"https://somerealm\",service=\"someservice\",scope=\"somescope\"",
            registryEndpointRequestProperties,
            "user-agent");
    Assert.assertEquals(
        new URL("https://somerealm?service=someservice&scope=repository:someimage:scope"),
        registryAuthenticator.getAuthenticationUrl(null, "scope"));

    registryAuthenticator =
        RegistryAuthenticator.fromAuthenticationMethod(
            "bEaReR realm=\"https://somerealm\",service=\"someservice\",scope=\"somescope\"",
            registryEndpointRequestProperties,
            "user-agent");
    Assert.assertEquals(
        new URL("https://somerealm?service=someservice&scope=repository:someimage:scope"),
        registryAuthenticator.getAuthenticationUrl(null, "scope"));
  }

  @Test
  public void testAuthRequestParameters_basicAuth() {
    Assert.assertEquals(
        "service=someservice&scope=repository:someimage:scope",
        registryAuthenticator.getAuthRequestParameters(null, "scope"));
  }

  @Test
  public void testAuthRequestParameters_oauth2() {
    Credential credential = Credential.from("<token>", "oauth2_access_token");
    Assert.assertEquals(
        "service=someservice&scope=repository:someimage:scope"
            + "&client_id=jib.da031fe481a93ac107a95a96462358f9"
            + "&grant_type=refresh_token&refresh_token=oauth2_access_token",
        registryAuthenticator.getAuthRequestParameters(credential, "scope"));
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
        registryAuthenticator.getAuthenticationUrl(null, "scope"));
  }

  @Test
  public void istAuthenticationUrl_oauth2() throws MalformedURLException {
    Credential credential = Credential.from("<token>", "oauth2_token");
    Assert.assertEquals(
        new URL("https://somerealm"),
        registryAuthenticator.getAuthenticationUrl(credential, "scope"));
  }

  @Test
  public void testFromAuthenticationMethod_basic() throws RegistryAuthenticationFailedException {
    Assert.assertNull(
        RegistryAuthenticator.fromAuthenticationMethod(
            "Basic realm=\"https://somerealm\",service=\"someservice\",scope=\"somescope\"",
            registryEndpointRequestProperties,
            "user-agent"));

    Assert.assertNull(
        RegistryAuthenticator.fromAuthenticationMethod(
            "BASIC realm=\"https://somerealm\",service=\"someservice\",scope=\"somescope\"",
            registryEndpointRequestProperties,
            "user-agent"));

    Assert.assertNull(
        RegistryAuthenticator.fromAuthenticationMethod(
            "bASIC realm=\"https://somerealm\",service=\"someservice\",scope=\"somescope\"",
            registryEndpointRequestProperties,
            "user-agent"));
  }

  @Test
  public void testFromAuthenticationMethod_noBearer() {
    try {
      RegistryAuthenticator.fromAuthenticationMethod(
          "realm=\"https://somerealm\",service=\"someservice\",scope=\"somescope\"",
          registryEndpointRequestProperties,
          "user-agent");
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
          "Bearer scope=\"somescope\"", registryEndpointRequestProperties, "user-agent");
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
            "Bearer realm=\"https://somerealm\"", registryEndpointRequestProperties, "user-agent");

    Assert.assertEquals(
        new URL("https://somerealm?service=someserver&scope=repository:someimage:scope"),
        registryAuthenticator.getAuthenticationUrl(null, "scope"));
  }

  @Test
  public void testUserAgent()
      throws IOException, InterruptedException, GeneralSecurityException, URISyntaxException {
    try (TestWebServer server = new TestWebServer(false)) {
      try {
        RegistryAuthenticator authenticator =
            RegistryAuthenticator.fromAuthenticationMethod(
                "Bearer realm=\"" + server.getEndpoint() + "\"",
                registryEndpointRequestProperties,
                "Competent-Agent");
        authenticator.authenticatePush(null);
      } catch (RegistryAuthenticationFailedException ex) {
        // Doesn't matter if auth fails. We only examine what we sent.
      }
      Assert.assertThat(
          server.getInputRead(), CoreMatchers.containsString("User-Agent: Competent-Agent"));
    }
  }
}
