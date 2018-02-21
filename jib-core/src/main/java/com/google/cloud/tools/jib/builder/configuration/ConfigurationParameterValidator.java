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

package com.google.cloud.tools.jib.builder.configuration;

import java.util.ArrayList;
import java.util.List;

/** Validates multiple {@link ConfigurationParameter}s and stores the validation results. */
class ConfigurationParameterValidator {

  private final List<String> errorMessages = new ArrayList<>();

  /** Validates a {@link ConfigurationParameter} and records its validation result. */
  void validate(ConfigurationParameter<?> configurationParameter) {
    ConfigurationParameter.ValidationResult validationResult = configurationParameter.validate();
    if (!validationResult.isValid()) {
      errorMessages.add(validationResult.getErrorMessage());
    }
  }

  boolean hasError() {
    return errorMessages.size() > 0;
  }

  /** @return a grammatical version of the list of {@link #errorMessages}. */
  String getErrorMessage() {
    switch (errorMessages.size()) {
      case 0:
        return "";

      case 1:
        return errorMessages.get(0);

      case 2:
        return errorMessages.get(0) + " and " + errorMessages.get(1);

      default:
        // Appends the descriptions in correct grammar.
        StringBuilder errorMessage = new StringBuilder(errorMessages.get(0));
        for (int errorMessageIndex = 1;
            errorMessageIndex < errorMessages.size();
            errorMessageIndex++) {
          if (errorMessageIndex == errorMessages.size() - 1) {
            errorMessage.append(", and ");
          } else {
            errorMessage.append(", ");
          }
          errorMessage.append(errorMessages.get(errorMessageIndex));
        }
        return errorMessage.toString();
    }
  }
}
