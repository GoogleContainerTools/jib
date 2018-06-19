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
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.HttpStatusCodes;
import com.google.cloud.tools.jib.http.Authorization;
import com.google.cloud.tools.jib.http.Connection;
import com.google.cloud.tools.jib.http.Request;
import com.google.cloud.tools.jib.http.Response;
import com.google.cloud.tools.jib.json.JsonTemplateMapper;
import com.google.cloud.tools.jib.registry.json.ErrorEntryTemplate;
import com.google.cloud.tools.jib.registry.json.ErrorResponseTemplate;
import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.function.Function;
import javax.annotation.Nullable;
import javax.net.ssl.SSLPeerUnverifiedException;
import org.apache.http.NoHttpResponseException;
import org.apache.http.conn.HttpHostConnectException;

/**
 * Makes requests to a registry endpoint.
 *
 * @param <T> the type returned by calling the endpoint
 */
class RegistryEndpointCaller<T> {

  private static final String DEFAULT_PROTOCOL = "https";

  /** Maintains the state of a request. This is used to retry requests with different parameters. */
  private static class RequestState {

    @Nullable private final Authorization authorization;
    private final URL url;

    /**
     * @param authorization authentication credentials
     * @param url the endpoint URL to call
     */
    private RequestState(@Nullable Authorization authorization, URL url) {
      this.authorization = authorization;
      this.url = url;
    }
  }

  /**
   * Converts the {@link URL}'s protocol to HTTP.
   *
   * @param url the URL to conver to HTTP
   * @return the URL with protocol set to HTTP
   */
  private static URL urlWithHttp(URL url) {
    GenericUrl httpUrl = new GenericUrl(url);
    httpUrl.setScheme("http");
    return httpUrl.toURL();
  }

  /** Makes a {@link Connection} to the specified {@link URL}. */
  private final Function<URL, Connection> connectionFactory;

  private final RequestState initialRequestState;
  private final String userAgent;
  private final RegistryEndpointProvider<T> registryEndpointProvider;
  private final RegistryEndpointProperties registryEndpointProperties;

  /**
   * Constructs with parameters for making the request.
   *
   * @param userAgent {@code User-Agent} header to send with the request
   * @param apiRouteBase the endpoint's API root, without the protocol
   * @param registryEndpointProvider the {@link RegistryEndpointProvider} to the endpoint
   * @param authorization optional authentication credentials to use
   * @param registryEndpointProperties properties of the registry endpoint request
   * @throws MalformedURLException if the URL generated for the endpoint is malformed
   */
  RegistryEndpointCaller(
      String userAgent,
      String apiRouteBase,
      RegistryEndpointProvider<T> registryEndpointProvider,
      @Nullable Authorization authorization,
      RegistryEndpointProperties registryEndpointProperties)
      throws MalformedURLException {
    this(
        userAgent,
        apiRouteBase,
        registryEndpointProvider,
        authorization,
        registryEndpointProperties,
        Connection::new);
  }

  @VisibleForTesting
  RegistryEndpointCaller(
      String userAgent,
      String apiRouteBase,
      RegistryEndpointProvider<T> registryEndpointProvider,
      @Nullable Authorization authorization,
      RegistryEndpointProperties registryEndpointProperties,
      Function<URL, Connection> connectionFactory)
      throws MalformedURLException {
    this.initialRequestState =
        new RequestState(
            authorization,
            registryEndpointProvider.getApiRoute(DEFAULT_PROTOCOL + "://" + apiRouteBase));
    this.userAgent = userAgent;
    this.registryEndpointProvider = registryEndpointProvider;
    this.registryEndpointProperties = registryEndpointProperties;
    this.connectionFactory = connectionFactory;
  }

  /**
   * Makes the request to the endpoint.
   *
   * @return an object representing the response, or {@code null}
   * @throws IOException for most I/O exceptions when making the request
   * @throws RegistryException for known exceptions when interacting with the registry
   */
  @Nullable
  T call() throws IOException, RegistryException {
    return call(initialRequestState);
  }

  /**
   * Calls the registry endpoint with a certain {@link RequestState}.
   *
   * @param requestState the state of the request - determines how to make the request and how to
   *     process the response
   * @return an object representing the response, or {@code null}
   * @throws IOException for most I/O exceptions when making the request
   * @throws RegistryException for known exceptions when interacting with the registry
   */
  @Nullable
  private T call(RequestState requestState) throws IOException, RegistryException {
    try (Connection connection = connectionFactory.apply(requestState.url)) {
      Request request =
          Request.builder()
              .setAuthorization(requestState.authorization)
              .setUserAgent(userAgent)
              .setAccept(registryEndpointProvider.getAccept())
              .setBody(registryEndpointProvider.getContent())
              .build();
      Response response = connection.send(registryEndpointProvider.getHttpMethod(), request);

      return registryEndpointProvider.handleResponse(response);

    } catch (HttpResponseException ex) {
      // First, see if the endpoint provider handles an exception as an expected response.
      try {
        return registryEndpointProvider.handleHttpResponseException(ex);

      } catch (HttpResponseException httpResponseException) {
        if (httpResponseException.getStatusCode() == HttpStatusCodes.STATUS_CODE_BAD_REQUEST
            || httpResponseException.getStatusCode() == HttpStatusCodes.STATUS_CODE_NOT_FOUND
            || httpResponseException.getStatusCode()
                == HttpStatusCodes.STATUS_CODE_METHOD_NOT_ALLOWED) {
          // The name or reference was invalid.
          ErrorResponseTemplate errorResponse =
              JsonTemplateMapper.readJson(
                  httpResponseException.getContent(), ErrorResponseTemplate.class);
          RegistryErrorExceptionBuilder registryErrorExceptionBuilder =
              new RegistryErrorExceptionBuilder(
                  registryEndpointProvider.getActionDescription(), httpResponseException);
          for (ErrorEntryTemplate errorEntry : errorResponse.getErrors()) {
            registryErrorExceptionBuilder.addReason(errorEntry);
          }

          throw registryErrorExceptionBuilder.build();

        } else if (httpResponseException.getStatusCode() == HttpStatusCodes.STATUS_CODE_UNAUTHORIZED
            || httpResponseException.getStatusCode() == HttpStatusCodes.STATUS_CODE_FORBIDDEN) {
          throw new RegistryUnauthorizedException(
              registryEndpointProperties.getServerUrl(),
              registryEndpointProperties.getImageName(),
              httpResponseException);

        } else if (httpResponseException.getStatusCode()
            == HttpStatusCodes.STATUS_CODE_TEMPORARY_REDIRECT) {
          // TODO: Use copy-construct builder.
          return call(
              new RequestState(
                  requestState.authorization,
                  new URL(httpResponseException.getHeaders().getLocation())));

        } else {
          // Unknown
          throw httpResponseException;
        }
      }

    } catch (HttpHostConnectException | SSLPeerUnverifiedException ex) {
      // Tries to call with HTTP protocol if HTTPS failed to connect.
      if ("https".equals(requestState.url.getProtocol())) {
        return call(new RequestState(requestState.authorization, urlWithHttp(requestState.url)));
      }

      throw ex;

    } catch (NoHttpResponseException ex) {
      throw new RegistryNoResponseException(ex);
    }
  }
}
