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

package com.google.cloud.tools.minikube.util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Utility class to parse a minikube's Docker environment variables list. */
public class MinikubeDockerEnvParser {

  private MinikubeDockerEnvParser() {}

  /**
   * Parses a list of KEY=VALUE strings into a map from KEY to VALUE.
   *
   * @param keyValueStrings a list of "KEY=VALUE" strings, where KEY is the environment variable
   *     name and VALUE is the value to set it to
   */
  public static Map<String, String> parse(List<String> keyValueStrings) {
    Map<String, String> environmentMap = new HashMap<>();

    for (String keyValueString : keyValueStrings) {
      String[] keyValuePair = keyValueString.split("=", 2);

      if (keyValuePair.length < 2) {
        throw new IllegalArgumentException(
            "Environment variable string must be in KEY=VALUE format");
      }
      if (keyValuePair[0].length() == 0) {
        throw new IllegalArgumentException("Environment variable name cannot be empty");
      }

      environmentMap.put(keyValuePair[0], keyValuePair[1]);
    }

    return environmentMap;
  }
}
