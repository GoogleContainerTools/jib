/*
 * Copyright 2020 Google LLC.
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

package com.google.cloud.tools.jib.cli.buildfile;

import com.google.cloud.tools.jib.api.buildplan.FilePermissions;
import java.time.Instant;
import org.junit.Assert;
import org.junit.Test;

public class FilePropertiesStackTest {

  @Test
  public void testDefaults() {
    FilePropertiesStack testStack = new FilePropertiesStack();

    Assert.assertEquals(FilePermissions.fromOctalString("644"), testStack.getFilePermissions());
    Assert.assertEquals(
        FilePermissions.fromOctalString("755"), testStack.getDirectoryPermissions());
    Assert.assertEquals("", testStack.getOwnership());
    Assert.assertEquals(Instant.ofEpochSecond(1), testStack.getModificationTime());
  }

  @Test
  public void testPush_simple() {
    FilePropertiesStack testStack = new FilePropertiesStack();

    testStack.push(new FilePropertiesSpec("111", "111", "1", "1", "1"));

    Assert.assertEquals(FilePermissions.fromOctalString("111"), testStack.getFilePermissions());
    Assert.assertEquals(
        FilePermissions.fromOctalString("111"), testStack.getDirectoryPermissions());
    Assert.assertEquals("1:1", testStack.getOwnership());
    Assert.assertEquals(Instant.ofEpochMilli(1), testStack.getModificationTime());
  }

  @Test
  public void testPush_stacking() {
    FilePropertiesStack testStack = new FilePropertiesStack();

    testStack.push(new FilePropertiesSpec("111", "111", "1", "1", "1"));
    testStack.push(new FilePropertiesSpec(null, "222", "2", null, null));
    testStack.push(new FilePropertiesSpec("333", null, "3", null, "3"));

    Assert.assertEquals(FilePermissions.fromOctalString("333"), testStack.getFilePermissions());
    Assert.assertEquals(
        FilePermissions.fromOctalString("222"), testStack.getDirectoryPermissions());
    Assert.assertEquals("3:1", testStack.getOwnership());
    Assert.assertEquals(Instant.ofEpochMilli(3), testStack.getModificationTime());
  }

  @Test
  public void testPush_tooMany() {
    FilePropertiesStack testStack = new FilePropertiesStack();

    testStack.push(new FilePropertiesSpec("111", "111", "1", "1", "1"));
    testStack.push(new FilePropertiesSpec("111", "111", "1", "1", "1"));
    testStack.push(new FilePropertiesSpec("111", "111", "1", "1", "1"));

    try {
      testStack.push(new FilePropertiesSpec("111", "111", "1", "1", "1"));
      Assert.fail();
    } catch (IllegalStateException ise) {
      Assert.assertEquals("Error in file properties stack push, stacking over 3", ise.getMessage());
    }
  }

  @Test
  public void testPop_toZero() {
    FilePropertiesStack testStack = new FilePropertiesStack();

    testStack.push(new FilePropertiesSpec("111", "111", "1", "1", "1"));
    testStack.pop();

    Assert.assertEquals(FilePermissions.fromOctalString("644"), testStack.getFilePermissions());
    Assert.assertEquals(
        FilePermissions.fromOctalString("755"), testStack.getDirectoryPermissions());
    Assert.assertEquals("", testStack.getOwnership());
    Assert.assertEquals(Instant.ofEpochSecond(1), testStack.getModificationTime());
  }

  @Test
  public void testPop_toOlderState() {
    FilePropertiesStack testStack = new FilePropertiesStack();

    testStack.push(new FilePropertiesSpec("111", "111", "1", "1", "1"));
    testStack.push(new FilePropertiesSpec(null, "222", "2", null, null));

    testStack.pop();

    Assert.assertEquals(FilePermissions.fromOctalString("111"), testStack.getFilePermissions());
    Assert.assertEquals(
        FilePermissions.fromOctalString("111"), testStack.getDirectoryPermissions());
    Assert.assertEquals("1:1", testStack.getOwnership());
    Assert.assertEquals(Instant.ofEpochMilli(1), testStack.getModificationTime());
  }

  @Test
  public void testPop_nothingToPop() {
    FilePropertiesStack testStack = new FilePropertiesStack();

    try {
      testStack.pop();
      Assert.fail();
    } catch (IllegalStateException ise) {
      Assert.assertEquals("Error in file properties stack pop, popping at 0", ise.getMessage());
    }
  }

  @Test
  public void testGetOwnership_onlyUser() {
    FilePropertiesStack testStack = new FilePropertiesStack();
    testStack.push(new FilePropertiesSpec(null, null, "u", null, null));

    Assert.assertEquals("u", testStack.getOwnership());
  }

  @Test
  public void testGetOwnership_onlyGroup() {
    FilePropertiesStack testStack = new FilePropertiesStack();
    testStack.push(new FilePropertiesSpec(null, null, null, "g", null));

    Assert.assertEquals(":g", testStack.getOwnership());
  }

  @Test
  public void testGetOwnership_userAndGroup() {
    FilePropertiesStack testStack = new FilePropertiesStack();
    testStack.push(new FilePropertiesSpec(null, null, "u", "g", null));

    Assert.assertEquals("u:g", testStack.getOwnership());
  }

  @Test
  public void testGetOwnership_noUserNoGroup() {
    FilePropertiesStack testStack = new FilePropertiesStack();
    testStack.push(new FilePropertiesSpec(null, null, null, null, null));

    Assert.assertEquals("", testStack.getOwnership());
  }
}
