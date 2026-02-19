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

import com.google.api.client.http.HttpMethods;
import com.google.api.client.http.HttpStatusCodes;
import com.google.cloud.tools.jib.api.RegistryAuthenticationFailedException;
import com.google.cloud.tools.jib.http.BlobHttpContent;
import com.google.cloud.tools.jib.http.FailoverHttpClient;
import com.google.cloud.tools.jib.http.Response;
import com.google.cloud.tools.jib.http.ResponseException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;

/** Retrieves the {@code WWW-Authenticate} header from the registry API. */
class AuthenticationMethodRetriever
    implements RegistryEndpointProvider<Optional<RegistryAuthenticator>> {

  private final RegistryEndpointRequestProperties registryEndpointRequestProperties;
  @Nullable private final String userAgent;
  private final FailoverHttpClient httpClient;

  AuthenticationMethodRetriever(
      RegistryEndpointRequestProperties registryEndpointRequestProperties,
      @Nullable String userAgent,
      FailoverHttpClient httpClient) {
    this.registryEndpointRequestProperties = registryEndpointRequestProperties;
    this.userAgent = userAgent;
    this.httpClient = httpClient;
  }

  @Nullable
  @Override
  public BlobHttpContent getContent() {
    return null;
  }

  @Override
  public List<String> getAccept() {
    return Collections.emptyList();
  }

  /**
   * The request did not error, meaning that the registry does not require authentication.
   *
   * @param response ignored
   * @return {@link Optional#empty()}
   */
  @Override
  public Optional<RegistryAuthenticator> handleResponse(Response response) {
    return Optional.empty();
  }

  @Override
  public URL getApiRoute(String apiRouteBase) throws MalformedURLException {
    return new URL(apiRouteBase);
  }

  @Override
  public String getHttpMethod() {
    return HttpMethods.GET;
  }

  @Override
  public String getActionDescription() {
    return "retrieve authentication method for " + registryEndpointRequestProperties.getServerUrl();
  }

  @Override
  public Optional<RegistryAuthenticator> handleHttpResponseException(
      ResponseException responseException) throws ResponseException, RegistryErrorException {
    // Only valid for status code of '401 Unauthorized'.
    if (responseException.getStatusCode() != HttpStatusCodes.STATUS_CODE_UNAUTHORIZED) {
      throw responseException;
    }

    // Checks if the 'WWW-Authenticate' header is present.
    List<String> authList = responseException.getHeaders().getAuthenticateAsList();
    if (authList == null || authList.isEmpty()) {
      throw new RegistryErrorExceptionBuilder(getActionDescription(), responseException)
          .addReason("'WWW-Authenticate' header not found")
          .build();
    }

    // try all 'WWW-Authenticate' headers until a working RegistryAuthenticator can be created
    RegistryErrorException lastExc = null;
    for (String authenticationMethod : authList) {
      try {
        return RegistryAuthenticator.fromAuthenticationMethod(
            authenticationMethod, registryEndpointRequestProperties, userAgent, httpClient);
      } catch (RegistryAuthenticationFailedException ex) {
        if (lastExc == null) {
          lastExc =
              new RegistryErrorExceptionBuilder(getActionDescription(), ex)
                  .addReason(
                      "Failed getting supported authentication method from 'WWW-Authenticate' header")
                  .build();
        }
      }
    }

    // if none of the RegistryAuthenticators worked, throw the last stored exception
    throw lastExc;
  }
}
