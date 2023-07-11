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

import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests for {@link DirectoryWalker}. */
class DirectoryWalkerTest {

  private final Set<Path> walkedPaths = new HashSet<>();
  private final PathConsumer addToWalkedPaths = walkedPaths::add;

  private Path testDir;

  @BeforeEach
  void setUp() throws URISyntaxException {
    testDir = Paths.get(Resources.getResource("core/layer").toURI());
  }

  @Test
  void testWalk() throws IOException {
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

  @Test
  void testWalk_withFilter() throws IOException {
    // Filters to immediate subdirectories of testDir, and foo.
    new DirectoryWalker(testDir)
        .filter(path -> path.getParent().equals(testDir))
        .filter(path -> !path.endsWith("foo"))
        .walk(addToWalkedPaths);

    Set<Path> expectedPaths =
        new HashSet<>(Arrays.asList(testDir.resolve("a"), testDir.resolve("c")));
    Assert.assertEquals(expectedPaths, walkedPaths);
  }

  @Test
  void testWalk_withFilterRoot() throws IOException {
    new DirectoryWalker(testDir).filterRoot().walk(addToWalkedPaths);

    Set<Path> expectedPaths =
        new HashSet<>(
            Arrays.asList(
                testDir.resolve("a"),
                testDir.resolve("a").resolve("b"),
                testDir.resolve("a").resolve("b").resolve("bar"),
                testDir.resolve("c"),
                testDir.resolve("c").resolve("cat"),
                testDir.resolve("foo")));
    Assert.assertEquals(expectedPaths, walkedPaths);
  }
}
