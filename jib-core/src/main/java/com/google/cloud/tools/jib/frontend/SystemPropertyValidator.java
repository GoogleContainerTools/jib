/*
 * Copyright 2018 Google LLC. All rights reserved.
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

package com.google.cloud.tools.jib.frontend;

import java.util.function.Function;

/** Validator for system properties. */
public class SystemPropertyValidator {

  /**
   * Checks the {@code jib.httpTimeout} system property for invalid (non-integer or negative)
   * values.
   *
   * @param exceptionFactory factory to create an exception with the given description
   * @param <T> the exception type to throw if invalid values
   * @throws T if invalid values
   */
  public static <T extends Throwable> void checkHttpTimeoutProperty(
      Function<String, T> exceptionFactory) throws T {
    String value = System.getProperty("jib.httpTimeout");
    try {
      if (value != null && Integer.parseInt(value) < 0) {
        throw exceptionFactory.apply("jib.httpTimeout cannot be negative: " + value);
      }
    } catch (NumberFormatException ex) {
      throw exceptionFactory.apply("jib.httpTimeout must be an integer: " + value);
    }
  }

  private SystemPropertyValidator() {}
}
