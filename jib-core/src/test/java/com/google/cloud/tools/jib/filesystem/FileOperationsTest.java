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

import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Tests for {@link FileOperations}. */
public class FileOperationsTest {

  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void testCopy() throws IOException, URISyntaxException {
    Path destDir = temporaryFolder.newFolder().toPath();
    Path libraryA =
        Paths.get(Resources.getResource("core/application/dependencies/libraryA.jar").toURI());
    Path libraryB =
        Paths.get(Resources.getResource("core/application/dependencies/libraryB.jar").toURI());
    Path dirLayer = Paths.get(Resources.getResource("core/layer").toURI());

    FileOperations.copy(ImmutableList.of(libraryA, libraryB, dirLayer), destDir);

    assertFilesEqual(libraryA, destDir.resolve("libraryA.jar"));
    assertFilesEqual(libraryB, destDir.resolve("libraryB.jar"));
    Assert.assertTrue(Files.exists(destDir.resolve("layer").resolve("a").resolve("b")));
    Assert.assertTrue(Files.exists(destDir.resolve("layer").resolve("c")));
    assertFilesEqual(
        dirLayer.resolve("a").resolve("b").resolve("bar"),
        destDir.resolve("layer").resolve("a").resolve("b").resolve("bar"));
    assertFilesEqual(
        dirLayer.resolve("c").resolve("cat"), destDir.resolve("layer").resolve("c").resolve("cat"));
    assertFilesEqual(dirLayer.resolve("foo"), destDir.resolve("layer").resolve("foo"));
  }

  private void assertFilesEqual(Path file1, Path file2) throws IOException {
    Assert.assertArrayEquals(Files.readAllBytes(file1), Files.readAllBytes(file2));
  }
}
