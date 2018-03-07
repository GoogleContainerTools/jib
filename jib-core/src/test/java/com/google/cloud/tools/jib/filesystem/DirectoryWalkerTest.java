/*
 * Copyright 2018 Google Inc.
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

import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.Assert;
import org.junit.Test;

/** Tests for {@link DirectoryWalker}. */
public class DirectoryWalkerTest {

  @Test
  public void testWalk() throws URISyntaxException, IOException {
    Path testDir = Paths.get(Resources.getResource("layer").toURI());

    Set<Path> walkedPaths = new HashSet<>();
    PathConsumer addToWalkedPaths = walkedPaths::add;

    new DirectoryWalker(testDir).walk(addToWalkedPaths);

    Set<Path> expectedPaths =
        new HashSet<>(
            Arrays.asList(
                testDir,
                testDir.resolve("a"),
                testDir.resolve("a").resolve("b"),
                testDir.resolve("a").resolve("b").resolve("bar"),
                testDir.resolve("c"),
                testDir.resolve("c").resolve("cat"),
                testDir.resolve("foo")));
    Assert.assertEquals(expectedPaths, walkedPaths);
  }
}
