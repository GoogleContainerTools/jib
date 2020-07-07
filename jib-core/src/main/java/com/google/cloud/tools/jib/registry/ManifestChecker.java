/*
 * Copyright 2020 Google LLC.
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
import com.google.cloud.tools.jib.http.ResponseException;
import com.google.cloud.tools.jib.image.json.ManifestTemplate;
import java.util.Optional;

/** Checks an image's manifest. */
class ManifestChecker<T extends ManifestTemplate>
    extends AbstractManifestPuller<T, Optional<ManifestAndDigest<T>>> {

  ManifestChecker(
      RegistryEndpointRequestProperties registryEndpointRequestProperties,
      String imageQualifier,
      Class<T> manifestTemplateClass) {
    super(registryEndpointRequestProperties, imageQualifier, manifestTemplateClass);
  }

  @Override
  public Optional<ManifestAndDigest<T>> handleHttpResponseException(
      ResponseException responseException) throws ResponseException {
    if (responseException.getStatusCode() != HttpStatusCodes.STATUS_CODE_NOT_FOUND) {
      throw responseException;
    }

    if (responseException.getContent() == null) {
      return Optional.empty();
    }

    // Find a MANIFEST_UNKNOWN error response code.
    ErrorCodes errorCode = ErrorResponseUtil.getErrorCode(responseException);
    if (errorCode == ErrorCodes.MANIFEST_UNKNOWN) {
      return Optional.empty();
    }

    // MANIFEST_UNKNOWN was not found as a error response code.
    throw responseException;
  }

  @Override
  Optional<ManifestAndDigest<T>> computeReturn(ManifestAndDigest<T> manifestAndDigest) {
    return Optional.of(manifestAndDigest);
  }
}
