/*
 * Copyright 2017 Google LLC. All rights reserved.
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

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpMethods;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.apache.ApacheHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.common.base.Preconditions;
import java.io.Closeable;
import java.io.IOException;
import java.net.URL;
import java.security.GeneralSecurityException;
import javax.annotation.Nullable;
import org.apache.http.NoHttpResponseException;

/**
 * Sends an HTTP {@link Request} and stores the {@link Response}. Clients should not send more than
 * one request.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * try (Connection connection = new Connection(url)) {
 *   Response response = connection.get(request);
 *   // ... process the response
 * }
 * }</pre>
 */
public class Connection implements Closeable {

  public static class Builder {

    private final URL url;

    /**
     * Do not use {@link NetHttpTransport}. It does not process response errors properly. A new
     * {@link ApacheHttpTransport} needs to be created for each connection because otherwise HTTP
     * connection persistence causes the connection to throw {@link NoHttpResponseException}.
     *
     * @see <a
     *     href="https://github.com/google/google-http-java-client/issues/39">https://github.com/google/google-http-java-client/issues/39</a>
     */
    private HttpTransport httpTransport = new ApacheHttpTransport();

    public Builder(URL url) {
      this.url = url;
    }

    /**
     * Turns off the normal TLS peer verification.
     *
     * @throws GeneralSecurityException if unable to turn off
     * @return this
     */
    public Builder doNotValidateCertificate() throws GeneralSecurityException {
      ApacheHttpTransport.Builder transportBuilder = new ApacheHttpTransport.Builder();
      transportBuilder.doNotValidateCertificate();
      httpTransport = transportBuilder.build();
      return this;
    }

    public Connection build() {
      return new Connection(url, httpTransport);
    }
  }

  private HttpRequestFactory requestFactory;

  @Nullable private HttpResponse httpResponse;

  /** The URL to send the request to. */
  private final GenericUrl url;

  /**
   * Make sure to wrap with a try-with-resource to ensure that the connection is closed after usage.
   *
   * @param url the url to send the request to
   */
  public Connection(URL url) {
    this(url, new ApacheHttpTransport());
  }

  private Connection(URL url, HttpTransport transport) {
    this.url = new GenericUrl(url);
    requestFactory = transport.createRequestFactory();
  }

  @Override
  public void close() throws IOException {
    if (httpResponse == null) {
      return;
    }

    httpResponse.disconnect();
  }

  /**
   * Sends the request with method GET.
   *
   * @param request the request to send
   * @return the response to the sent request
   * @throws IOException if sending the request fails
   */
  public Response get(Request request) throws IOException {
    return send(HttpMethods.GET, request);
  }

  /**
   * Sends the request with method POST.
   *
   * @param request the request to send
   * @return the response to the sent request
   * @throws IOException if sending the request fails
   */
  public Response post(Request request) throws IOException {
    return send(HttpMethods.POST, request);
  }

  /**
   * Sends the request with method PUT.
   *
   * @param request the request to send
   * @return the response to the sent request
   * @throws IOException if sending the request fails
   */
  public Response put(Request request) throws IOException {
    return send(HttpMethods.PUT, request);
  }

  /**
   * Sends the request.
   *
   * @param httpMethod the HTTP request method
   * @param request the request to send
   * @return the response to the sent request
   * @throws IOException if building the HTTP request fails.
   */
  public Response send(String httpMethod, Request request) throws IOException {
    Preconditions.checkState(httpResponse == null, "Connection can send only one request");

    HttpRequest httpRequest =
        requestFactory
            .buildRequest(httpMethod, url, request.getHttpContent())
            .setHeaders(request.getHeaders());
    if (request.getHttpTimeout() != null) {
      httpRequest.setConnectTimeout(request.getHttpTimeout());
      httpRequest.setReadTimeout(request.getHttpTimeout());
    }

    httpResponse = httpRequest.execute();
    return new Response(httpResponse);
  }
}
