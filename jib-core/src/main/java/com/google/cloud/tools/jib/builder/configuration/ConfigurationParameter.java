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

/** A parameter in the {@link BuildConfiguration}. */
interface ConfigurationParameter<T> {

  class ValidationResult {

    static ValidationResult valid() {
      return new ValidationResult(true, null);
    }

    static ValidationResult invalid(String errorMessage) {
      return new ValidationResult(false, errorMessage);
    }

    private final boolean isValid;
    private String errorMessage;

    private ValidationResult(boolean isValid, String errorMessage) {
      this.isValid = isValid;
      this.errorMessage = errorMessage;
    }

    boolean isValid() {
      return isValid;
    }

    String getErrorMessage() {
      return errorMessage;
    }
  }

  /** Sets the value for the parameter. */
  ConfigurationParameter<T> set(T value);

  /** Gets the value of this parameter. */
  T get();

  /** Validates the value of this parameter. */
  ValidationResult validate();
}
