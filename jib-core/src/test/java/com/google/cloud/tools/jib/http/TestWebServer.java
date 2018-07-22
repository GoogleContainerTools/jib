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
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

/** Simple local web server for testing. */
class TestWebServer implements Closeable {

  private final ServerSocket serverSocket;
  private final ConcurrentLinkedQueue<Socket> socketsToClose = new ConcurrentLinkedQueue<>();
  private final ExecutorService executorService = Executors.newSingleThreadExecutor();
  private final Semaphore threadStarted = new Semaphore(0);

  TestWebServer() throws IOException, InterruptedException {
    serverSocket = new ServerSocket(0);
    executorService.submit(this::serve200);
    threadStarted.acquire();
  }

  String getEndpoint() {
    String host = serverSocket.getInetAddress().getHostAddress();
    return "http://" + host + ":" + serverSocket.getLocalPort();
  }

  @Override
  public void close() throws IOException {
    serverSocket.close();
    for (Socket socket : socketsToClose) {
      try (Socket toClose = socket) {
        toClose.close();
      } catch (IOException ex) {
      }
    }
    executorService.shutdown();
  }

  private Void serve200() throws IOException {
    threadStarted.release();
    while (true) {
      try (Socket socket = serverSocket.accept()) {
        try {
          String response = "HTTP/1.1 200 OK\nContent-Length:12\n\nHello World!";
          socket.getOutputStream().write(response.getBytes(StandardCharsets.UTF_8));
          socket.shutdownOutput();
        } catch (IOException ex) {
        }
      }
    }
  }
}
