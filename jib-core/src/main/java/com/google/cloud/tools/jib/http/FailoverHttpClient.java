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
import com.google.api.client.http.HttpBackOffIOExceptionHandler;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpIOExceptionHandler;
import com.google.api.client.http.HttpMethods;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.apache.v2.ApacheHttpTransport;
import com.google.api.client.util.ExponentialBackOff;
import com.google.api.client.util.SslUtils;
import com.google.cloud.tools.jib.api.LogEvent;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.net.ssl.SSLException;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.HttpClientBuilder;

/**
 * Thread-safe HTTP client that can automatically failover from secure HTTPS to insecure HTTPS or
 * HTTP. Intended to be created once and shared to be called at multiple places. Callers should
 * close the returned {@link Response}.
 *
 * <p>The failover (if enabled) in the following way:
 *
 * <ul>
 *   <li>When a port is provided (for example {@code my-registry:5000/my-repo}):
 *       <ol>
 *         <li>Attempts secure HTTPS on the specified port.
 *         <li>If (1) fails due to {@link SSLException}, re-attempts secure HTTPS on the specified
 *             port but disabling certificate validation.
 *         <li>If (2) fails again due to {@link SSLException}, attempts plain-HTTP on the specified
 *             port.
 *       </ol>
 *   <li>When a port is not provided (for example {@code my-registry/my-repo}):
 *       <ol>
 *         <li>Attempts secure HTTPS on port 443 (default HTTPS port).
 *         <li>If (1) fails due to {@link SSLException}, re-attempts secure HTTPS on port 443 but
 *             disabling certificate validation.
 *         <li>If (2) fails again due to {@link SSLException}, attempts plain-HTTP on port 80
 *             (default HTTP port).
 *         <li>Or, if (1) fails due to non-timeout {@link ConnectException}, attempts plain-HTTP on
 *             port 80.
 *       </ol>
 * </ul>
 *
 * <p>This failover behavior is similar to how the Docker client works:
 * https://docs.docker.com/registry/insecure/#deploy-a-plain-http-registry
 */
public class FailoverHttpClient {

  /** Represents failover actions taken. To be recorded in the failover history. */
  private static enum Failover {
    NONE, // no failover (secure HTTPS)
    INSECURE_HTTPS, // HTTPS with certificate validation disabled
    HTTP // plain HTTP
  }

  private static boolean isHttpsProtocol(URL url) {
    return "https".equals(url.getProtocol());
  }

  private static URL toHttp(URL url) {
    GenericUrl httpUrl = new GenericUrl(url);
    httpUrl.setScheme("http");
    return httpUrl.toURL();
  }

  private static HttpTransport getSecureHttpTransport() {
    // Do not use NetHttpTransport. It does not process response errors properly.
    // See https://github.com/google/google-http-java-client/issues/39
    //
    // A new ApacheHttpTransport needs to be created for each connection because otherwise HTTP
    // connection persistence causes the connection to throw NoHttpResponseException.
    HttpClientBuilder httpClientBuilder =
        ApacheHttpTransport.newDefaultHttpClientBuilder()
            // using "system socket factory" to enable sending client certificate
            // https://github.com/GoogleContainerTools/jib/issues/2585
            .setSSLSocketFactory(SSLConnectionSocketFactory.getSystemSocketFactory());
    return new ApacheHttpTransport(httpClientBuilder.build());
  }

  private static HttpTransport getInsecureHttpTransport() {
    try {
      HttpClientBuilder httpClientBuilder =
          ApacheHttpTransport.newDefaultHttpClientBuilder()
              .setSSLSocketFactory(null) // creates new factory with the SSLContext given below
              .setSSLContext(SslUtils.trustAllSSLContext())
              .setSSLHostnameVerifier(new NoopHostnameVerifier());
      // Do not use NetHttpTransport. See comments in getConnectionFactory for details.
      return new ApacheHttpTransport(httpClientBuilder.build());
    } catch (GeneralSecurityException ex) {
      throw new RuntimeException("platform does not support TLS protocol", ex);
    }
  }

  private final boolean enableHttpAndInsecureFailover;
  private final boolean sendAuthorizationOverHttp;
  private final Consumer<LogEvent> logger;
  private final Supplier<HttpTransport> secureHttpTransportFactory;
  private final Supplier<HttpTransport> insecureHttpTransportFactory;
  private final boolean retryOnIoException;

  private final ConcurrentHashMap<String, Failover> failoverHistory = new ConcurrentHashMap<>();

  private final Deque<HttpTransport> transportsCreated = new ArrayDeque<>();
  private final Deque<Response> responsesCreated = new ArrayDeque<>();

