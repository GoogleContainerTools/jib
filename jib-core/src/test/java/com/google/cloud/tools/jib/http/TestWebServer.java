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

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/** Simple local web server for testing. */
public class TestWebServer implements Closeable {

  private final ServerSocket serverSocket;

  public TestWebServer() throws IOException, InterruptedException {
    serverSocket = new ServerSocket(0);
    new Thread(this::serve200).start();
    waitUntilReady();
  }

  public String getEndpoint() {
    String host = serverSocket.getInetAddress().getHostAddress();
    return "http://" + host + ":" + serverSocket.getLocalPort();
  }

  @Override
  public void close() throws IOException {
    serverSocket.close();
  }

  private void waitUntilReady() throws IOException, InterruptedException {
    while (!Thread.interrupted()) {
      try (Socket socket = new Socket(serverSocket.getInetAddress(), serverSocket.getLocalPort())) {
        if (socket.isBound()) {
          return;
        }
      }
      Thread.sleep(50);
    }
    Thread.currentThread().interrupt();
  }

  private void serve200() {
    try {
      while (true) {
        try (Socket socket = serverSocket.accept();
            OutputStream out = socket.getOutputStream()) {
          out.write("HTTP/1.1 200 OK\n\nHello World!".getBytes(StandardCharsets.UTF_8));
        }
      }
    } catch (IOException ex) {
    }
  }
}
