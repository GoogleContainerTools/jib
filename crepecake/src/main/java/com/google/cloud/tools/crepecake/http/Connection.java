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
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.cloud.tools.crepecake.blob.Blob;
import com.google.common.annotations.VisibleForTesting;

import java.io.Closeable;
import java.io.IOException;
import java.net.URL;
import java.util.function.BiFunction;

/** Sends an HTTP {@link Request} and stores the {@link Response}. */
public class Connection implements Closeable {

  private static final HttpRequestFactory HTTP_REQUEST_FACTORY =
      new NetHttpTransport().createRequestFactory();

  private final HttpRequestFactory requestFactory;

  private HttpResponse httpResponse;

  /** The URL to send the request to. */
  private GenericUrl url;

  /**
   * Make sure to wrap with a try-with-resource to ensure that the connection is closed after usage.
   */
  public Connection(URL url) throws IOException {
    this(url, HTTP_REQUEST_FACTORY);
  }

  @VisibleForTesting
  Connection(URL url, HttpRequestFactory requestFactory) {
    this.url = new GenericUrl(url);
    this.requestFactory = requestFactory;
  }

  @Override
  public void close() throws IOException {
    if (httpResponse == null) {
      return;
    }

    httpResponse.disconnect();
  }

  /** Sends the request with method GET. */
  public Response get(Request request) throws IOException {
    return send(request, (url, body) -> requestFactory.buildGetRequest(url));
  }

  /** Sends the request with method POST. */
  public Response post(Request request) throws IOException {
    return send(request, requestFactory::buildPostRequest);
  }

  /** Sends the request with method PUT. */
  public Response put(Request request) throws IOException {
    return send(request, requestFactory::buildPutRequest);
  }

  @FunctionalInterface
  private interface BuildRequestFunction {

    HttpRequest build(GenericUrl url, BlobHttpContent body) throws IOException;
  }

  private Response send(Request request, BuildRequestFunction buildRequestFunction) throws IOException {
    httpResponse = buildRequestFunction.build(url, request.getBody()).setHeaders(request.getHeaders()).execute();
    return new Response(httpResponse);
  }
}
