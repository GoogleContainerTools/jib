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

import com.google.cloud.tools.jib.api.RegistryException;
import com.google.cloud.tools.jib.http.BlobHttpContent;
import com.google.cloud.tools.jib.http.Response;
import com.google.cloud.tools.jib.http.ResponseException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Provides implementations for a registry endpoint. Implementations should be immutable.
 *
 * @param <T> the type returned from handling the endpoint response
 */
interface RegistryEndpointProvider<T> {

  /** Returns the HTTP method to send the request with. */
  String getHttpMethod();

  /**
   * Returns the registry endpoint URL.
   *
   * @param apiRouteBase the registry's base URL (for example, {@code https://gcr.io/v2/})
   * @return the registry endpoint URL
   */
  URL getApiRoute(String apiRouteBase) throws MalformedURLException;

  /** Returns the {@link BlobHttpContent} to send as the request body. */
  @Nullable
  BlobHttpContent getContent();

  /** Returns a list of MIME types to pass as an HTTP {@code Accept} header. */
  List<String> getAccept();

  /** Handles the response specific to the registry action. */
  T handleResponse(Response response) throws IOException, RegistryException;

  /**
   * Handles an {@link ResponseException} that occurs. Implementation must re-throw the given
   * exception if it did not conclusively handled the response exception.
   *
   * @param responseException the {@link ResponseException} to handle
   * @throws ResponseException {@code responseException} if {@code responseException} could not be
   *     handled
   * @throws RegistryErrorException if there is an error with a remote registry
   */
  T handleHttpResponseException(ResponseException responseException)
      throws ResponseException, RegistryErrorException;

  /**
   * Returns description of the registry action performed, used in error messages to describe the
   * action that failed.
   */
  String getActionDescription();
}
