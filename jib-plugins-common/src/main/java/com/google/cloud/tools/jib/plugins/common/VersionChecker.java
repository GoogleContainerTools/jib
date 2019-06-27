/*
 * Copyright 2019 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.tools.jib.plugins.common;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import java.util.function.BiPredicate;
import java.util.function.Function;

/**
 * A simple version-range checker, intended to check whether a Jib plugin version falls in some
 * range. A version range can be in one of two forms:
 *
 * <ol>
 *   <li>A <em>bounded range</em> such as {@code [1.0,3.5)}, where square brackets indicate an open
 *       boundary (includes the value) and parentheses indicates a closed boundary (excludes the
 *       actual value). Either of the lower or upper bounds can be dropped (but not both!), such as
 *       to indicate an exclusive upper- or lower-bound. For example, {@code [,4)} indicates up to
 *       but not including version 4.0.
 *   <li>a single version such a {@code 1.0} which acts as an open lower bound and akin to {@code
 *       [1.0,)}
 * </ol>
 *
 * To support custom version representations, the actual version object type ({@code <V>}) is
 * pluggable. It must implement {@link Comparable}.
 *
 * <p>This class exists as Gradle has no version-range-type class and Maven's {@code
 * org.apache.maven.artifact.versioning.VersionRange} treats a single version as an exact bound.
 * Note that Gradle's {@code org.gradle.util.GradleVersion} class does not support
 * major-version-only versions.
 */
public class VersionChecker<V extends Comparable<? super V>> {
  /** The expected version representation. */
  private static final String VERSION_PATTERN = "\\d+(\\.\\d+(\\.\\d+)?)?";

  // Helper functions to avoid the cognitive burden of {@link Comparable#compareTo()}

  /** Return {@code true} if {@code a} is less than {@code b}. */
  @VisibleForTesting
  static <T extends Comparable<? super T>> boolean LT(T a, T b) {
    return a.compareTo(b) < 0;
  }

  /** Return {@code true} if {@code a} is less than or equal to {@code b}. */
  @VisibleForTesting
  static <T extends Comparable<? super T>> boolean LE(T a, T b) {
    return a.compareTo(b) <= 0;
  }

  /** Return {@code true} if {@code a} is greater than {@code b}. */
  @VisibleForTesting
  static <T extends Comparable<? super T>> boolean GT(T a, T b) {
    return a.compareTo(b) > 0;
  }

  /** Return {@code true} if {@code a} is greater than or equal to {@code b}. */
  @VisibleForTesting
  static <T extends Comparable<? super T>> boolean GE(T a, T b) {
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
  @VisibleForTesting
  public boolean compatibleVersion(String acceptableVersionRange, String actualVersion) {
    V pluginVersion = parseVersion(actualVersion);

    // Treat a single version "1.4" as a lower bound (equivalent to "[1.4,)")
    if (acceptableVersionRange.matches(VERSION_PATTERN)) {
      return GE(pluginVersion, parseVersion(acceptableVersionRange));
    }

    // Otherwise ensure it is a version range with bounds
    Preconditions.checkArgument(
        acceptableVersionRange.matches(
            "[\\[(](" + VERSION_PATTERN + ")?,(" + VERSION_PATTERN + ")?[\\])]"),
        "invalid version range");
    BiPredicate<V, V> bottomComparator =
        acceptableVersionRange.startsWith("[") ? VersionChecker::GE : VersionChecker::GT;
    BiPredicate<V, V> topComparator =
        acceptableVersionRange.endsWith("]") ? VersionChecker::LE : VersionChecker::LT;
    // extract the two version specs
    String[] range =
        acceptableVersionRange.substring(1, acceptableVersionRange.length() - 1).split(",", -1);
    Preconditions.checkArgument(range.length == 2, "version range must have upper and lower bound");
    Preconditions.checkArgument(
        range[0].length() != 0 || range[1].length() != 0,
        "upper and lower bounds cannot both be empty");

    if (!range[0].isEmpty() && !bottomComparator.test(pluginVersion, parseVersion(range[0]))) {
      return false;
    }
    if (!range[1].isEmpty() && !topComparator.test(pluginVersion, parseVersion(range[1]))) {
      return false;
    }
    return true;
  }

  /**
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
