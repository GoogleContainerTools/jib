/*
 * Copyright 2018 Google LLC.
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

import com.google.api.client.http.HttpStatusCodes;
import com.google.cloud.tools.jib.api.InsecureRegistryException;
import com.google.cloud.tools.jib.api.LogEvent;
import com.google.cloud.tools.jib.api.RegistryException;
import com.google.cloud.tools.jib.api.RegistryUnauthorizedException;
import com.google.cloud.tools.jib.event.EventHandlers;
import com.google.cloud.tools.jib.global.JibSystemProperties;
import com.google.cloud.tools.jib.http.Authorization;
import com.google.cloud.tools.jib.http.Request;
import com.google.cloud.tools.jib.http.Response;
import com.google.cloud.tools.jib.http.ResponseException;
import com.google.cloud.tools.jib.http.TlsFailoverHttpClient;
import com.google.cloud.tools.jib.json.JsonTemplateMapper;
import com.google.cloud.tools.jib.registry.json.ErrorEntryTemplate;
import com.google.cloud.tools.jib.registry.json.ErrorResponseTemplate;
import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.net.URL;
import java.util.Locale;
import javax.annotation.Nullable;
import javax.net.ssl.SSLException;

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

  // https://github.com/GoogleContainerTools/jib/issues/1316
  @VisibleForTesting
  static boolean isBrokenPipe(IOException original) {
    Throwable exception = original;
    while (exception != null) {
      String message = exception.getMessage();
      if (message != null && message.toLowerCase(Locale.US).contains("broken pipe")) {
        return true;
      }

      exception = exception.getCause();
      if (exception == original) { // just in case if there's a circular chain
        return false;
      }
    }
    return false;
  }

  private final EventHandlers eventHandlers;
  private final String userAgent;
  private final RegistryEndpointProvider<T> registryEndpointProvider;
  @Nullable private final Authorization authorization;
  private final RegistryEndpointRequestProperties registryEndpointRequestProperties;
  private final TlsFailoverHttpClient httpClient;

  /**
   * Constructs with parameters for making the request.
   *
   * @param eventHandlers the event dispatcher used for dispatching log events
   * @param userAgent {@code User-Agent} header to send with the request
   * @param registryEndpointProvider the {@link RegistryEndpointProvider} to the endpoint
   * @param authorization optional authentication credentials to use
   * @param registryEndpointRequestProperties properties of the registry endpoint request
   */
  @VisibleForTesting
  RegistryEndpointCaller(
      EventHandlers eventHandlers,
      String userAgent,
      RegistryEndpointProvider<T> registryEndpointProvider,
      @Nullable Authorization authorization,
      RegistryEndpointRequestProperties registryEndpointRequestProperties,
      TlsFailoverHttpClient httpClient) {
    this.eventHandlers = eventHandlers;
    this.userAgent = userAgent;
    this.registryEndpointProvider = registryEndpointProvider;
    this.authorization = authorization;
    this.registryEndpointRequestProperties = registryEndpointRequestProperties;
    this.httpClient = httpClient;
  }

  /**
   * Makes the request to the endpoint.
   *
   * @return an object representing the response, or {@code null}
   * @throws IOException for most I/O exceptions when making the request
   * @throws RegistryException for known exceptions when interacting with the registry
   */
  T call() throws IOException, RegistryException {
    String apiRouteBase = "https://" + registryEndpointRequestProperties.getServerUrl() + "/v2/";
    URL initialRequestUrl = registryEndpointProvider.getApiRoute(apiRouteBase);
    return call(initialRequestUrl);
  }

  /**
   * Calls the registry endpoint with a certain {@link URL}.
   *
   * @param url the endpoint URL to call
   * @return an object representing the response
   * @throws IOException for most I/O exceptions when making the request
   * @throws RegistryException for known exceptions when interacting with the registry
   */
  private T call(URL url) throws IOException, RegistryException {
    Request.Builder requestBuilder =
        Request.builder()
            .setUserAgent(userAgent)
            .setHttpTimeout(JibSystemProperties.getHttpTimeout())
            .setAccept(registryEndpointProvider.getAccept())
            .setBody(registryEndpointProvider.getContent())
            .setAuthorization(authorization);

    try (Response response =
        httpClient.call(registryEndpointProvider.getHttpMethod(), url, requestBuilder.build())) {

      return registryEndpointProvider.handleResponse(response);

    } catch (ResponseException ex) {
      // First, see if the endpoint provider handles an exception as an expected response.
      try {
        return registryEndpointProvider.handleHttpResponseException(ex);

      } catch (ResponseException responseException) {
        if (responseException.getStatusCode() == HttpStatusCodes.STATUS_CODE_BAD_REQUEST
            || responseException.getStatusCode() == HttpStatusCodes.STATUS_CODE_NOT_FOUND
            || responseException.getStatusCode()
                == HttpStatusCodes.STATUS_CODE_METHOD_NOT_ALLOWED) {
          // The name or reference was invalid.
          throw newRegistryErrorException(responseException);

        } else if (responseException.getStatusCode() == HttpStatusCodes.STATUS_CODE_FORBIDDEN) {
          throw new RegistryUnauthorizedException(
              registryEndpointRequestProperties.getServerUrl(),
              registryEndpointRequestProperties.getImageName(),
              responseException);

        } else if (responseException.getStatusCode() == HttpStatusCodes.STATUS_CODE_UNAUTHORIZED) {
          if (responseException.requestAuthorizationCleared()) {
            throw new RegistryCredentialsNotSentException(
                registryEndpointRequestProperties.getServerUrl(),
                registryEndpointRequestProperties.getImageName());
          } else {
            // Credentials are either missing or wrong.
            throw new RegistryUnauthorizedException(
                registryEndpointRequestProperties.getServerUrl(),
                registryEndpointRequestProperties.getImageName(),
                responseException);
          }

        } else if (responseException.getStatusCode()
                == HttpStatusCodes.STATUS_CODE_TEMPORARY_REDIRECT
            || responseException.getStatusCode() == HttpStatusCodes.STATUS_CODE_MOVED_PERMANENTLY
            || responseException.getStatusCode() == STATUS_CODE_PERMANENT_REDIRECT) {
          // 'Location' header can be relative or absolute.
          URL redirectLocation = new URL(url, responseException.getHeaders().getLocation());
          return call(redirectLocation);

        } else {
          // Unknown
          throw responseException;
        }
      }

    } catch (SSLException ex) {
      throw new InsecureRegistryException(url);

    } catch (IOException ex) {
      String registry = registryEndpointRequestProperties.getServerUrl();
      String repository = registryEndpointRequestProperties.getImageName();
      logError("I/O error for image [" + registry + "/" + repository + "]:");
      logError("    " + ex.getMessage());
      if (isBrokenPipe(ex)) {
        logError(
            "broken pipe: the server shut down the connection. Check the server log if possible. "
                + "This could also be a proxy issue. For example, a proxy may prevent sending "
                + "packets that are too large.");
      }
      throw ex;
    }
  }

  @VisibleForTesting
  RegistryErrorException newRegistryErrorException(ResponseException responseException) {
    RegistryErrorExceptionBuilder registryErrorExceptionBuilder =
        new RegistryErrorExceptionBuilder(
            registryEndpointProvider.getActionDescription(), responseException);

    try {
      ErrorResponseTemplate errorResponse =
          JsonTemplateMapper.readJson(responseException.getContent(), ErrorResponseTemplate.class);
      for (ErrorEntryTemplate errorEntry : errorResponse.getErrors()) {
        registryErrorExceptionBuilder.addReason(errorEntry);
      }
    } catch (IOException ex) {
      registryErrorExceptionBuilder.addReason(
          "registry returned error code "
              + responseException.getStatusCode()
              + "; possible causes include invalid or wrong reference. Actual error output follows:\n"
              + responseException.getContent()
              + "\n");
    }

    return registryErrorExceptionBuilder.build();
  }

  /** Logs error message in red. */
  private void logError(String message) {
    eventHandlers.dispatch(LogEvent.error("\u001B[31;1m" + message + "\u001B[0m"));
  }
}
