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

import com.google.cloud.tools.jib.builder.BuildLogger;
import java.util.List;

/** Used for validating configuration parameters. */
public class ParameterValidator {

  /**
   * Checks a list for null or empty elements. Warns the user if an empty string is found, and
   * throws an error if a null element is found.
   *
   * @param list the list to check
   * @param parameterName the name of the configuration parameter being checked
   * @param logger used for displaying messages
   */
  public static void checkListForNullOrEmpty(
      List<String> list, String parameterName, BuildLogger logger) {
    for (String element : list) {
      if (element == null) {
        throw new IllegalStateException(
            "Null element found in list parameter '"
                + parameterName
                + "'. Make sure your configuration is free of null parameters.");
      } else if (element.equals("")) {
        logger.warn("Empty string found in list parameter '" + parameterName + "'.");
      }
    }
  }

  private ParameterValidator() {}
}
