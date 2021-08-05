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
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Utility helper class to detect errors in parsed yaml values. This class is mostly concerned with
 * error message formatting, checking is delegated to guava.
 */
public class Validator {

  private Validator() {}

  /**
   * Checks if string is non null and non empty.
   *
   * @param value the string in question
   * @param propertyName the equivalent 'yaml' property name
   * @throws NullPointerException if {@code value} is null
   * @throws IllegalArgumentException if {@code value} is empty or only whitespace
   */
  public static void checkNotNullAndNotEmpty(@Nullable String value, String propertyName) {
    Preconditions.checkNotNull(value, "Property '" + propertyName + "' cannot be null");
    Preconditions.checkArgument(
        !value.trim().isEmpty(), "Property '" + propertyName + "' cannot be an empty string");
  }

  /**
   * Checks if string is null or non empty.
   *
   * @param value the string in question
   * @param propertyName the equivalent 'yaml' property name
   * @throws IllegalArgumentException if {@code value} is empty or only whitespace
   */
  public static void checkNullOrNotEmpty(@Nullable String value, String propertyName) {
    if (value == null) {
      // pass
      return;
    }
    Preconditions.checkArgument(
        !value.trim().isEmpty(), "Property '" + propertyName + "' cannot be an empty string");
  }

  /**
   * Checks if a collection is not null and not empty.
   *
   * @param value the string in question
   * @param propertyName the equivalent 'yaml' property name
   * @throws NullPointerException if {@code value} is null
   * @throws IllegalArgumentException if {@code value} is empty
   */
  public static void checkNotNullAndNotEmpty(@Nullable Collection<?> value, String propertyName) {
    Preconditions.checkNotNull(value, "Property '" + propertyName + "' cannot be null");
    Preconditions.checkArgument(
        !value.isEmpty(), "Property '" + propertyName + "' cannot be an empty collection");
  }

  /**
   * Check if a collection is either null, empty or contains only non-null, non-empty values.
   *
   * @param values the collection in question
   * @param propertyName the equivalent 'yaml' property name
   * @throws IllegalArgumentException if {@code values} contains empty entries
   * @throws NullPointerException if {@code values} contains null entries
   */
  public static void checkNullOrNonNullNonEmptyEntries(
      @Nullable Collection<String> values, String propertyName) {
    if (values == null) {
      // pass
      return;
    }
    for (String value : values) {
      Preconditions.checkNotNull(
          value, "Property '" + propertyName + "' cannot contain null entries");
      Preconditions.checkArgument(
          !value.trim().isEmpty(), "Property '" + propertyName + "' cannot contain empty strings");
    }
  }

  /**
   * Check if a map is either null, empty or contains only non-null, non-empty keys and values.
   *
   * @param values the collection in question
   * @param propertyName the equivalent 'yaml' property name
   * @throws IllegalArgumentException if {@code values} contains empty keys or values
   * @throws NullPointerException if {@code values} contains null keys or values
   */
  public static void checkNullOrNonNullNonEmptyEntries(
      @Nullable Map<String, String> values, String propertyName) {
    if (values == null) {
      // pass
      return;
    }
    for (Map.Entry<String, String> entry : values.entrySet()) {
      Preconditions.checkNotNull(
          entry.getKey(), "Property '" + propertyName + "' cannot contain null keys");
      Preconditions.checkArgument(
          !entry.getKey().trim().isEmpty(),
          "Property '" + propertyName + "' cannot contain empty string keys");
      Preconditions.checkNotNull(
          entry.getValue(), "Property '" + propertyName + "' cannot contain null values");
      Preconditions.checkArgument(
          !entry.getValue().trim().isEmpty(),
          "Property '" + propertyName + "' cannot contain empty string values");
    }
  }

  /**
   * Check if a collection is either null, empty or contains only non-null values.
   *
   * @param values the collection in question
   * @param propertyName the equivalent 'yaml' property name
   * @throws NullPointerException if {@code values} contains null entries
   */
  public static void checkNullOrNonNullEntries(
      @Nullable Collection<?> values, String propertyName) {
    if (values == null) {
      // pass
      return;
    }
    for (Object value : values) {
      Preconditions.checkNotNull(
          value, "Property '" + propertyName + "' cannot contain null entries");
    }
  }

  /**
   * Checks if string is equal to the expected string.
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
