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

package com.google.cloud.tools.crepecake.registry;

import com.google.api.client.http.HttpResponseException;
import com.google.cloud.tools.crepecake.http.Request;
import com.google.cloud.tools.crepecake.http.Response;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Provides implementations for a registry endpoint. Implementations should be immutable.
 *
 * @param <T> the type returned from handling the endpoint response
 */
interface RegistryEndpointProvider<T> {

  /** @return the HTTP method to send the request with */
  String getHttpMethod();

  /**
   * @param apiRouteBase the registry's base URL (for example, {@code https://gcr.io/v2/})
   * @return the registry endpoint URL
   */
  URL getApiRoute(String apiRouteBase) throws MalformedURLException;

  /** Custom builder steps to add to build the request. */
  void buildRequest(Request.Builder builder);

  /** Handles the response specific to the registry action. */
  T handleResponse(Response response) throws IOException, RegistryException;

  /**
   * Handles an {@link HttpResponseException} that occurs.
   *
   * @param ex the {@link HttpResponseException} to handle
   * @throws HttpResponseException {@code ex} if {@code ex} could not be handled
   */
  default T handleHttpResponseException(HttpResponseException ex) throws IOException {
    throw ex;
  }

  /**
   * @return a description of the registry action performed, used in error messages to describe the
   *     action that failed
   */
  String getActionDescription();
}
