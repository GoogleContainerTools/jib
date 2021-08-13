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

import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Assert;
import org.junit.Assume;
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
  public void testFromPath() {
    Assert.assertEquals(
        "/absolute/path", AbsoluteUnixPath.fromPath(Paths.get("/absolute/path")).toString());
  }

  @Test
  public void testFromPath_windows() {
    Assume.assumeTrue(System.getProperty("os.name").startsWith("Windows"));

    Assert.assertEquals(
        "/absolute/path", AbsoluteUnixPath.fromPath(Paths.get("T:\\absolute\\path")).toString());
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
  public void testResolve_relativeUnixPath() {
    AbsoluteUnixPath absoluteUnixPath1 = AbsoluteUnixPath.get("/");
    Assert.assertEquals(absoluteUnixPath1, absoluteUnixPath1.resolve(""));
    Assert.assertEquals("/file", absoluteUnixPath1.resolve("file").toString());
    Assert.assertEquals("/relative/path", absoluteUnixPath1.resolve("relative/path").toString());

    AbsoluteUnixPath absoluteUnixPath2 = AbsoluteUnixPath.get("/some/path");
    Assert.assertEquals(absoluteUnixPath2, absoluteUnixPath2.resolve(""));
    Assert.assertEquals("/some/path/file", absoluteUnixPath2.resolve("file").toString());
    Assert.assertEquals(
        "/some/path/relative/path", absoluteUnixPath2.resolve("relative/path").toString());
  }

  @Test
  public void testResolve_Path_notRelative() {
    AbsoluteUnixPath absoluteUnixPath = AbsoluteUnixPath.get("/");
    Path path = Paths.get("/not/relative");
    IllegalArgumentException exception =
        Assert.assertThrows(IllegalArgumentException.class, () -> absoluteUnixPath.resolve(path));
    Assert.assertEquals("Cannot resolve against absolute Path: " + path, exception.getMessage());
  }

  @Test
  public void testResolve_Path() {
    AbsoluteUnixPath absoluteUnixPath1 = AbsoluteUnixPath.get("/");
    Assert.assertEquals(absoluteUnixPath1, absoluteUnixPath1.resolve(Paths.get("")));
    Assert.assertEquals("/file", absoluteUnixPath1.resolve(Paths.get("file")).toString());
    Assert.assertEquals(
        "/relative/path", absoluteUnixPath1.resolve(Paths.get("relative/path")).toString());

    AbsoluteUnixPath absoluteUnixPath2 = AbsoluteUnixPath.get("/some/path");
    Assert.assertEquals(absoluteUnixPath2, absoluteUnixPath2.resolve(Paths.get("")));
    Assert.assertEquals(
        "/some/path/file", absoluteUnixPath2.resolve(Paths.get("file///")).toString());
    Assert.assertEquals(
        "/some/path/relative/path",
        absoluteUnixPath2.resolve(Paths.get("relative//path/")).toString());
  }
}
