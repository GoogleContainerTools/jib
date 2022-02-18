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

import com.google.common.io.Resources;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

/** Simple local web server for testing. */
public class TestWebServer implements Closeable {

  private final boolean https;
  private final int numThreads;
  private final List<String> responses;
  private final boolean forgetServedResponses;

  private final ServerSocket serverSocket;
  private final ExecutorService executorService;
  private final Semaphore serverStarted = new Semaphore(1);
  private final StringBuilder inputRead = new StringBuilder();

  private int totalResponsesServed = 0;
  private int globalResponseIndex = 0;

  public TestWebServer(boolean https)
      throws IOException, InterruptedException, GeneralSecurityException, URISyntaxException {
    this(https, Arrays.asList("HTTP/1.1 200 OK\nContent-Length:12\n\nHello World!"), 1);
  }

  public TestWebServer(boolean https, int numThreads)
      throws IOException, InterruptedException, GeneralSecurityException, URISyntaxException {
    this(https, Arrays.asList("HTTP/1.1 200 OK\nContent-Length:12\n\nHello World!"), numThreads);
  }

  public TestWebServer(boolean https, List<String> responses, int numThreads)
      throws IOException, InterruptedException, GeneralSecurityException, URISyntaxException {
    this(https, responses, numThreads, false);
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  public TestWebServer(
      boolean https, List<String> responses, int numThreads, boolean forgetServedResponses)
      throws IOException, InterruptedException, GeneralSecurityException, URISyntaxException {
    this.https = https;
    this.responses = responses;
    this.numThreads = numThreads;
    this.forgetServedResponses = forgetServedResponses;
    serverSocket = https ? createHttpsServerSocket() : new ServerSocket(0);
    executorService = Executors.newFixedThreadPool(numThreads + 1);
    executorService.submit(this::listen);
    serverStarted.acquire();
  }

  public int getLocalPort() {
    return serverSocket.getLocalPort();
  }

  public String getEndpoint() {
    return (https ? "https" : "http") + "://localhost:" + serverSocket.getLocalPort();
  }

  @Override
  public void close() throws IOException {
    serverSocket.close();
    executorService.shutdown();
  }

  private ServerSocket createHttpsServerSocket()
      throws IOException, GeneralSecurityException, URISyntaxException {
    KeyStore keyStore = KeyStore.getInstance("JKS");
    // generated with: keytool -genkey -keyalg RSA -keystore ./TestWebServer-keystore
    Path keyStoreFile = Paths.get(Resources.getResource("core/TestWebServer-keystore").toURI());
    try (InputStream in = Files.newInputStream(keyStoreFile)) {
      keyStore.load(in, "password".toCharArray());
    }

    KeyManagerFactory keyManagerFactory =
        KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    keyManagerFactory.init(keyStore, "password".toCharArray());

    SSLContext sslContext = SSLContext.getInstance("TLS");
    sslContext.init(keyManagerFactory.getKeyManagers(), null, null);
    return sslContext.getServerSocketFactory().createServerSocket(0);
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  private Void listen() throws IOException {
    serverStarted.release();
    for (int i = 0; i < numThreads; i++) {
      Socket socket = serverSocket.accept();
      executorService.submit(() -> serveResponses(socket));
    }
    return null;
  }

  private Void serveResponses(Socket socket) throws IOException {
    try (Socket ignored = socket) {
      InputStream in = socket.getInputStream();
      OutputStream out = socket.getOutputStream();

      int firstByte = in.read();
      int secondByte = in.read();
      if (!(firstByte == 'G' && secondByte == 'E')
          && !(firstByte == 'P' && secondByte == 'O')
          && !(firstByte == 'H' && secondByte == 'E')) { // GET, POST, HEAD, ...
        out.write(
            "HTTP/1.1 400 Bad Request\nContent-Length: 0\n\n".getBytes(StandardCharsets.UTF_8));
        return null;
      }

      BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
      for (int i = 0; true; i++) {
        for (String line = reader.readLine();
            line != null && !line.isEmpty(); // An empty line marks the end of an HTTP request.
            line = reader.readLine()) {
          synchronized (inputRead) {
            if (firstByte != -1) {
              inputRead.append((char) firstByte).append((char) secondByte);
              firstByte = -1;
            }
            inputRead.append(line).append('\n');
          }
        }
        String response = getNextResponse(i);
        if (response == null) {
          return null;
        }
        out.write(response.getBytes(StandardCharsets.UTF_8));
        out.flush();
      }
    }
  }

  private synchronized String getNextResponse(int index) {
    if (index >= responses.size() || globalResponseIndex >= responses.size()) {
      return null;
    }
    totalResponsesServed++;
    return forgetServedResponses ? responses.get(globalResponseIndex++) : responses.get(index);
  }

  /**
   * Returns input read. Note if there were concurrent connections, input lines from different
   * connections can be intermixed. However, no lines will ever be broken in the middle.
   */
  public String getInputRead() {
    synchronized (inputRead) {
      return inputRead.toString();
    }
  }

  public synchronized int getTotalResponsesServed() {
    return totalResponsesServed;
  }
}
