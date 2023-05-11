/*
 * Copyright 2017 Google LLC.
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

package com.google.cloud.tools.jib.api;

import com.google.api.client.http.HttpResponseException;
import com.google.cloud.tools.jib.http.ResponseException;
import javax.annotation.Nullable;

/** Thrown when a registry request was unauthorized and therefore authentication is needed. */
public class RegistryUnauthorizedException extends RegistryException {

  private final String registry;
  private final String repository;

  /**
   * Identifies the image registry and repository that denied access.
   *
   * @param registry the image registry
   * @param repository the image repository
   * @param cause the cause
   */
  public RegistryUnauthorizedException(
      String registry, String repository, ResponseException cause) {
    super("Unauthorized for " + registry + "/" + repository, cause);
    this.registry = registry;
    this.repository = repository;
  }

  public String getImageReference() {
    return registry + "/" + repository;
  }

  @Nullable
  public HttpResponseException getHttpResponseException() {
    if (getCause() != null && getCause().getCause() != null) {
      return (HttpResponseException) getCause().getCause();
    } else {
      return null;
    }
  }
}
