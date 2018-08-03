/*
 * Copyright 2018 Google LLC. All rights reserved.
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

package com.google.cloud.tools.jib.registry;

import com.google.api.client.http.GenericUrl;
import com.google.cloud.tools.jib.JibLogger;
import com.google.cloud.tools.jib.http.Connection;
import com.google.cloud.tools.jib.http.Request;
import com.google.cloud.tools.jib.http.Response;
import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.util.function.Function;
import javax.annotation.Nullable;
import javax.net.ssl.SSLPeerUnverifiedException;
import org.apache.http.conn.HttpHostConnectException;

class EndpointCaller {

  private static boolean isHttpsProtocol(URL url) {
    return "https".equals(url.getProtocol());
  }

  private final JibLogger logger;
  private final boolean allowInsecureEndoints;

  private final Function<URL, Connection> connectionFactory;
  @Nullable private Function<URL, Connection> insecureConnectionFactory;

  EndpointCaller(JibLogger logger, boolean allowInsecureEndpoints) {
    this(
        logger,
        allowInsecureEndpoints,
        Connection.getConnectionFactory(),
        null /* might never be used, so create lazily to delay throwing potential GeneralSecurityException */);
  }

  @VisibleForTesting
  EndpointCaller(
      JibLogger logger,
      boolean allowInsecureEndpoints,
      Function<URL, Connection> connectionFactory,
      @Nullable Function<URL, Connection> insecureConnectionFactory) {
    this.logger = logger;
    allowInsecureEndoints = allowInsecureEndpoints;
    this.connectionFactory = connectionFactory;
    this.insecureConnectionFactory = insecureConnectionFactory;
  }

  // TODO: maybe "url" and "httpMethod" should be part of "request" (the pattern of the Google HTTP
  // Client Library)?
  public Response call(URL url, String httpMethod, Request request)
      throws IOException, EndpointException {
    if (!isHttpsProtocol(url) && !allowInsecureEndoints) {
      throw new InsecureEndpointException(url);
    }

    try {
      return sendRequest(url, httpMethod, request, connectionFactory);

    } catch (SSLPeerUnverifiedException ex) {
      return handleUnverifiableServerException(url, httpMethod, request);

    } catch (HttpHostConnectException ex) {
      if (allowInsecureEndoints && isHttpsProtocol(url) && url.getPort() == -1) {
        // Fall back to HTTP only if "url" had no port specified (i.e., we tried the default HTTPS
        // port 443) and we could not connect to 443. It's worth trying port 80.
        return fallBackToHttp(url, httpMethod, request);
      }
      throw ex;
    }
  }

  private Response handleUnverifiableServerException(URL url, String httpMethod, Request request)
      throws IOException, EndpointException {
    if (!allowInsecureEndoints) {
      throw new InsecureEndpointException(url);
    }

    try {
      logger.warn(
          "Cannot verify server at " + url + ". Attempting again with no TLS verification.");
      return sendRequest(url, httpMethod, request, getInsecureConnectionFactory());

    } catch (SSLPeerUnverifiedException ex) {
      return fallBackToHttp(url, httpMethod, request);
    }
  }

  private Response fallBackToHttp(URL url, String httpMethod, Request request)
      throws IOException, EndpointException {
    GenericUrl httpUrl = new GenericUrl(url);
    httpUrl.setScheme("http");
    logger.warn(
        "Failed to connect to " + url + " over HTTPS. Attempting again with HTTP: " + httpUrl);
    return sendRequest(httpUrl.toURL(), httpMethod, request, connectionFactory);
  }

  private Function<URL, Connection> getInsecureConnectionFactory() throws EndpointException {
    try {
      if (insecureConnectionFactory == null) {
        insecureConnectionFactory = Connection.getInsecureConnectionFactory();
      }
      return insecureConnectionFactory;

    } catch (GeneralSecurityException ex) {
      throw new EndpointException("cannot turn off TLS peer verification", ex);
    }
  }

  private Response sendRequest(
      URL url, String httpMethod, Request request, Function<URL, Connection> connectionFactory)
      throws IOException {
    try (Connection connection = connectionFactory.apply(url)) {
      return connection.send(httpMethod, request);
    }
  }
}
