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

package com.google.cloud.tools.jib.http;

import static com.google.common.truth.Truth.assertThat;

import com.google.cloud.tools.jib.api.LogEvent;
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import javax.net.ssl.SSLException;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.RestoreSystemProperties;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link FailoverHttpClient} using an actual local server. */
@RunWith(MockitoJUnitRunner.class)
public class WithServerFailoverHttpClientTest {

  @Rule public final RestoreSystemProperties systemPropertyRestorer = new RestoreSystemProperties();

  @Mock private Consumer<LogEvent> logger;

  private final Request request = new Request.Builder().build();

  @Test
  public void testGet()
      throws IOException, InterruptedException, GeneralSecurityException, URISyntaxException {
    FailoverHttpClient insecureHttpClient =
        new FailoverHttpClient(false, true /*insecure*/, false, logger);
    try (TestWebServer server = new TestWebServer(false);
        Response response = insecureHttpClient.get(new URL(server.getEndpoint()), request)) {

      Assert.assertEquals(200, response.getStatusCode());
      Assert.assertArrayEquals(
          "Hello World!".getBytes(StandardCharsets.UTF_8),
          ByteStreams.toByteArray(response.getBody()));
      Mockito.verifyNoInteractions(logger);
    }
  }

  @Test
  public void testSecureConnectionOnInsecureHttpsServer()
      throws IOException, InterruptedException, GeneralSecurityException, URISyntaxException {
    FailoverHttpClient secureHttpClient =
        new FailoverHttpClient(false, false /*secure*/, false, logger);
    try (TestWebServer server = new TestWebServer(true);
        Response ignored = secureHttpClient.get(new URL(server.getEndpoint()), request)) {
      Assert.fail("Should fail if cannot verify peer");

    } catch (SSLException ex) {
      Assert.assertNotNull(ex.getMessage());
    }
  }

  @Test
  public void testInsecureConnection_insecureHttpsFailover()
      throws IOException, InterruptedException, GeneralSecurityException, URISyntaxException {
    FailoverHttpClient insecureHttpClient =
        new FailoverHttpClient(false, true /*insecure*/, false, logger);
    try (TestWebServer server = new TestWebServer(true, 2);
        Response response = insecureHttpClient.get(new URL(server.getEndpoint()), request)) {

      Assert.assertEquals(200, response.getStatusCode());
      Assert.assertArrayEquals(
          "Hello World!".getBytes(StandardCharsets.UTF_8),
          ByteStreams.toByteArray(response.getBody()));

      String endpoint = server.getEndpoint();
      String expectedLog =
          "Cannot verify server at " + endpoint + ". Attempting again with no TLS verification.";
      Mockito.verify(logger).accept(LogEvent.warn(expectedLog));
    }
  }

  @Test
  public void testInsecureConnection_plainHttpFailover()
      throws IOException, InterruptedException, GeneralSecurityException, URISyntaxException {
    FailoverHttpClient insecureHttpClient =
        new FailoverHttpClient(false, true /*insecure*/, false, logger);
    try (TestWebServer server = new TestWebServer(false, 3)) {
      String httpsUrl = server.getEndpoint().replace("http://", "https://");
      try (Response response = insecureHttpClient.get(new URL(httpsUrl), request)) {

        Assert.assertEquals(200, response.getStatusCode());
        Assert.assertArrayEquals(
            "Hello World!".getBytes(StandardCharsets.UTF_8),
            ByteStreams.toByteArray(response.getBody()));

        String expectedLog1 =
            "Cannot verify server at " + httpsUrl + ". Attempting again with no TLS verification.";
        String expectedLog2 =
            "Failed to connect to " + httpsUrl + " over HTTPS. Attempting again with HTTP.";
        Mockito.verify(logger).accept(LogEvent.warn(expectedLog1));
        Mockito.verify(logger).accept(LogEvent.warn(expectedLog2));
      }
    }
  }

  @Test
  public void testProxyCredentialProperties()
      throws IOException, InterruptedException, GeneralSecurityException, URISyntaxException {
    String proxyResponse =
        "HTTP/1.1 407 Proxy Authentication Required\n"
            + "Proxy-Authenticate: BASIC realm=\"some-realm\"\n"
            + "Cache-Control: no-cache\n"
            + "Pragma: no-cache\n"
            + "Content-Length: 0\n\n";
    String targetServerResponse = "HTTP/1.1 200 OK\nContent-Length:12\n\nHello World!";

    FailoverHttpClient httpClient = new FailoverHttpClient(false, true /*insecure*/, false, logger);
    try (TestWebServer server =
        new TestWebServer(false, Arrays.asList(proxyResponse, targetServerResponse), 1)) {
      System.setProperty("http.proxyHost", "localhost");
      System.setProperty("http.proxyPort", String.valueOf(server.getLocalPort()));
      System.setProperty("http.proxyUser", "user_sys_prop");
      System.setProperty("http.proxyPassword", "pass_sys_prop");

      try (Response response = httpClient.get(new URL("http://does.not.matter"), request)) {
        MatcherAssert.assertThat(
            server.getInputRead(),
            CoreMatchers.containsString(
                "Proxy-Authorization: Basic dXNlcl9zeXNfcHJvcDpwYXNzX3N5c19wcm9w"));
        Assert.assertArrayEquals(
            "Hello World!".getBytes(StandardCharsets.UTF_8),
            ByteStreams.toByteArray(response.getBody()));
      }
    }
  }

