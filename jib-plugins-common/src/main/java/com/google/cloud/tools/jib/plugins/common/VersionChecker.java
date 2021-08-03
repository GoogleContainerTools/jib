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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A simple version-range checker, intended to check whether a Jib plugin version falls in some
 * range. A version range can be in one of two forms:
 *
 * <ol>
 *   <li>An <em>interval</em> such as {@code [1.0,3.5)}, where square brackets indicate an closed
 *       boundary (includes the value) and parentheses indicates an open boundary (excludes the
 *       actual value). Either of the left or right bounds can be dropped (but not both!) to to
 *       indicate an half-bound. For example, {@code [,4)} will include any version up to but not
 *       including version 4.0.
 *   <li>a single version such a {@code 1.0} which acts as an interval with an open left bound and
 *       akin to {@code [1.0,)}
 * </ol>
 *
 * <p>To support custom version representations, the actual version object type ({@code <V>}) is
 * pluggable. It must implement {@link Comparable}. The versions in the range must have at most 3
 * components (e.g., {@code major.minor.micro}).
 *
 * <p>This class exists as Gradle has no version-range-type class and Maven's {@code
 * org.apache.maven.artifact.versioning.VersionRange} treats a single version as an exact bound.
 * Note that Gradle's {@code org.gradle.util.GradleVersion} class does not support
 * major-version-only versions.
 */
public class VersionChecker<V extends Comparable<? super V>> {
  /** Regular expression to match a single version. */
  private static final String VERSION_REGEX = "\\d+(\\.\\d+(\\.\\d+)?)?";

  /** Regular expression to match an interval version range. */
  private static final String INTERVAL_REGEX =
      "[\\[(](?<left>" + VERSION_REGEX + ")?,(?<right>" + VERSION_REGEX + ")?[])]";

  private static final Pattern INTERVAL_PATTERN = Pattern.compile(INTERVAL_REGEX);

  // Helper functions to avoid the cognitive burden of {@link Comparable#compareTo()}

  /** Return {@code true} if {@code a} is less than {@code b}. */
  @VisibleForTesting
  static <T extends Comparable<? super T>> boolean lt(T a, T b) {
    return a.compareTo(b) < 0;
  }

  /** Return {@code true} if {@code a} is less than or equal to {@code b}. */
  @VisibleForTesting
  static <T extends Comparable<? super T>> boolean le(T a, T b) {
    return a.compareTo(b) <= 0;
  }

  /** Return {@code true} if {@code a} is greater than {@code b}. */
  @VisibleForTesting
  static <T extends Comparable<? super T>> boolean gt(T a, T b) {
    return a.compareTo(b) > 0;
  }

  /** Return {@code true} if {@code a} is greater than or equal to {@code b}. */
  @VisibleForTesting
  static <T extends Comparable<? super T>> boolean ge(T a, T b) {
    return a.compareTo(b) >= 0;
  }

  /** Responsible for converting a string representation to a comparable version representation. */
  private Function<String, V> converter;

  public VersionChecker(Function<String, V> converter) {
    this.converter = converter;
  }

  /**
   * Return {@code true} if {@code actualVersion} is contained within the version range represented
   * by {@code acceptableVersionRange}.
   *
   * @param acceptableVersionRange the encoded version range
   * @param actualVersion the version to be compared
   * @return true if the version is acceptable
   * @throws IllegalArgumentException if the version could not be parsed
   */
  public boolean compatibleVersion(String acceptableVersionRange, String actualVersion) {
    V pluginVersion = parseVersion(actualVersion);

    // Treat a single version "1.4" as a left bound, equivalent to "[1.4,)"
    if (acceptableVersionRange.matches(VERSION_REGEX)) {
      return ge(pluginVersion, parseVersion(acceptableVersionRange));
    }

    // Otherwise ensure it is a version range with bounds
    Matcher matcher = INTERVAL_PATTERN.matcher(acceptableVersionRange);
    Preconditions.checkArgument(matcher.matches(), "invalid version range");
    String leftBound = matcher.group("left");
    String rightBound = matcher.group("right");
    Preconditions.checkArgument(
        leftBound != null || rightBound != null, "left and right bounds cannot both be empty");
    BiPredicate<V, V> leftComparator =
        acceptableVersionRange.startsWith("[") ? VersionChecker::ge : VersionChecker::gt;
    BiPredicate<V, V> rightComparator =
        acceptableVersionRange.endsWith("]") ? VersionChecker::le : VersionChecker::lt;

    if (leftBound != null && !leftComparator.test(pluginVersion, parseVersion(leftBound))) {
      return false;
    }
    if (rightBound != null && !rightComparator.test(pluginVersion, parseVersion(rightBound))) {
      return false;
    }
    return true;
  }

  /**
   * Parses and returns a version object.
   *
   * @return the parsed version
   * @throws IllegalArgumentException if an exception occurred
   */
  private V parseVersion(String versionString) {
    // catch other exceptions and turn into an IllegalArgumentException
    try {
      return converter.apply(versionString);
    } catch (IllegalArgumentException ex) {
      throw ex; // rethrow
    } catch (Throwable ex) {
      // Gradle's GradleVersion throws all sorts of unchecked exceptions
      throw new IllegalArgumentException("unable to parse '" + versionString + "'", ex);
    }
  }
}
