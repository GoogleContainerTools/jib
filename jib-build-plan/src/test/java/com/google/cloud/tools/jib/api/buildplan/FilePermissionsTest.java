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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.nio.file.attribute.PosixFilePermission;
import org.junit.Assert;
import org.junit.jupiter.api.Test;

/** Tests for {@link FilePermissions}. */
class FilePermissionsTest {

  @Test
  void testFromOctalString() {
    Assert.assertEquals(new FilePermissions(0777), FilePermissions.fromOctalString("777"));
    Assert.assertEquals(new FilePermissions(0000), FilePermissions.fromOctalString("000"));
    Assert.assertEquals(new FilePermissions(0123), FilePermissions.fromOctalString("123"));
    Assert.assertEquals(new FilePermissions(0755), FilePermissions.fromOctalString("755"));
    Assert.assertEquals(new FilePermissions(0644), FilePermissions.fromOctalString("644"));

    ImmutableList<String> badStrings = ImmutableList.of("abc", "-123", "777444333", "987", "3");
    for (String badString : badStrings) {
      try {
        FilePermissions.fromOctalString(badString);
        Assert.fail();
      } catch (IllegalArgumentException ex) {
        Assert.assertEquals(
            "octalPermissions must be a 3-digit octal number (000-777)", ex.getMessage());
      }
    }
  }

  @Test
  void testFromPosixFilePermissions() {
    Assert.assertEquals(
        new FilePermissions(0000), FilePermissions.fromPosixFilePermissions(ImmutableSet.of()));
    Assert.assertEquals(
        new FilePermissions(0110),
        FilePermissions.fromPosixFilePermissions(
            ImmutableSet.of(PosixFilePermission.OWNER_EXECUTE, PosixFilePermission.GROUP_EXECUTE)));
    Assert.assertEquals(
        new FilePermissions(0202),
        FilePermissions.fromPosixFilePermissions(
            ImmutableSet.of(PosixFilePermission.OWNER_WRITE, PosixFilePermission.OTHERS_WRITE)));
    Assert.assertEquals(
        new FilePermissions(0044),
        FilePermissions.fromPosixFilePermissions(
            ImmutableSet.of(PosixFilePermission.GROUP_READ, PosixFilePermission.OTHERS_READ)));
    Assert.assertEquals(
        new FilePermissions(0777),
        FilePermissions.fromPosixFilePermissions(
            ImmutableSet.copyOf(PosixFilePermission.values())));
  }
}
