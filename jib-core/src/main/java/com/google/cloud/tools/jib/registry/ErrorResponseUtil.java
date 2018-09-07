/*
 * Copyright 2018 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.tools.jib.registry;

import com.google.api.client.http.HttpResponseException;
import com.google.cloud.tools.jib.json.JsonTemplateMapper;
import com.google.cloud.tools.jib.registry.json.ErrorEntryTemplate;
import com.google.cloud.tools.jib.registry.json.ErrorResponseTemplate;
import java.io.IOException;
import java.util.List;

/** Utility methods for parsing {@link ErrorResponseTemplate JSON-encoded error responses}. */
public class ErrorResponseUtil {

  /**
   * Extract an {@link ErrorCodes} response from the error object encoded in an {@link
   * HttpResponseException}.
   *
   * @param httpResponseException the response exception
   * @return the parsed {@link ErrorCodes} if found
   * @throws HttpResponseException rethrows the original exception if an error object could not be
   *     parsed, if there were multiple error objects, or if the error code is unknown.
   */
  public static ErrorCodes getErrorCode(HttpResponseException httpResponseException)
      throws HttpResponseException {
    // Obtain the error response code.
    String errorContent = httpResponseException.getContent();
    if (errorContent == null) {
      throw httpResponseException;
    }

    try {
      ErrorResponseTemplate errorResponse =
          JsonTemplateMapper.readJson(errorContent, ErrorResponseTemplate.class);
      List<ErrorEntryTemplate> errors = errorResponse.getErrors();
      // There may be multiple error objects
      if (errors.size() == 1) {
        String errorCodeString = errors.get(0).getCode();
        // May not get an error code back.
        if (errorCodeString != null) {
          // throws IllegalArgumentException if unknown error code
          return ErrorCodes.valueOf(errorCodeString);
        }
      }

    } catch (IOException | IllegalArgumentException ex) {
      // Parse exception: either isn't an error object or unknown error code
    }

    // rethrow the original exception
    throw httpResponseException;
  }

  // not intended to be instantiated
  private ErrorResponseUtil() {}
}
