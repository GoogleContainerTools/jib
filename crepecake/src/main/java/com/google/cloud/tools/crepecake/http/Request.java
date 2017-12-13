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
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.cloud.tools.crepecake.blob.Blob;
import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.net.URL;
import javax.annotation.Nullable;

/** Sends an HTTP request. */
public class Request {

  private static final HttpRequestFactory HTTP_REQUEST_FACTORY =
      new NetHttpTransport().createRequestFactory();

  private final HttpRequestFactory requestFactory;

  /** The URL to send the request to. */
  private GenericUrl url;

  /** The request method; uses GET if null. */
  @Nullable private String method;

  /** The HTTP request headers. */
  private HttpHeaders headers = new HttpHeaders();

  public Request(URL url) throws IOException {
    this(url, HTTP_REQUEST_FACTORY);
  }

  @VisibleForTesting
  Request(URL url, HttpRequestFactory requestFactory, HttpHeaders headers) throws IOException {
    this(url, requestFactory);
    this.headers = headers;
  }

  private Request(URL url, HttpRequestFactory requestFactory) throws IOException {
    this.url = new GenericUrl(url);
    this.requestFactory = requestFactory;
  }

  /** Sets the {@code Content-Type} header. */
  public Request setContentType(String contentType) {
    headers.setContentType(contentType);
    return this;
  }

  /** Sends the request with method GET. */
  public Response get() throws IOException {
    return new Response(requestFactory.buildGetRequest(url));
  }

  /** Sends the request with method POST. */
  public Response post(Blob body) throws IOException {
    return new Response(requestFactory.buildPostRequest(url, new BlobHttpContent(body)));
  }

  /** Sends the request with method PUT. */
  public Response put(Blob body) throws IOException {
    return new Response(requestFactory.buildPutRequest(url, new BlobHttpContent(body)));
  }
}
