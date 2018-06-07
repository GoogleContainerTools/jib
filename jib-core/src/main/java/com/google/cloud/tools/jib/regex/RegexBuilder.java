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

package com.google.cloud.tools.jib.regex;

import java.util.regex.Matcher;

/**
 * Static methods for constructing a regular expression in a more readable functional form. Extend
 * this class to use the static methods.
 */
public abstract class RegexBuilder {

  /** {@link Matcher}s will find these. */
  protected static String match(String inside) {
    return "(" + inside + ")";
  }

  /** Nonmatching group. */
  protected static String group(String... insides) {
    return "(?:" + String.join("", insides) + ")";
  }

  /** Matches any of the provided characters. */
  protected static String chars(char... characters) {
    return "[" + new String(characters) + "]";
  }

  /** Matches word characters {@code [a-zA-Z_0-9]} and any of the provided characters. */
  protected static String wordChars(char... characters) {
    return "[\\w" + new String(characters) + "]";
  }

  /** Matches alphanumeric characters {@code [a-zA-Z0-9]} and any of the provided characters. */
  protected static String alphanum(char... characters) {
    return "[a-zA-Z\\d" + new String(characters) + "]";
  }

  /** Matches lowercase alphanumeric characters. */
  protected static String lowerAlphanum() {
    return "[a-z\\d]";
  }

  /** A digit {@code 0-9} character. */
  protected static String digit() {
    return "\\d";
  }

  /** Zero or more. */
  protected static String any(String inside) {
    return inside + "*";
  }

  /** Zero or one. */
  protected static String optional(String inside) {
    return inside + "?";
  }

  /** One or more. */
  protected static String repeated(String inside) {
    return inside + "+";
  }

  /** At least {@code min} and not more than {@code max}. */
  protected static String range(String component, int min, int max) {
    return component + "{" + min + "," + max + "}";
  }

  /** In-order sequence. */
  protected static String sequence(String... parts) {
    return String.join("", parts);
  }

  /** Matches any of {@code parts}. */
  protected static String or(String... parts) {
    return String.join("|", parts);
  }

  /** Matches beginning of line. */
  protected static String begin() {
    return "^";
  }

  /** Matches end of line. */
  protected static String end() {
    return "$";
  }

  /** Matches a character that needs escaping. */
  // TODO: Add more to escape.
  protected static String literal(char literal) {
    switch (literal) {
      case '.':
        return "\\.";
      default:
        return Character.toString(literal);
    }
  }
}
