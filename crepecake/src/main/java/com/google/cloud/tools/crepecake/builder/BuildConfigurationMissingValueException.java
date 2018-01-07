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

package com.google.cloud.tools.crepecake.builder;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

/** Exception indicating the {@link BuildConfiguration} is missing a required value. */
public class BuildConfigurationMissingValueException extends Exception {

  static class Builder {

    private final List<String> descriptions = new ArrayList<>();

    Builder addDescription(String description) {
      descriptions.add(description);
      return this;
    }

    /**
     * @return the built {@link BuildConfigurationMissingValueException}, or {@code null} if no
     *     descriptions were added
     */
    @Nullable
    BuildConfigurationMissingValueException build() {
      switch (descriptions.size()) {
        case 0:
          return null;

        case 1:
          return new BuildConfigurationMissingValueException(descriptions.get(0));

        case 2:
          return new BuildConfigurationMissingValueException(
              descriptions.get(0) + " and " + descriptions.get(1));

        default:
          // Appends the descriptions in correct grammar.
          StringBuilder stringBuilder = new StringBuilder();
          for (int descriptionsIndex = 0;
              descriptionsIndex < descriptions.size();
              descriptionsIndex++) {
            if (descriptionsIndex == descriptions.size() - 1) {
              stringBuilder.append(", and ");
            } else {
              stringBuilder.append(", ");
            }
            stringBuilder.append(descriptions.get(descriptionsIndex));
          }
          return new BuildConfigurationMissingValueException(stringBuilder.toString());
      }
    }
  }

  static Builder builder() {
    return new Builder();
  }

  BuildConfigurationMissingValueException(String requiredDescription) {
    super(requiredDescription + " required but not set in build configuration");
  }
}
