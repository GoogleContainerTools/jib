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

import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import javax.net.ssl.SSLException;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Test;

/** Tests for {@link Connection} using an actual local server. */
public class WithServerConnectionTest {

  @Test
  public void testGet()
      throws IOException, InterruptedException, GeneralSecurityException, URISyntaxException {
    try (TestWebServer server = new TestWebServer(false);
        Connection connection =
            Connection.getConnectionFactory().apply(new URL(server.getEndpoint()))) {
      Response response = connection.send("GET", new Request.Builder().build());

      Assert.assertEquals(200, response.getStatusCode());
      Assert.assertArrayEquals(
          "Hello World!".getBytes(StandardCharsets.UTF_8),
          ByteStreams.toByteArray(response.getBody()));
    }
  }

  @Test
  public void testErrorOnSecondSend()
      throws IOException, InterruptedException, GeneralSecurityException, URISyntaxException {
    try (TestWebServer server = new TestWebServer(false);
        Connection connection =
            Connection.getConnectionFactory().apply(new URL(server.getEndpoint()))) {
      connection.send("GET", new Request.Builder().build());
      try {
        connection.send("GET", new Request.Builder().build());
        Assert.fail("Should fail on the second send");
      } catch (IllegalStateException ex) {
        Assert.assertEquals("Connection can send only one request", ex.getMessage());
      }
    }
  }

  @Test
  public void testSecureConnectionOnInsecureHttpsServer()
      throws IOException, InterruptedException, GeneralSecurityException, URISyntaxException {
    try (TestWebServer server = new TestWebServer(true);
        Connection connection =
            Connection.getConnectionFactory().apply(new URL(server.getEndpoint()))) {
      try {
        connection.send("GET", new Request.Builder().build());
        Assert.fail("Should fail if cannot verify peer");
      } catch (SSLException ex) {
        Assert.assertNotNull(ex.getMessage());
      }
    }
  }

  @Test
  public void testInsecureConnection()
      throws IOException, InterruptedException, GeneralSecurityException, URISyntaxException {
    try (TestWebServer server = new TestWebServer(true);
        Connection connection =
            Connection.getInsecureConnectionFactory().apply(new URL(server.getEndpoint()))) {
      Response response = connection.send("GET", new Request.Builder().build());

      Assert.assertEquals(200, response.getStatusCode());
      Assert.assertArrayEquals(
          "Hello World!".getBytes(StandardCharsets.UTF_8),
          ByteStreams.toByteArray(response.getBody()));
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

    try (TestWebServer server =
        new TestWebServer(false, Arrays.asList(proxyResponse, targetServerResponse))) {
      System.setProperty("http.proxyHost", "localhost");
      System.setProperty("http.proxyPort", String.valueOf(server.getLocalPort()));
      System.setProperty("http.proxyUser", "user_sys_prop");
      System.setProperty("http.proxyPassword", "pass_sys_prop");

      try (Connection connection =
          Connection.getConnectionFactory().apply(new URL("http://does.not.matter"))) {
        Response response = connection.send("GET", new Request.Builder().build());
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
