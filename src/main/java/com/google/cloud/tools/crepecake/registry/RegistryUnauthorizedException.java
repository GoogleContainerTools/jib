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

import com.google.api.client.http.HttpResponseException;

/** Thrown when a registry request was unauthorized and therefore authentication is needed. */
public class RegistryUnauthorizedException extends RegistryException {

  RegistryUnauthorizedException(HttpResponseException cause) {
    super(cause);
  }

  HttpResponseException getHttpResponseException() {
    return (HttpResponseException) getCause();
  }
}
