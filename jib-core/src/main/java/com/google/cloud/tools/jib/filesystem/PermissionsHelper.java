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
import com.google.common.collect.ImmutableSet;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Map.Entry;
import java.util.Set;

/** Helpers for converting between file permission bits and set of {@link PosixFilePermission}. */
public class PermissionsHelper {

  /** Maps from a {@link PosixFilePermission} to its corresponding file permission bit. */
  private static final ImmutableMap<PosixFilePermission, Integer> permissionMap =
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
  public static int toPermissionBits(Set<PosixFilePermission> permissions) {
    int permissionBits = 0;
    for (PosixFilePermission permission : permissions) {
      permissionBits |= permissionMap.get(permission);
    }
    return permissionBits;
  }

  /**
   * Converts file permission bits to an equivalent set of {@link PosixFilePermission}.
   *
   * @param permissions the file permission bits
   * @return the equivalent {@link PosixFilePermission} set
   */
  public static ImmutableSet<PosixFilePermission> toPermissionSet(int permissions) {
    Preconditions.checkArgument(
        permissions >= 0 && permissions <= 0777, "Permissions must be between 000 and 777 octal");
    EnumSet<PosixFilePermission> permissionsSetBuilder = EnumSet.noneOf(PosixFilePermission.class);
    for (Entry<PosixFilePermission, Integer> entry : permissionMap.entrySet()) {
      if ((permissions & entry.getValue()) != 0) {
        permissionsSetBuilder.add(entry.getKey());
      }
    }
    return ImmutableSet.copyOf(permissionsSetBuilder);
  }

  private PermissionsHelper() {}
}
