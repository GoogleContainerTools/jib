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

package com.google.cloud.tools.jib.registry;

import com.google.api.client.http.HttpResponseException;

/** Thrown when a registry request was unauthorized and therefore authentication is needed. */
public class RegistryUnauthorizedException extends RegistryException {

  private final String imageReference;

  /** @param imageReference identifies the image registry and repository that denied access */
  public RegistryUnauthorizedException(String imageReference, HttpResponseException cause) {
    super(cause);
    this.imageReference = imageReference;
  }

  public String getImageReference() {
    return imageReference;
  }

  public HttpResponseException getHttpResponseException() {
    return (HttpResponseException) getCause();
  }
}
