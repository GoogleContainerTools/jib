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

import java.util.Locale;

/**
 * Containerizing mode.
 *
 * <ul>
 *   <li>{@code EXPLODED} puts individual application files without packaging.
 *   <li>{@code PACKAGED} puts a single packaged artifact for an application.
 * </ul>
 */
public enum ContainerizingMode {
  EXPLODED,
  PACKAGED;

  /**
   * Converts a string representation of ContainerizingMode to Enum. It requires an all lowercase
   * string that matches the enum value exactly.
   *
   * @param rawMode the raw string to parse
   * @return the enum equivalent of the mode
   * @throws InvalidContainerizingModeException when not lowercase, or cannot match to an values of
   *     this enum class
   */
  public static ContainerizingMode from(String rawMode) throws InvalidContainerizingModeException {
    try {
      if (!rawMode.toLowerCase(Locale.US).equals(rawMode)) {
        throw new InvalidContainerizingModeException(rawMode, rawMode);
      }
      return ContainerizingMode.valueOf(rawMode.toUpperCase(Locale.US));
    } catch (IllegalArgumentException ex) {
      throw new InvalidContainerizingModeException(rawMode, rawMode);
    }
  }
}
