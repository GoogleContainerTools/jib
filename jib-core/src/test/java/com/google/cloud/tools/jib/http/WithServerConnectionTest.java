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

import com.google.cloud.tools.jib.api.LogEvent;
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.function.Consumer;
import javax.net.ssl.SSLException;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.RestoreSystemProperties;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link TlsFailoverHttpClient} using an actual local server. */
@RunWith(MockitoJUnitRunner.class)
public class WithServerConnectionTest {

  @Rule public final RestoreSystemProperties systemPropertyRestorer = new RestoreSystemProperties();

  @Mock private Consumer<LogEvent> logger;

  @Test
  public void testInsecureConnection_plainHttp()
      throws IOException, InterruptedException, GeneralSecurityException, URISyntaxException {
    TlsFailoverHttpClient httpClient = new TlsFailoverHttpClient(true, false, logger);
    try (TestWebServer server = new TestWebServer(false);
        Response response =
            httpClient.get(new URL(server.getEndpoint()), new Request.Builder().build())) {

      Assert.assertEquals(200, response.getStatusCode());
      Assert.assertArrayEquals(
          "Hello World!".getBytes(StandardCharsets.UTF_8),
          ByteStreams.toByteArray(response.getBody()));
      Mockito.verifyNoInteractions(logger);
    }
  }

  @Test
  public void testGet_insecureHttps()
      throws IOException, InterruptedException, GeneralSecurityException, URISyntaxException {
    TlsFailoverHttpClient httpClient = new TlsFailoverHttpClient(false, false, logger);
    try (TestWebServer server = new TestWebServer(true)) {
      try (Response ignored =
          httpClient.get(new URL(server.getEndpoint()), new Request.Builder().build())) {
        Assert.fail("Should fail if cannot verify peer");

      } catch (SSLException ex) {
        Assert.assertNotNull(ex.getMessage());
      }
    }
  }

  @Test
  public void testInsecureFailover_insecureHttps()
      throws IOException, InterruptedException, GeneralSecurityException, URISyntaxException {
    TlsFailoverHttpClient httpClient = new TlsFailoverHttpClient(true, false, logger);
    try (TestWebServer server = new TestWebServer(true, 2);
        Response response =
            httpClient.get(new URL(server.getEndpoint()), new Request.Builder().build())) {

      Assert.assertEquals(200, response.getStatusCode());
      Assert.assertArrayEquals(
          "Hello World!".getBytes(StandardCharsets.UTF_8),
          ByteStreams.toByteArray(response.getBody()));

      String endpoint = server.getEndpoint();
      String expectedLog =
          "Cannot verify server at " + endpoint + ". Attempting again with no TLS verification.";
      Mockito.verify(logger).accept(LogEvent.info(expectedLog));
    }
  }

  @Test
  public void testInsecureFailover_fallBackToHttp()
      throws IOException, InterruptedException, GeneralSecurityException, URISyntaxException {
    TlsFailoverHttpClient httpClient = new TlsFailoverHttpClient(true, false, logger);
    try (TestWebServer server = new TestWebServer(false, 3)) {
      String httpsUrl = server.getEndpoint().replace("http://", "https://");
      try (Response response = httpClient.get(new URL(httpsUrl), new Request.Builder().build())) {

        Assert.assertEquals(200, response.getStatusCode());
        Assert.assertArrayEquals(
            "Hello World!".getBytes(StandardCharsets.UTF_8),
            ByteStreams.toByteArray(response.getBody()));

        String expectedLog1 =
            "Cannot verify server at " + httpsUrl + ". Attempting again with no TLS verification.";
        String expectedLog2 =
            "Failed to connect to " + httpsUrl + " over HTTPS. Attempting again with HTTP.";
        Mockito.verify(logger).accept(LogEvent.info(expectedLog1));
        Mockito.verify(logger).accept(LogEvent.info(expectedLog2));
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

    TlsFailoverHttpClient httpClient = new TlsFailoverHttpClient(true, false, logger);
    try (TestWebServer server =
        new TestWebServer(false, Arrays.asList(proxyResponse, targetServerResponse), 1)) {
      System.setProperty("http.proxyHost", "localhost");
      System.setProperty("http.proxyPort", String.valueOf(server.getLocalPort()));
      System.setProperty("http.proxyUser", "user_sys_prop");
      System.setProperty("http.proxyPassword", "pass_sys_prop");

      try (Response response =
          httpClient.call(
              "GET", new URL("http://does.not.matter"), new Request.Builder().build())) {
        Assert.assertThat(
            server.getInputRead(),
            CoreMatchers.containsString(
                "Proxy-Authorization: Basic dXNlcl9zeXNfcHJvcDpwYXNzX3N5c19wcm9w"));
        Assert.assertArrayEquals(
            "Hello World!".getBytes(StandardCharsets.UTF_8),
            ByteStreams.toByteArray(response.getBody()));
      }
    }
  }
}
