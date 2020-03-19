/*
 * Copyright 2018 Google LLC.
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

package com.google.cloud.tools.jib.buildplan;

import java.util.ArrayList;
import java.util.List;

/** Parses Unix-style paths. */
public class UnixPathParser {

  /**
   * Parses a Unix-style path into a list of path components.
   *
   * @param unixPath the Unix-style path
   * @return a list of path components
   */
  public static List<String> parse(String unixPath) {
    List<String> pathComponents = new ArrayList<>();
    // -1 limit for Guava Splitter behavior: https://errorprone.info/bugpattern/StringSplitter
    for (String component : unixPath.split("/", -1)) {
      if (!component.isEmpty()) {
        pathComponents.add(component);
      }
    }
    return pathComponents;
  }

  private UnixPathParser() {}
}
