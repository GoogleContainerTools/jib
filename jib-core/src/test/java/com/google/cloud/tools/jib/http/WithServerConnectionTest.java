/*
 * Copyright 2018 Google LLC. All rights reserved.
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

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import org.junit.Assert;
import org.junit.Test;

/** Tests for {@link Connection} using an actual local server. */
public class WithServerConnectionTest {

  private static class WebServer implements Closeable {

    private ServerSocket serverSocket;
    private boolean stop;

    private WebServer() throws IOException {
      serverSocket = new ServerSocket(8087);
      new Thread(this::serve200).start();
    }

    private void serve200() {
      try {
        while (!stop) {
          try (Socket socket = serverSocket.accept();
              OutputStream out = socket.getOutputStream()) {
            out.write("HTTP/1.1 200 OK\n\nHello World!".getBytes(StandardCharsets.UTF_8));
          }
        }
      } catch (IOException e) {
      }
    }

    @Override
    public void close() throws IOException {
      stop = true;
      serverSocket.close();
    }
  }

  @Test
  public void testGet() throws IOException {
    try (WebServer server = new WebServer();
        Connection connection = new Connection(new URL("http://localhost:8087"))) {
      Response response = connection.send("GET", new Request.Builder().build());

      Assert.assertEquals(200, response.getStatusCode());

      ByteArrayOutputStream out = new ByteArrayOutputStream();
      response.getBody().writeTo(out);
      Assert.assertEquals("Hello World!", new String(out.toByteArray(), StandardCharsets.UTF_8));
    }
  }

  @Test
  public void testErrorOnSecondSend() throws IOException {
    try (WebServer server = new WebServer();
        Connection connection = new Connection(new URL("http://localhost:8087"))) {
      connection.send("GET", new Request.Builder().build());
      try {
        connection.send("GET", new Request.Builder().build());
        Assert.fail("Should fail on the second send");
      } catch (IllegalStateException ex) {
        Assert.assertEquals("Connection can send only one request", ex.getMessage());
      }
    }
  }
}
