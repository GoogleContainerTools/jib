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

import com.google.cloud.tools.crepecake.http.BlobHttpContent;
import com.google.cloud.tools.crepecake.http.Response;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import javax.annotation.Nullable;

/**
 * Provides implementations for a registry endpoint.
 *
 * @param <T> the type returned from handling the endpoint response
 */
interface RegistryEndpointProvider<T> {

  /** @return the {@link BlobHttpContent} to send as the request body */
  @Nullable
  BlobHttpContent getContent();

  /** Handles the response specific to the registry action. */
  T handleResponse(Response response) throws IOException, RegistryException;

  /**
   * @param apiRouteBase the registry's base URL (for example, {@code https://gcr.io/v2/})
   * @return the registry endpoint URL
   */
  URL getApiRoute(String apiRouteBase) throws MalformedURLException;

  /** @return the HTTP method to send the request with */
  String getHttpMethod();

  /**
   * @return a description of the registry action performed, used in error messages to describe the
   *     action that failed
   */
  String getActionDescription();
}
