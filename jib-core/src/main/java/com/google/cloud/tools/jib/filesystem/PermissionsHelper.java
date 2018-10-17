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

package com.google.cloud.tools.jib.filesystem;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

/** Helpers for converting file permissions. */
public class PermissionsHelper {

  /**
   * Converts a set of {@link PosixFilePermission} to its 3-digit octal integer representation.
   *
   * @param permissions the set of {@link PosixFilePermission}
   * @return the 3-digit octal integer representation of the set
   */
  public static int toInt(Set<PosixFilePermission> permissions) {
    int result = 0;
    PosixFilePermission[] values = PosixFilePermission.values();
    for (int bit = 0; bit < values.length; bit++) {
      if (permissions.contains(values[bit])) {
        result |= 1 << (8 - bit);
      }
    }
    return result;
  }

  /**
   * Converts a 3-digit octal integer to an equivalent set of {@link PosixFilePermission}.
   *
   * @param permissions the 3-digit octal integer
   * @return the equivalent {@link PosixFilePermission} set
   */
  public static Set<PosixFilePermission> toSet(int permissions) {
    Preconditions.checkArgument(
        permissions >= 0 && permissions <= 511, "Permissions must be between 000 and 777 octal");
    ImmutableSet.Builder<PosixFilePermission> result = ImmutableSet.builder();
    for (int bit = 0; bit < 9; bit++) {
      if ((permissions & (1 << bit)) != 0) {
        result.add(PosixFilePermission.values()[8 - bit]);
      }
    }
    return result.build();
  }
}
