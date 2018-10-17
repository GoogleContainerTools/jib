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
import java.nio.file.attribute.PosixFilePermission;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/** Helpers for converting file permissions. */
public class PermissionsHelper {

  private static final Map<PosixFilePermission, Integer> permissionMap;

  static {
    Map<PosixFilePermission, Integer> result = new HashMap<>();
    result.put(PosixFilePermission.OWNER_READ, 0b100000000);
    result.put(PosixFilePermission.OWNER_WRITE, 0b010000000);
    result.put(PosixFilePermission.OWNER_EXECUTE, 0b001000000);
    result.put(PosixFilePermission.GROUP_READ, 0b000100000);
    result.put(PosixFilePermission.GROUP_WRITE, 0b000010000);
    result.put(PosixFilePermission.GROUP_EXECUTE, 0b000001000);
    result.put(PosixFilePermission.OTHERS_READ, 0b000000100);
    result.put(PosixFilePermission.OTHERS_WRITE, 0b000000010);
    result.put(PosixFilePermission.OTHERS_EXECUTE, 0b000000001);
    permissionMap = Collections.unmodifiableMap(result);
  }

  /**
   * Converts a set of {@link PosixFilePermission} to its 3-digit octal integer representation.
   *
   * @param permissions the set of {@link PosixFilePermission}
   * @return the 3-digit octal integer representation of the set
   */
  public static int toInt(Set<PosixFilePermission> permissions) {
    int result = 0;
    for (PosixFilePermission permission : permissions) {
      result |= permissionMap.get(permission);
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
        permissions >= 0 && permissions <= 0777, "Permissions must be between 000 and 777 octal");
    HashSet<PosixFilePermission> result = new HashSet<>();
    for (Entry<PosixFilePermission, Integer> entry : permissionMap.entrySet()) {
      if ((permissions & entry.getValue()) != 0) {
        result.add(entry.getKey());
      }
    }
    return result;
  }

  private PermissionsHelper() {}
}
