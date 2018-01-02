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
import com.google.cloud.tools.crepecake.registry.json.ErrorEntryTemplate;

/** Builds a {@link RegistryErrorException} with multiple causes. */
class RegistryErrorExceptionBuilder {

  private final HttpResponseException cause;
  private final StringBuilder errorMessageBuilder = new StringBuilder();

  private boolean firstErrorReason = true;

  /**
   * Gets the reason for certain errors.
   *
   * @param errorCodeString string form of {@link ErrorCodes}
   * @param message the original received error message, which may or may not be used depending on
   *     the {@code errorCode}
   */
  private static String getReason(String errorCodeString, String message) {
    try {
      ErrorCodes errorCode = ErrorCodes.valueOf(errorCodeString);

      switch (errorCode) {
        case MANIFEST_UNKNOWN:
          return message;

          // TODO: Include more reasons.

        default:
          return "other: " + message;
      }

    } catch (IllegalArgumentException ex) {
      // Unknown errorCodeString
      return "unknown: " + message;
    }
  }

  /** @param method the registry method that errored */
  RegistryErrorExceptionBuilder(String method, HttpResponseException cause) {
    this.cause = cause;

    errorMessageBuilder.append("Tried to ");
    errorMessageBuilder.append(method);
    errorMessageBuilder.append(" but failed because: ");
  }

  // TODO: Don't use a JsonTemplate as a data object to pass around.
  /**
   * Adds an entry to the error reasons.
   *
   * @param errorEntry the {@link ErrorEntryTemplate} to add
   */
  RegistryErrorExceptionBuilder addErrorEntry(ErrorEntryTemplate errorEntry) {
    String reason = getReason(errorEntry.getCode(), errorEntry.getMessage());
    if (!firstErrorReason) {
      errorMessageBuilder.append(", ");
    }
    errorMessageBuilder.append(reason);
    firstErrorReason = false;
    return this;
  }

  RegistryErrorException build() {
    return new RegistryErrorException(errorMessageBuilder.toString(), cause);
  }
}
