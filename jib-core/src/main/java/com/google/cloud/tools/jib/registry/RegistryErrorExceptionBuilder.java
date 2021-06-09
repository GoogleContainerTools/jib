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

package com.google.cloud.tools.jib.registry;

import com.google.cloud.tools.jib.registry.json.ErrorEntryTemplate;
import javax.annotation.Nullable;

/** Builds a {@link RegistryErrorException} with multiple causes. */
class RegistryErrorExceptionBuilder {

  @Nullable private final Throwable cause;
  private final StringBuilder errorMessageBuilder = new StringBuilder();

  private boolean firstErrorReason = true;

  /**
   * Gets the reason for certain errors.
   *
   * @param errorCodeString string form of {@link ErrorCodes}
   * @param message the original received error message, which may or may not be used depending on
   *     the {@code errorCode}
   */
  private static String getReason(@Nullable String errorCodeString, @Nullable String message) {
    if (message == null) {
      message = "no details";
    }
    if (errorCodeString == null) {
      return "unknown: " + message;
    }

    try {
      switch (ErrorCodes.valueOf(errorCodeString)) {
        case MANIFEST_INVALID:
        case BLOB_UNKNOWN:
          return message + " (something went wrong)";

        case MANIFEST_UNKNOWN:
        case TAG_INVALID:
        case MANIFEST_UNVERIFIED:
          return message;

        default:
          return "other: " + message;
      }
    } catch (IllegalArgumentException ex) {
      return "unknown error code: " + errorCodeString + " (" + message + ")";
    }
  }

  /** Creates a new builder with information about the method that errored. */
  RegistryErrorExceptionBuilder(String method, @Nullable Throwable cause) {
    this.cause = cause;

    errorMessageBuilder.append("Tried to ");
    errorMessageBuilder.append(method);
    errorMessageBuilder.append(" but failed because: ");
  }

  /** Creates a new builder with information about the method that errored. */
  RegistryErrorExceptionBuilder(String method) {
    this(method, null);
  }

  // TODO: Don't use a JsonTemplate as a data object to pass around.
  /**
   * Builds an entry to the error reasons from an {@link ErrorEntryTemplate}.
   *
   * @param errorEntry the {@link ErrorEntryTemplate} to add
   */
  RegistryErrorExceptionBuilder addReason(ErrorEntryTemplate errorEntry) {
    String reason = getReason(errorEntry.getCode(), errorEntry.getMessage());
    addReason(reason);
    return this;
  }

  /** Adds an entry to the error reasons. */
  RegistryErrorExceptionBuilder addReason(String reason) {
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