  /**
   * Create a new FailoverHttpclient.
   *
   * @param enableHttpAndInsecureFailover to enable automatic failover to insecure connection types
   * @param sendAuthorizationOverHttp allow sending auth over http connections
   * @param logger to receive log events
   */
  public FailoverHttpClient(
      boolean enableHttpAndInsecureFailover,
      boolean sendAuthorizationOverHttp,
      Consumer<LogEvent> logger) {
    this(enableHttpAndInsecureFailover, sendAuthorizationOverHttp, logger, true);
  }

  @VisibleForTesting
  FailoverHttpClient(
      boolean enableHttpAndInsecureFailover,
      boolean sendAuthorizationOverHttp,
      Consumer<LogEvent> logger,
      boolean retryOnIoException) {
    this(
        enableHttpAndInsecureFailover,
        sendAuthorizationOverHttp,
        logger,
        FailoverHttpClient::getSecureHttpTransport,
        FailoverHttpClient::getInsecureHttpTransport,
        retryOnIoException);
  }

  @VisibleForTesting
  FailoverHttpClient(
      boolean enableHttpAndInsecureFailover,
      boolean sendAuthorizationOverHttp,
      Consumer<LogEvent> logger,
      Supplier<HttpTransport> secureHttpTransportFactory,
      Supplier<HttpTransport> insecureHttpTransportFactory,
      boolean retryOnIoException) {
    this.enableHttpAndInsecureFailover = enableHttpAndInsecureFailover;
    this.sendAuthorizationOverHttp = sendAuthorizationOverHttp;
    this.logger = logger;
    this.secureHttpTransportFactory = secureHttpTransportFactory;
    this.insecureHttpTransportFactory = insecureHttpTransportFactory;
    this.retryOnIoException = retryOnIoException;
  }

  /**
   * Closes all connections and allocated resources, whether they are currently used or not.
   *
   * <p>If an I/O error occurs, shutdown attempts stop immediately, resulting in partial resource
   * release up to that point. The method can be called again later to re-attempt releasing all
   * resources.
   *
   * @throws IOException when I/O error shutting down resources
   */
  public void shutDown() throws IOException {
    synchronized (transportsCreated) {
      while (!transportsCreated.isEmpty()) {
        transportsCreated.peekFirst().shutdown();
        transportsCreated.removeFirst();
      }
    }
    synchronized (responsesCreated) {
      while (!responsesCreated.isEmpty()) {
        responsesCreated.peekFirst().close();
        responsesCreated.removeFirst();
      }
    }
  }

  /**
   * Sends the request with method GET.
   *
   * @param url endpoint URL
   * @param request the request to send
   * @return the response to the sent request
   * @throws IOException if sending the request fails
   */
  public Response get(URL url, Request request) throws IOException {
    return call(HttpMethods.GET, url, request);
  }

  /**
   * Sends the request with method POST.
   *
   * @param url endpoint URL
   * @param request the request to send
   * @return the response to the sent request
   * @throws IOException if sending the request fails
   */
  public Response post(URL url, Request request) throws IOException {
    return call(HttpMethods.POST, url, request);
  }

  /**
   * Sends the request with method PUT.
   *
   * @param url endpoint URL
   * @param request the request to send
   * @return the response to the sent request
   * @throws IOException if sending the request fails
   */
  public Response put(URL url, Request request) throws IOException {
    return call(HttpMethods.PUT, url, request);
  }

