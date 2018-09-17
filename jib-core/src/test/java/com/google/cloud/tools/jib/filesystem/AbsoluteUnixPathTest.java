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

import org.junit.Assert;
import org.junit.Test;

/** Test for {@link AbsoluteUnixPath}. */
public class AbsoluteUnixPathTest {

  @Test
  public void testGet_notAbsolute() {
    try {
      AbsoluteUnixPath.get("not/absolute");
      Assert.fail();

    } catch (IllegalArgumentException ex) {
      Assert.assertEquals(
          "Path does not start with forward slash (/): not/absolute", ex.getMessage());
    }
  }

  @Test
  public void testEquals() {
    AbsoluteUnixPath absoluteUnixPath1 = AbsoluteUnixPath.get("/absolute/path");
    AbsoluteUnixPath absoluteUnixPath2 = AbsoluteUnixPath.get("/absolute/path/");
    AbsoluteUnixPath absoluteUnixPath3 = AbsoluteUnixPath.get("/another/path");
    Assert.assertEquals(absoluteUnixPath1, absoluteUnixPath2);
    Assert.assertNotEquals(absoluteUnixPath1, absoluteUnixPath3);
  }

  @Test
  public void testResolve_absolute() {
    try {
      AbsoluteUnixPath.get("/some/path").resolve(RelativeUnixPath.get("/absolute"));
      Assert.fail();

    } catch (IllegalArgumentException ex) {
      Assert.assertEquals("Path starts with forward slash (/): /absolute", ex.getMessage());
    }
  }

  @Test
  public void testResolve() {
    AbsoluteUnixPath absoluteUnixPath1 = AbsoluteUnixPath.get("/");
    Assert.assertEquals(absoluteUnixPath1, absoluteUnixPath1.resolve(RelativeUnixPath.get("")));
    Assert.assertEquals(
        "/file", absoluteUnixPath1.resolve(RelativeUnixPath.get("file")).toString());
    Assert.assertEquals(
        "/relative/path",
        absoluteUnixPath1.resolve(RelativeUnixPath.get("relative/path")).toString());

    AbsoluteUnixPath absoluteUnixPath2 = AbsoluteUnixPath.get("/some/path");
    Assert.assertEquals(absoluteUnixPath2, absoluteUnixPath2.resolve(RelativeUnixPath.get("")));
    Assert.assertEquals(
        "/some/path/file", absoluteUnixPath2.resolve(RelativeUnixPath.get("file")).toString());
    Assert.assertEquals(
        "/some/path/relative/path",
        absoluteUnixPath2.resolve(RelativeUnixPath.get("relative/path")).toString());
  }
}
