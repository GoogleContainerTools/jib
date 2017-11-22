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

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.cloud.tools.crepecake.blob.BlobStream;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import javax.annotation.Nullable;

/** Sends an HTTP request. */
public class Request {

  private static final HttpRequestFactory HTTP_REQUEST_FACTORY = new NetHttpTransport().createRequestFactory();

  private static class HttpMethod {
    private static final String PUT = "PUT";
    private static final String POST = "POST";
  }

  private final HttpRequest request;

  /** The request method; uses GET if null. */
  @Nullable private String method;

  private HttpHeaders headers = new HttpHeaders();

  public Request(URL url) throws IOException {
    request = HTTP_REQUEST_FACTORY.buildGetRequest(new GenericUrl(url));
  }

  /** Sends request with body. */
  public Response send(BlobStream body) throws IOException {
    request.setContent(body);

    return new Response(request);
  }

  /** Sends request without body. */
  public Response send() throws IOException {
    return send(new BlobStream());
  }

  public Request setContentType(String contentType) {
    headers.setContentType(contentType);
    return this;
  }

  public Request setMethodPut() {
    request.setRequestMethod(HttpMethod.PUT);
    return this;
  }

  public Request setMethodPost() {
    request.setRequestMethod(HttpMethod.POST);
    return this;
  }
}
