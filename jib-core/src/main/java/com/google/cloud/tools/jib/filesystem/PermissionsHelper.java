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
import com.google.common.collect.ImmutableMap;
import java.nio.file.attribute.PosixFilePermission;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/** Helpers for converting between file permission bits and set of {@link PosixFilePermission}. */
public class PermissionsHelper {

  private static final Map<PosixFilePermission, Integer> permissionMap =
      ImmutableMap.<PosixFilePermission, Integer>builder()
          .put(PosixFilePermission.OWNER_READ, 0400)
          .put(PosixFilePermission.OWNER_WRITE, 0200)
          .put(PosixFilePermission.OWNER_EXECUTE, 0100)
          .put(PosixFilePermission.GROUP_READ, 040)
          .put(PosixFilePermission.GROUP_WRITE, 020)
          .put(PosixFilePermission.GROUP_EXECUTE, 010)
          .put(PosixFilePermission.OTHERS_READ, 04)
          .put(PosixFilePermission.OTHERS_WRITE, 02)
          .put(PosixFilePermission.OTHERS_EXECUTE, 01)
          .build();

  /**
   * Converts a set of {@link PosixFilePermission} to the equivalent file permission bits.
   *
   * @param permissions the set of {@link PosixFilePermission}
   * @return the equivalent file permission bits
   */
  public static int toInt(Set<PosixFilePermission> permissions) {
    int octalPermissions = 0;
    for (PosixFilePermission permission : permissions) {
      octalPermissions |= permissionMap.get(permission);
    }
    return octalPermissions;
  }

  /**
   * Converts file permission bits to an equivalent set of {@link PosixFilePermission}.
   *
   * @param permissions the file permission bits
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
