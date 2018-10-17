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

import com.google.common.collect.ImmutableSet;
import java.nio.file.attribute.PosixFilePermission;
import org.junit.Assert;
import org.junit.Test;

/** Tests for {@link PermissionsHelper}. */
public class PermissionsHelperTest {

  @Test
  public void testToInt() {
    Assert.assertEquals(0, PermissionsHelper.toInt(ImmutableSet.of()));
    Assert.assertEquals(
        0110,
        PermissionsHelper.toInt(
            ImmutableSet.of(PosixFilePermission.OWNER_EXECUTE, PosixFilePermission.GROUP_EXECUTE)));
    Assert.assertEquals(
        0202,
        PermissionsHelper.toInt(
            ImmutableSet.of(PosixFilePermission.OWNER_WRITE, PosixFilePermission.OTHERS_WRITE)));
    Assert.assertEquals(
        0044,
        PermissionsHelper.toInt(
            ImmutableSet.of(PosixFilePermission.GROUP_READ, PosixFilePermission.OTHERS_READ)));
    Assert.assertEquals(
        0644,
        PermissionsHelper.toInt(
            ImmutableSet.of(
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.GROUP_READ,
                PosixFilePermission.OTHERS_READ)));
    Assert.assertEquals(
        0755,
        PermissionsHelper.toInt(
            ImmutableSet.of(
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_EXECUTE,
                PosixFilePermission.GROUP_READ,
                PosixFilePermission.GROUP_EXECUTE,
                PosixFilePermission.OTHERS_READ,
                PosixFilePermission.OTHERS_EXECUTE)));
    Assert.assertEquals(
        0777, PermissionsHelper.toInt(ImmutableSet.copyOf(PosixFilePermission.values())));
  }

  @Test
  public void testToSet() {
    Assert.assertEquals(ImmutableSet.of(), PermissionsHelper.toSet(0));
    Assert.assertEquals(
        ImmutableSet.of(PosixFilePermission.OWNER_EXECUTE, PosixFilePermission.GROUP_EXECUTE),
        PermissionsHelper.toSet(0110));
    Assert.assertEquals(
        ImmutableSet.of(PosixFilePermission.OWNER_WRITE, PosixFilePermission.OTHERS_WRITE),
        PermissionsHelper.toSet(0202));
    Assert.assertEquals(
        ImmutableSet.of(PosixFilePermission.GROUP_READ, PosixFilePermission.OTHERS_READ),
        PermissionsHelper.toSet(0044));
    Assert.assertEquals(
        ImmutableSet.of(
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.GROUP_READ,
            PosixFilePermission.OTHERS_READ),
        PermissionsHelper.toSet(0644));
    Assert.assertEquals(
        ImmutableSet.of(
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_EXECUTE,
            PosixFilePermission.GROUP_READ,
            PosixFilePermission.GROUP_EXECUTE,
            PosixFilePermission.OTHERS_READ,
            PosixFilePermission.OTHERS_EXECUTE),
        PermissionsHelper.toSet(0755));
    Assert.assertEquals(
        ImmutableSet.copyOf(PosixFilePermission.values()), PermissionsHelper.toSet(0777));
  }
}
