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
import com.google.api.client.http.apache.ApacheHttpTransport;
import com.google.cloud.tools.jib.api.LogEvent;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import javax.net.ssl.SSLException;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.DefaultHttpClient;

/** */
public class Connection2 {

  public static Connection2 create(boolean enableInsecureFailover) {
    // Do not use NetHttpTransport. It does not process response errors properly.
    // See https://github.com/google/google-http-java-client/issues/39
    //
    // A new ApacheHttpTransport needs to be created for each connection because otherwise HTTP
    // connection persistence causes the connection to throw NoHttpResponseException.
    ApacheHttpTransport transport = new ApacheHttpTransport();
    addProxyCredentials(transport);

    HttpRequestFactory insecureRequestFactory = null;
    if (enableInsecureFailover) {
      try {
        ApacheHttpTransport insecureTransport =
            new ApacheHttpTransport.Builder().doNotValidateCertificate().build();
        addProxyCredentials(insecureTransport);
      } catch (GeneralSecurityException ex) {
        throw new RuntimeException("platform does not support TSL protocol", ex);
      }
    }

    return new Connection2(transport.createRequestFactory(), insecureRequestFactory);
  }

  private static boolean isHttpsProtocol(URL url) {
    return "https".equals(url.getProtocol());
  }

  private static URL toHttp(URL url) {
    GenericUrl httpUrl = new GenericUrl(url);
    httpUrl.setScheme("http");
    return httpUrl.toURL();
  }

  /**
   * Registers proxy credentials onto transport client, in order to deal with proxies that require
   * basic authentication.
   *
   * @param transport Apache HTTP transport
   */
  @VisibleForTesting
  static void addProxyCredentials(ApacheHttpTransport transport) {
    addProxyCredentials(transport, "https");
    addProxyCredentials(transport, "http");
  }

  private static void addProxyCredentials(ApacheHttpTransport transport, String protocol) {
    Preconditions.checkArgument(protocol.equals("http") || protocol.equals("https"));

    String proxyHost = System.getProperty(protocol + ".proxyHost");
    String proxyUser = System.getProperty(protocol + ".proxyUser");
    String proxyPassword = System.getProperty(protocol + ".proxyPassword");
    if (proxyHost == null || proxyUser == null || proxyPassword == null) {
      return;
    }

    String defaultProxyPort = protocol.equals("http") ? "80" : "443";
    int proxyPort = Integer.parseInt(System.getProperty(protocol + ".proxyPort", defaultProxyPort));

    DefaultHttpClient httpClient = (DefaultHttpClient) transport.getHttpClient();
    httpClient
        .getCredentialsProvider()
        .setCredentials(
            new AuthScope(proxyHost, proxyPort),
            new UsernamePasswordCredentials(proxyUser, proxyPassword));
  }

  private final HttpRequestFactory requestFactory;
  @Nullable private final HttpRequestFactory insecureRequestFactory;
  private final Consumer<LogEvent> logger;

  Connection2(
      HttpRequestFactory requestFactory, @Nullable HttpRequestFactory insecureRequestFactory) {
    this.requestFactory = requestFactory;
    this.insecureRequestFactory = insecureRequestFactory;
    this.logger = ignored -> {};
  }

  /**
   * Sends the request with method GET.
   *
   * @param url
   * @param request the request to send
   * @return the response to the sent request
   * @throws IOException if sending the request fails
   */
  public Response2 get(URL url, Request request) throws IOException {
    return call(HttpMethods.GET, url, request);
  }

  /**
   * Sends the request with method POST.
   *
   * @param url
   * @param request the request to send
   * @return the response to the sent request
   * @throws IOException if sending the request fails
   */
  public Response2 post(URL url, Request request) throws IOException {
    return call(HttpMethods.POST, url, request);
  }

  /**
   * Sends the request with method PUT.
   *
   * @param url
   * @param request the request to send
   * @return the response to the sent request
   * @throws IOException if sending the request fails
   */
  public Response2 put(URL url, Request request) throws IOException {
    return call(HttpMethods.PUT, url, request);
  }

  /**
   * Sends the request.
   *
   * @param httpMethod the HTTP request method
   * @param url
   * @param request the request to send
   * @return the response to the sent request
   * @throws IOException if building the HTTP request fails.
   */
  public Response2 call(String httpMethod, URL url, Request request) throws IOException {
    boolean insecureFailoverEnabled = insecureRequestFactory != null;

    if (!isHttpsProtocol(url) && !insecureFailoverEnabled) {
      throw new IOException("insecure HTTP connection not allowed: " + url);
      // throw new InsecureFailoverDisabledException(": " + url, ex);
    }

    try {
      return call(httpMethod, url, request, requestFactory);

    } catch (SSLException ex) {
      if (!insecureFailoverEnabled) {
        throw new IOException("insecure HTTPS not allowed: " + url, ex);
        // throw new InsecureFailoverDisabledException(": " + url, ex);
      }

      try {
        logInsecureHttpsFailover(url);
        return call(httpMethod, url, request, insecureRequestFactory);

      } catch (SSLException ignored) {
        logHttpFailover(url);
        return call(httpMethod, toHttp(url), request, requestFactory);
      }

    } catch (ConnectException ex) {
      // It is observed that Open/Oracle JDKs sometimes throw SocketTimeoutException but other times
      // ConnectException for connection timeout. (Could be a JDK bug.) Note SocketTimeoutException
      // does not extend ConnectException (or vice versa), and we want to be consistent to error out
      // on timeouts: https://github.com/GoogleContainerTools/jib/issues/1895#issuecomment-527544094
      if (ex.getMessage() != null && ex.getMessage().contains("timed out")) {
        throw ex;
      }

      // Fall back to HTTP only if "url" had no port specified (i.e., we tried the default HTTPS
      // port 443) and we could not connect to 443. It's worth trying port 80.
      if (insecureFailoverEnabled && isHttpsProtocol(url) && url.getPort() == -1) {
        logHttpFailover(url);
        return call(httpMethod, toHttp(url), request, requestFactory);
      }
      throw ex;
    }
  }

  private static Response2 call(
      String httpMethod, URL url, Request request, HttpRequestFactory requestFactory)
      throws IOException {
    HttpRequest httpRequest =
        requestFactory
            .buildRequest(httpMethod, new GenericUrl(url), request.getHttpContent())
            .setHeaders(request.getHeaders());
    if (request.getHttpTimeout() != null) {
      httpRequest.setConnectTimeout(request.getHttpTimeout());
      httpRequest.setReadTimeout(request.getHttpTimeout());
    }

    return new Response2(httpRequest.execute());
  }

  private void logHttpFailover(URL url) {
    String log = "Failed to connect to " + url + " over HTTPS. Attempting again with HTTP.";
    logger.accept(LogEvent.lifecycle(log));
  }

  private void logInsecureHttpsFailover(URL url) {
    String log = "Cannot verify server at " + url + ". Attempting again with no TLS verification.";
    logger.accept(LogEvent.lifecycle(log));
  }
}
