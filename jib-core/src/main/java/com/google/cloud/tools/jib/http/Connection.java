/*
 * Copyright 2017 Google LLC.
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
import com.google.api.client.http.apache.v2.ApacheHttpTransport;
import com.google.api.client.util.SslUtils;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import java.io.Closeable;
import java.io.IOException;
import java.net.ProxySelector;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import javax.annotation.Nullable;
import org.apache.http.config.SocketConfig;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.impl.conn.SystemDefaultRoutePlanner;

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

  /**
   * Returns a factory for {@link Connection}.
   *
   * @return {@link Connection} factory, a function that generates a {@link Connection} to a URL
   */
  public static Function<URL, Connection> getConnectionFactory() {
    // Do not use NetHttpTransport. It does not process response errors properly.
    // See https://github.com/google/google-http-java-client/issues/39
    //
    // A new ApacheHttpTransport needs to be created for each connection because otherwise HTTP
    // connection persistence causes the connection to throw NoHttpResponseException.
    return url -> new Connection(url, new ApacheHttpTransport());
  }

  /**
   * Returns a factory for {@link Connection} that does not verify TLS peer verification.
   *
   * @throws GeneralSecurityException if unable to turn off TLS peer verification
   * @return {@link Connection} factory, a function that generates a {@link Connection} to a URL
   */
  public static Function<URL, Connection> getInsecureConnectionFactory()
      throws GeneralSecurityException {
    HttpClientBuilder httpClientBuilder =
        newDefaultHttpClientBuilder()
            .setSSLContext(SslUtils.trustAllSSLContext())
            .setSSLHostnameVerifier(new NoopHostnameVerifier());

    // Do not use NetHttpTransport. See comments in getConnectionFactory for details.
    return url -> new Connection(url, new ApacheHttpTransport(httpClientBuilder.build()));
  }

  // TODO(chanseok): remove. Use ApacheHttpTransport.newDefaultHttpClientBuilder() when it becomes
  // available (https://github.com/googleapis/google-http-java-client/issues/578)
  private static HttpClientBuilder newDefaultHttpClientBuilder() {
    // Code from
    // https://github.com/googleapis/google-http-java-client/blob/v1.30.1/google-http-client-apache-v2/src/main/java/com/google/api/client/http/apache/v2/ApacheHttpTransport.java#L125-L149
    SocketConfig socketConfig =
        SocketConfig.custom().setRcvBufSize(8192).setSndBufSize(8192).build();

    PoolingHttpClientConnectionManager connectionManager =
        new PoolingHttpClientConnectionManager(-1, TimeUnit.MILLISECONDS);
    connectionManager.setValidateAfterInactivity(-1);

    return HttpClientBuilder.create()
        .useSystemProperties()
        .setSSLSocketFactory(SSLConnectionSocketFactory.getSocketFactory())
        .setDefaultSocketConfig(socketConfig)
        .setMaxConnTotal(200)
        .setMaxConnPerRoute(20)
        .setRoutePlanner(new SystemDefaultRoutePlanner(ProxySelector.getDefault()))
        .setConnectionManager(connectionManager)
        .disableRedirectHandling()
        .disableAutomaticRetries();
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
  @VisibleForTesting
  Connection(URL url, HttpTransport transport) {
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
