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

package com.google.cloud.tools.jib.maven;

import com.google.cloud.tools.jib.frontend.HelpfulSuggestions;
import java.util.function.Function;

/** Provider for Maven-specific {@link HelpfulSuggestions}. */
class HelpfulSuggestionsProvider {

  private static final Function<String, String> AUTH_CONFIGURATION_SUGGESTION =
      registry -> "set credentials for '" + registry + "' in your Maven settings";

  /**
   * @param messagePrefix the prefix
   * @return a new {@link HelpfulSuggestions} with the specified message prefix
   */
  static HelpfulSuggestions get(String messagePrefix) {
    return new HelpfulSuggestions(
        messagePrefix,
        "mvn clean",
        "<from><credHelper>",
        AUTH_CONFIGURATION_SUGGESTION,
        "<to><credHelper>",
        AUTH_CONFIGURATION_SUGGESTION);
  }

  private HelpfulSuggestionsProvider() {}
}
