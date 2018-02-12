/*
 * Copyright 2018 Google Inc.
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
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.HttpStatusCodes;
import com.google.cloud.tools.jib.http.BlobHttpContent;
import com.google.cloud.tools.jib.http.Response;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;

/** Retrieves the {@code WWW-Authenticate} header from the registry API. */
class AuthenticationMethodRetriever implements RegistryEndpointProvider<RegistryAuthenticator> {

  private final RegistryEndpointProperties registryEndpointProperties;

  @Nullable
  @Override
  public BlobHttpContent getContent() {
    return null;
  }

  @Override
  public List<String> getAccept() {
    return Collections.emptyList();
  }

  @Override
  @Nullable
  public RegistryAuthenticator handleResponse(Response response) {
    // The registry does not require authentication.
    return null;
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
    return "retrieve authentication method for " + registryEndpointProperties.getServerUrl();
  }

  @Override
  @Nullable
  public RegistryAuthenticator handleHttpResponseException(
      HttpResponseException httpResponseException)
      throws HttpResponseException, RegistryErrorException {
    // Only valid for status code of '401 Unauthorized'.
    if (httpResponseException.getStatusCode() != HttpStatusCodes.STATUS_CODE_UNAUTHORIZED) {
      throw httpResponseException;
    }

    // Checks if the 'WWW-Authenticate' header is present.
    String authenticationMethod = httpResponseException.getHeaders().getAuthenticate();
    if (authenticationMethod == null) {
      throw new RegistryErrorExceptionBuilder(getActionDescription(), httpResponseException)
          .addReason("'WWW-Authenticate' header not found")
          .build();
    }

    // Parses the header to retrieve the components.
    try {
      return RegistryAuthenticator.fromAuthenticationMethod(
          authenticationMethod, registryEndpointProperties.getImageName());

    } catch (RegistryAuthenticationFailedException | MalformedURLException ex) {
      throw new RegistryErrorExceptionBuilder(getActionDescription(), ex)
          .addReason("Failed get authentication method from 'WWW-Authenticate' header")
          .build();
    }
  }

  AuthenticationMethodRetriever(RegistryEndpointProperties registryEndpointProperties) {
    this.registryEndpointProperties = registryEndpointProperties;
  }
}
