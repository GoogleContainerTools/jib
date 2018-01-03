/*
 * Copyright 2017 Google Inc.
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

import com.google.cloud.tools.crepecake.http.Response;
import java.io.IOException;

/** Provides implementations for a registry endpoint. */
interface RegistryEndpointProvider {

  /** Handles the response specific to the registry action. */
  Object handleResponse(Response response) throws IOException, RegistryException;

  /**
   * @return the suffix for the registry endpoint after the namespace (for example, {@code
   *     "/manifests/latest"})
   */
  String getApiRouteSuffix();

  /**
   * @return a description of the registry action performed, used in error messages to describe the
   *     action that failed
   */
  String getActionDescription(String serverUrl, String imageName);
}
