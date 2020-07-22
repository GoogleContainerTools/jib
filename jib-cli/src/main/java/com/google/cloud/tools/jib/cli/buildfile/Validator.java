/*
 * Copyright 2020 Google LLC.
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

package com.google.cloud.tools.jib.cli.buildfile;

import com.google.common.base.Preconditions;
import java.util.Collection;
import javax.annotation.Nullable;

/**
 * Utility helper class to detect errors in parsed yaml values. This class is mostly concerned with
 * error message formatting, checking is delegated to guava.
 */
public class Validator {

  /**
   * Checks if string is null, empty or only whitespace.
   *
   * @param value the string in question
   * @param propertyName the equivalent 'yaml' property name
   * @throws NullPointerException if {@code value} is null
   * @throws IllegalArgumentException if {@code value} is empty or only whitespace
   */
  public static void checkNotEmpty(@Nullable String value, String propertyName) {
    Preconditions.checkNotNull(value, "Property '" + propertyName + "' cannot be null");
    Preconditions.checkArgument(
        !value.trim().isEmpty(), "Property '" + propertyName + "' cannot be empty");
  }

  /**
   * Checks if a collection is null or empty.
   *
   * @param value the string in question
   * @param propertyName the equivalent 'yaml' property name
   * @throws NullPointerException if {@code value} is null
   * @throws IllegalArgumentException if {@code value} is empty
   */
  public static void checkNotEmpty(@Nullable Collection<?> value, String propertyName) {
    Preconditions.checkNotNull(value, "Property '" + propertyName + "' cannot be null");
    Preconditions.checkArgument(
        !value.isEmpty(), "Property '" + propertyName + "' cannot be an empty collection");
  }

  /**
   * Checks if string is what is expected.
   *
   * @param value the string in question
   * @param propertyName the equivalent 'yaml' property name
   * @param expectedValue the value we expect {@code value} to be
   * @throws NullPointerException if {@code value} is null
   * @throws IllegalArgumentException if {@code value} is not equal to {@code expectedValue}
   */
  public static void checkEquals(
      @Nullable String value, String propertyName, String expectedValue) {
    Preconditions.checkNotNull(value, "Property '" + propertyName + "' cannot be null");
    Preconditions.checkArgument(
        value.equals(expectedValue),
        "Property '" + propertyName + "' must be '" + expectedValue + "' but is '" + value + "'");
  }
}