  @Test
  public void testClosingResourcesMultipleTimes_noErrors()
      throws IOException, InterruptedException, GeneralSecurityException, URISyntaxException {
    FailoverHttpClient httpClient = new FailoverHttpClient(false, true /*insecure*/, false, logger);
    try (TestWebServer server = new TestWebServer(false, 2);
        Response ignored1 = httpClient.get(new URL(server.getEndpoint()), request);
        Response ignored2 = httpClient.get(new URL(server.getEndpoint()), request)) {
      ignored1.close();
      ignored2.close();
    } finally {

      // Validate that calling shutdown() many times completes with no errors
      assertThat(httpClient.getTransportsCreated()).hasSize(2);
      httpClient.shutDown();
      httpClient.shutDown();
      assertThat(httpClient.getTransportsCreated()).hasSize(0);
    }
  }

  @Test
  public void testRedirectionUrls()
      throws IOException, InterruptedException, GeneralSecurityException, URISyntaxException {
    // Sample query strings from
    // https://github.com/GoogleContainerTools/jib/issues/1986#issuecomment-547610104
    String url1 = "?id=301&_auth_=exp=1572285389~hmac=f0a387f0";
    String url2 = "?id=302&Signature=2wYOD0a%2BDAkK%2F9lQJUOuIpYti8o%3D&Expires=1569997614";
    String url3 = "?id=303&_auth_=exp=1572285389~hmac=f0a387f0";
    String url4 = "?id=307&Signature=2wYOD0a%2BDAkK%2F9lQJUOuIpYti8o%3D&Expires=1569997614";
    String url5 = "?id=308&_auth_=exp=1572285389~hmac=f0a387f0";

    String redirect301 =
        "HTTP/1.1 301 Moved Permanently\nLocation: " + url1 + "\nContent-Length: 0\n\n";
    String redirect302 = "HTTP/1.1 302 Found\nLocation: " + url2 + "\nContent-Length: 0\n\n";
    String redirect303 = "HTTP/1.1 303 See Other\nLocation: " + url3 + "\nContent-Length: 0\n\n";
    String redirect307 =
        "HTTP/1.1 307 Temporary Redirect\nLocation: " + url4 + "\nContent-Length: 0\n\n";
    String redirect308 =
        "HTTP/1.1 308 Permanent Redirect\nLocation: " + url5 + "\nContent-Length: 0\n\n";
    String ok200 = "HTTP/1.1 200 OK\nContent-Length:12\n\nHello World!";
    List<String> responses =
        Arrays.asList(redirect301, redirect302, redirect303, redirect307, redirect308, ok200);

    FailoverHttpClient httpClient = new FailoverHttpClient(false, true /*insecure*/, false, logger);
    try (TestWebServer server = new TestWebServer(false, responses, 1)) {
      httpClient.get(new URL(server.getEndpoint()), request);

      MatcherAssert.assertThat(
          server.getInputRead(),
          CoreMatchers.containsString("GET /?id=301&_auth_=exp=1572285389~hmac=f0a387f0 "));
      MatcherAssert.assertThat(
          server.getInputRead(),
          CoreMatchers.containsString(
              "GET /?id=302&Signature=2wYOD0a%2BDAkK%2F9lQJUOuIpYti8o%3D&Expires=1569997614 "));
      MatcherAssert.assertThat(
          server.getInputRead(),
          CoreMatchers.containsString("GET /?id=303&_auth_=exp=1572285389~hmac=f0a387f0 "));
      MatcherAssert.assertThat(
          server.getInputRead(),
          CoreMatchers.containsString(
              "GET /?id=307&Signature=2wYOD0a%2BDAkK%2F9lQJUOuIpYti8o%3D&Expires=1569997614 "));
      MatcherAssert.assertThat(
          server.getInputRead(),
          CoreMatchers.containsString("GET /?id=308&_auth_=exp=1572285389~hmac=f0a387f0 "));
    }
  }
}
