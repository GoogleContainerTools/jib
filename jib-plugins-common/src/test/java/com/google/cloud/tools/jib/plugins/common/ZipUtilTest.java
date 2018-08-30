/*
 * Copyright 2018 Google LLC. All rights reserved.
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

package com.google.cloud.tools.jib.plugins.common;

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

/** Tests for {@link ZipUtil}. */
public class ZipUtilTest {

  @Rule public final TemporaryFolder tempFolder = new TemporaryFolder();

  @Test
  public void testUnzip() throws URISyntaxException, IOException {
    verifyUnzip(tempFolder.getRoot().toPath());
  }

  @Test
  public void testUnzip_nonExistingDestination() throws URISyntaxException, IOException {
    Path destination = tempFolder.getRoot().toPath().resolve("non/exisiting");
    verifyUnzip(destination);

    Assert.assertTrue(Files.exists(destination));
  }

  private void verifyUnzip(Path destination) throws URISyntaxException, IOException {
    Path archive = Paths.get(Resources.getResource("test-archives/test.zip").toURI());

    ZipUtil.unzip(archive, destination);

    Assert.assertTrue(Files.isDirectory(destination.resolve("my-zip/some/sub/folder")));

    Path file1 = destination.resolve("file1.txt");
    Path file2 = destination.resolve("my-zip/file2.txt");
    Path file3 = destination.resolve("my-zip/some/sub/folder/file3.txt");
    Assert.assertEquals("file1", Files.readAllLines(file1).get(0));
    Assert.assertEquals("file2", Files.readAllLines(file2).get(0));
    Assert.assertEquals("file3", Files.readAllLines(file3).get(0));
  }
}
