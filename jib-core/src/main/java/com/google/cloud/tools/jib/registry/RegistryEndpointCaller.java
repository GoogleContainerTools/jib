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

  /**
   * @see <a
   *     href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Status/308">https://developer.mozilla.org/en-US/docs/Web/HTTP/Status/308</a>
   */
  @VisibleForTesting static final int STATUS_CODE_PERMANENT_REDIRECT = 308;

  private static final String DEFAULT_PROTOCOL = "https";

  /** Maintains the state of a request. This is used to retry requests with different parameters. */
  @VisibleForTesting
  static class RequestState {

    @Nullable private final Authorization authorization;
    private final URL url;

    /**
     * @param authorization authentication credentials
     * @param url the endpoint URL to call
     */
    @VisibleForTesting
    RequestState(@Nullable Authorization authorization, URL url) {
      this.authorization = authorization;
      this.url = url;
    }
  }

  /** Makes a {@link Connection} to the specified {@link URL}. */
  private final Function<URL, Connection> connectionFactory;

  private final RequestState initialRequestState;
  private final String userAgent;
  private final RegistryEndpointProvider<T> registryEndpointProvider;
  private final RegistryEndpointRequestProperties registryEndpointRequestProperties;
  private final boolean allowHttp;

  /**
   * Constructs with parameters for making the request.
   *
   * @param userAgent {@code User-Agent} header to send with the request
   * @param apiRouteBase the endpoint's API root, without the protocol
   * @param registryEndpointProvider the {@link RegistryEndpointProvider} to the endpoint
   * @param authorization optional authentication credentials to use
   * @param registryEndpointRequestProperties properties of the registry endpoint request
   * @param allowHttp if {@code true}, allows redirects and fallbacks to HTTP; otherwise, only
   *     allows HTTPS
   * @throws MalformedURLException if the URL generated for the endpoint is malformed
   */
  RegistryEndpointCaller(
      String userAgent,
      String apiRouteBase,
      RegistryEndpointProvider<T> registryEndpointProvider,
      @Nullable Authorization authorization,
      RegistryEndpointRequestProperties registryEndpointRequestProperties,
      boolean allowHttp)
      throws MalformedURLException {
    this(
        userAgent,
        apiRouteBase,
        registryEndpointProvider,
        authorization,
        registryEndpointRequestProperties,
        allowHttp,
        Connection::new);
  }

  @VisibleForTesting
  RegistryEndpointCaller(
      String userAgent,
      String apiRouteBase,
      RegistryEndpointProvider<T> registryEndpointProvider,
      @Nullable Authorization authorization,
      RegistryEndpointRequestProperties registryEndpointRequestProperties,
      boolean allowHttp,
      Function<URL, Connection> connectionFactory)
      throws MalformedURLException {
    this.initialRequestState =
        new RequestState(
            authorization,
            registryEndpointProvider.getApiRoute(DEFAULT_PROTOCOL + "://" + apiRouteBase));
    this.userAgent = userAgent;
    this.registryEndpointProvider = registryEndpointProvider;
    this.registryEndpointRequestProperties = registryEndpointRequestProperties;
    this.allowHttp = allowHttp;
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
  @VisibleForTesting
  @Nullable
  T call(RequestState requestState) throws IOException, RegistryException {
    boolean isHttpProtocol = "http".equals(requestState.url.getProtocol());
    if (!allowHttp && isHttpProtocol) {
      throw new InsecureRegistryException(requestState.url);
    }

    try (Connection connection = connectionFactory.apply(requestState.url)) {
      Request.Builder requestBuilder =
          Request.builder()
              .setUserAgent(userAgent)
              .setHttpTimeout(Integer.getInteger("jib.httpTimeout"))
              .setAccept(registryEndpointProvider.getAccept())
              .setBody(registryEndpointProvider.getContent());
      // Only sends authorization if using HTTPS.
      if (!isHttpProtocol || Boolean.getBoolean("sendCredentialsOverHttp")) {
        requestBuilder.setAuthorization(requestState.authorization);
      }
      Response response =
          connection.send(registryEndpointProvider.getHttpMethod(), requestBuilder.build());

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

        } else if (httpResponseException.getStatusCode() == HttpStatusCodes.STATUS_CODE_FORBIDDEN) {
          throw new RegistryUnauthorizedException(
              registryEndpointRequestProperties.getServerUrl(),
              registryEndpointRequestProperties.getImageName(),
              httpResponseException);

        } else if (httpResponseException.getStatusCode()
            == HttpStatusCodes.STATUS_CODE_UNAUTHORIZED) {
          if (isHttpProtocol) {
            // Using HTTP, so credentials weren't sent.
            throw new RegistryCredentialsNotSentException(
                registryEndpointRequestProperties.getServerUrl(),
                registryEndpointRequestProperties.getImageName());

          } else {
            // Using HTTPS, so credentials are missing.
            throw new RegistryUnauthorizedException(
                registryEndpointRequestProperties.getServerUrl(),
                registryEndpointRequestProperties.getImageName(),
                httpResponseException);
          }

        } else if (httpResponseException.getStatusCode()
                == HttpStatusCodes.STATUS_CODE_TEMPORARY_REDIRECT
            || httpResponseException.getStatusCode()
                == HttpStatusCodes.STATUS_CODE_MOVED_PERMANENTLY
            || httpResponseException.getStatusCode() == STATUS_CODE_PERMANENT_REDIRECT) {
          // 'Location' header can be relative or absolute.
          URL redirectLocation =
              new URL(requestState.url, httpResponseException.getHeaders().getLocation());
          return call(new RequestState(requestState.authorization, redirectLocation));

        } else {
          // Unknown
          throw httpResponseException;
        }
      }

    } catch (HttpHostConnectException | SSLPeerUnverifiedException ex) {
      // Tries to call with HTTP protocol if HTTPS failed to connect.
      // Note that this will not succeed if 'allowHttp' is false.
      if ("https".equals(requestState.url.getProtocol())) {
        GenericUrl httpUrl = new GenericUrl(requestState.url);
        httpUrl.setScheme("http");
        return call(new RequestState(requestState.authorization, httpUrl.toURL()));
      }

      throw ex;

    } catch (NoHttpResponseException ex) {
      throw new RegistryNoResponseException(ex);
    }
  }
}
