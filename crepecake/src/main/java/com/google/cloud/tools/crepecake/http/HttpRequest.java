/*
 * Copyright 2017 Google Inc.
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

package com.google.cloud.tools.crepecake.http;

import com.google.cloud.tools.crepecake.blob.BlobStream;
import com.google.common.net.HttpHeaders;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;

/** Sends an HTTP request. */
public class HttpRequest {

  private static class HttpMethod {
    private static final String PUT = "PUT";
    private static final String POST = "POST";
  }

  private final URL url;
  private final ConnectionFactory connectionFactory;

  /** The request method; uses GET if null. */
  @Nullable private String method;

  private Map<String, String> headers = new HashMap<>();

  public HttpRequest(URL url) {
    this(url, new ConnectionFactory());
  }

  HttpRequest(URL url, ConnectionFactory connectionFactory) {
    this.url = url;
    this.connectionFactory = connectionFactory;
  }

  /** Sends request with body. */
  public HttpResponse send(BlobStream body) throws IOException {
    HttpURLConnection connection = connectionFactory.newConnection(url);

    headers.forEach(connection::setRequestProperty);

    if (method != null) {
      connection.setRequestMethod(method);

      // Any method besides GET should send a body.
      connection.setDoOutput(true);
      try (OutputStream outputStream = connection.getOutputStream()) {
        body.writeTo(outputStream);
      }
    }

    return new HttpResponse(connection);
  }

  /** Sends request without body. */
  public HttpResponse send() throws IOException {
    return send(new BlobStream());
  }

  public HttpRequest setContentType(String contentType) {
    headers.put(HttpHeaders.CONTENT_TYPE, contentType);
    return this;
  }

  public HttpRequest setMethodPut() {
    method = HttpMethod.PUT;
    return this;
  }

  public HttpRequest setMethodPost() {
    method = HttpMethod.POST;
    return this;
  }
}
