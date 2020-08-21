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

package com.google.cloud.tools.jib.api.buildplan;

import java.nio.file.attribute.PosixFilePermission;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.annotation.concurrent.Immutable;

/**
 * Represents read/write/execute file permissions for owner, group, and others.
 *
 * <p>This class is immutable and thread-safe.
 */
@Immutable
public class FilePermissions {

  /** Default permissions for files added to the container. */
  public static final FilePermissions DEFAULT_FILE_PERMISSIONS = new FilePermissions(0644);

  /** Default permissions for folders added to the container. */
  public static final FilePermissions DEFAULT_FOLDER_PERMISSIONS = new FilePermissions(0755);

  /**
   * Matches an octal string representation of file permissions. From left to right, each digit
   * represents permissions for owner, group, and other.
   */
  private static final String OCTAL_PATTERN = "[0-7][0-7][0-7]";

  /** Maps from a {@link PosixFilePermission} to its corresponding file permission bit. */
  private static final Map<PosixFilePermission, Integer> PERMISSION_MAP;

  static {
    Map<PosixFilePermission, Integer> map = new HashMap<>(9);
    map.put(PosixFilePermission.OWNER_READ, 0400);
    map.put(PosixFilePermission.OWNER_WRITE, 0200);
    map.put(PosixFilePermission.OWNER_EXECUTE, 0100);
    map.put(PosixFilePermission.GROUP_READ, 040);
    map.put(PosixFilePermission.GROUP_WRITE, 020);
    map.put(PosixFilePermission.GROUP_EXECUTE, 010);
    map.put(PosixFilePermission.OTHERS_READ, 04);
    map.put(PosixFilePermission.OTHERS_WRITE, 02);
    map.put(PosixFilePermission.OTHERS_EXECUTE, 01);
    PERMISSION_MAP = Collections.unmodifiableMap(map);
  }

  /**
   * Creates a new {@link FilePermissions} from an octal string representation (e.g. "123", "644",
   * "755", etc).
   *
   * @param octalPermissions the octal string representation of the permissions
   * @return a new {@link FilePermissions} with the given permissions
   */
  public static FilePermissions fromOctalString(String octalPermissions) {
    if (!octalPermissions.matches(OCTAL_PATTERN)) {
      throw new IllegalArgumentException(
          "octalPermissions must be a 3-digit octal number (000-777)");
    }
    return new FilePermissions(Integer.parseInt(octalPermissions, 8));
  }

  /**
   * Creates a new {@link FilePermissions} from a set of {@link PosixFilePermission}.
   *
   * @param posixFilePermissions the set of {@link PosixFilePermission}
   * @return a new {@link FilePermissions} with the given permissions
   */
  public static FilePermissions fromPosixFilePermissions(
      Set<PosixFilePermission> posixFilePermissions) {
    int permissionBits = 0;
    for (PosixFilePermission permission : posixFilePermissions) {
      permissionBits |= Objects.requireNonNull(PERMISSION_MAP.get(permission));
    }
    return new FilePermissions(permissionBits);
  }

  private final int permissionBits;

  // VisibleForTesting
  FilePermissions(int permissionBits) {
    this.permissionBits = permissionBits;
  }

  /**
   * Gets the corresponding permissions bits specified by the {@link FilePermissions}.
   *
   * @return the permission bits
   */
  public int getPermissionBits() {
    return permissionBits;
  }

  /**
   * Gets the octal string representation of the permissions.
   *
   * @return the octal string representation of the permissions
   */
  public String toOctalString() {
    return Integer.toString(permissionBits, 8);
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof FilePermissions)) {
      return false;
    }
    FilePermissions otherFilePermissions = (FilePermissions) other;
    return permissionBits == otherFilePermissions.permissionBits;
  }

  @Override
  public int hashCode() {
    return permissionBits;
  }

  @Override
  public String toString() {
    return toOctalString();
  }
}
