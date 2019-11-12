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
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

/** Simple local web server for testing. */
public class TestWebServer implements Closeable {

  private final boolean https;
  private final int numThreads;
  private final List<String> responses;

  private final ServerSocket serverSocket;
  private final ExecutorService executorService;
  private final Semaphore serverStarted = new Semaphore(1);
  private final StringBuilder inputRead = new StringBuilder();

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
    this.https = https;
    this.responses = responses;
    this.numThreads = numThreads;
    serverSocket = https ? createHttpsServerSocket() : new ServerSocket(0);
    executorService = Executors.newFixedThreadPool(numThreads + 1);
    ignoreReturn(executorService.submit(this::listen));
    serverStarted.acquire();
  }

  public int getLocalPort() {
    return serverSocket.getLocalPort();
  }

  public String getEndpoint() {
    String host = serverSocket.getInetAddress().getHostAddress();
    return (https ? "https" : "http") + "://" + host + ":" + serverSocket.getLocalPort();
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

  private Void listen() throws IOException {
    serverStarted.release();
    for (int i = 0; i < numThreads; i++) {
      Socket socket = serverSocket.accept();
      ignoreReturn(executorService.submit(() -> serveResponses(socket)));
    }
    return null;
  }

  private Void serveResponses(Socket socket) throws IOException {
    try (Socket toClose = socket) {
      InputStream in = socket.getInputStream();
      OutputStream out = socket.getOutputStream();

      int firstByte = in.read();
      if (firstByte != 'G' && firstByte != 'P') { // GET, POST, ...
        out.write("HTTP/1.1 400 Bad Request\n\n".getBytes(StandardCharsets.UTF_8));
        return null;
      }

      BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
      for (String response : responses) {
        for (String line = reader.readLine();
            line != null && !line.isEmpty(); // An empty line marks the end of an HTTP request.
            line = reader.readLine()) {
          synchronized (inputRead) {
            if (firstByte != -1) {
              inputRead.append((char) firstByte);
              firstByte = -1;
            }
            inputRead.append(line).append('\n');
          }
        }
        out.write(response.getBytes(StandardCharsets.UTF_8));
        socket.getOutputStream().flush();
      }
    }
    return null;
  }

  // For use to ignore (i.e., accept and do nothing) a return value from ExecutionService.submit().
  // Without "consuming" the return value this way, Error Prone will complain to use it.
  private void ignoreReturn(Future<Void> future) {
    // do nothing; to make Error Prone happy
  }

  /**
   * Returns input read. Note if there were concurrent connections, input lines from different
   * connections can be intermixed. However, no lines will ever be broken in the middle.
   */
  public String getInputRead() {
    return inputRead.toString();
  }
}
