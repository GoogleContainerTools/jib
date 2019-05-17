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
import javax.net.ssl.SSLException;
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
}
