/*
 * Copyright 2019 Google LLC.
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

package com.google.cloud.tools.jib.plugins.common;

import java.time.format.DateTimeParseException;

/** Exception when an invalid container creation timestamp configuration is encountered. */
public class InvalidCreationTimeException extends Exception {

  private final String invalidValue;

  public InvalidCreationTimeException(
      String message, String invalidValue, DateTimeParseException ex) {
    super(message, ex);
    this.invalidValue = invalidValue;
  }

  public String getInvalidCreationTime() {
    return invalidValue;
  }
}