  /**
   * Sends the request.
   *
   * @param httpMethod the HTTP request method
   * @param url endpoint URL
   * @param request the request to send
   * @return the response to the sent request
   * @throws IOException if building the HTTP request fails.
   */
  public Response call(String httpMethod, URL url, Request request) throws IOException {
    if (!isHttpsProtocol(url)) {
      if (enableHttpAndInsecureFailover) { // HTTP requested. We only care if HTTP is enabled.
        return call(httpMethod, url, request, getHttpTransport(true));
      }
      throw new SSLException("insecure HTTP connection not allowed: " + url);
    }

    Optional<Response> fastPathResponse = followFailoverHistory(httpMethod, url, request);
    if (fastPathResponse.isPresent()) {
      return fastPathResponse.get();
    }

    try {
      return call(httpMethod, url, request, getHttpTransport(true));

    } catch (SSLException ex) {
      if (!enableHttpAndInsecureFailover) {
        throw ex;
      }

      try {
        logInsecureHttpsFailover(url);
        Response response = call(httpMethod, url, request, getHttpTransport(false));
        failoverHistory.put(url.getHost() + ":" + url.getPort(), Failover.INSECURE_HTTPS);
        return response;

      } catch (SSLException ignored) { // This is usually when the server is plain-HTTP.
        logHttpFailover(url);
        Response response = call(httpMethod, toHttp(url), request, getHttpTransport(true));
        failoverHistory.put(url.getHost() + ":" + url.getPort(), Failover.HTTP);
        return response;
      }

    } catch (ConnectException ex) {
      // It is observed that Open/Oracle JDKs sometimes throw SocketTimeoutException but other times
      // ConnectException for connection timeout. (Could be a JDK bug.) Note SocketTimeoutException
      // does not extend ConnectException (or vice versa), and we want to be consistent to error out
      // on timeouts: https://github.com/GoogleContainerTools/jib/issues/1895#issuecomment-527544094
      if (ex.getMessage() == null || !ex.getMessage().contains("timed out")) {
        // Fall back to HTTP only if "url" had no port specified (i.e., we tried the default HTTPS
        // port 443) and we could not connect to 443. It's worth trying port 80.
        if (enableHttpAndInsecureFailover && isHttpsProtocol(url) && url.getPort() == -1) {
          logHttpFailover(url);
          Response response = call(httpMethod, toHttp(url), request, getHttpTransport(true));
          failoverHistory.put(url.getHost() + ":" + url.getPort(), Failover.HTTP);
          return response;
        }
      }
      throw ex;
    }
  }

  private Optional<Response> followFailoverHistory(String httpMethod, URL url, Request request)
      throws IOException {
    Preconditions.checkArgument(isHttpsProtocol(url));
    switch (failoverHistory.getOrDefault(url.getHost() + ":" + url.getPort(), Failover.NONE)) {
      case HTTP:
        return Optional.of(call(httpMethod, toHttp(url), request, getHttpTransport(true)));
      case INSECURE_HTTPS:
        return Optional.of(call(httpMethod, url, request, getHttpTransport(false)));
      default:
        return Optional.empty(); // No history found. Should go for normal execution path.
    }
  }

  private Response call(String httpMethod, URL url, Request request, HttpTransport httpTransport)
      throws IOException {
    boolean clearAuthorization = !isHttpsProtocol(url) && !sendAuthorizationOverHttp;

    HttpHeaders requestHeaders =
        clearAuthorization
            ? request.getHeaders().clone().setAuthorization((String) null) // deep clone implemented
            : request.getHeaders();

    HttpRequest httpRequest =
        httpTransport
            .createRequestFactory()
            .buildRequest(httpMethod, new GenericUrl(url), request.getHttpContent())
            .setIOExceptionHandler(createBackOffRetryHandler())
            .setUseRawRedirectUrls(true)
            .setHeaders(requestHeaders);
    if (request.getHttpTimeout() != null) {
      httpRequest.setConnectTimeout(request.getHttpTimeout());
      httpRequest.setReadTimeout(request.getHttpTimeout());
    }

    try {
      Response response = new Response(httpRequest.execute());
      synchronized (responsesCreated) {
        responsesCreated.add(response);
      }
      return response;
    } catch (HttpResponseException ex) {
      throw new ResponseException(ex, clearAuthorization);
    }
  }

  private HttpIOExceptionHandler createBackOffRetryHandler() {
    return new HttpBackOffIOExceptionHandler(new ExponentialBackOff()) {
      @Override
      public boolean handleIOException(HttpRequest request, boolean supportsRetry)
          throws IOException {
        String requestUrl = request.getRequestMethod() + " " + request.getUrl();
        if (retryOnIoException && super.handleIOException(request, supportsRetry)) {
          logger.accept(LogEvent.warn(requestUrl + " failed and will be retried"));
          return true;
        }
        logger.accept(LogEvent.warn(requestUrl + " failed and will NOT be retried"));
        return false;
      }
    };
  }

  private HttpTransport getHttpTransport(boolean secureTransport) {
    HttpTransport transport =
        secureTransport ? secureHttpTransportFactory.get() : insecureHttpTransportFactory.get();
    synchronized (transportsCreated) {
      transportsCreated.add(transport);
    }
    return transport;
  }

  private void logHttpFailover(URL url) {
    String log = "Failed to connect to " + url + " over HTTPS. Attempting again with HTTP.";
    logger.accept(LogEvent.warn(log));
  }

  private void logInsecureHttpsFailover(URL url) {
    String log = "Cannot verify server at " + url + ". Attempting again with no TLS verification.";
    logger.accept(LogEvent.warn(log));
  }
}
