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

package com.google.cloud.tools.jib.plugins.common;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Assert;
import org.junit.Assume;
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

  @Test
  public void testZipSlipVulnerability_windows() throws URISyntaxException {
    Assume.assumeTrue(System.getProperty("os.name").startsWith("Windows"));

    Path archive =
        Paths.get(Resources.getResource("plugins-common/test-archives/zip-slip-win.zip").toURI());
    verifyZipSlipSafe(archive);
  }

  @Test
  public void testZipSlipVulnerability_unix() throws URISyntaxException {
    Assume.assumeFalse(System.getProperty("os.name").startsWith("Windows"));

    Path archive =
        Paths.get(Resources.getResource("plugins-common/test-archives/zip-slip.zip").toURI());
    verifyZipSlipSafe(archive);
  }

  @Test
  public void testUnzip_modificationTimePreserved() throws URISyntaxException, IOException {
    Path destination = tempFolder.getRoot().toPath();
    Path archive =
        Paths.get(Resources.getResource("plugins-common/test-archives/test.zip").toURI());

    ZipUtil.unzip(archive, destination);

    assertThat(Files.getLastModifiedTime(destination.resolve("file1.txt")))
        .isEqualTo(FileTime.from(Instant.parse("2018-08-30T14:53:05Z")));
    assertThat(Files.getLastModifiedTime(destination.resolve("my-zip/file2.txt")))
        .isEqualTo(FileTime.from(Instant.parse("2018-08-30T14:53:44Z")));
    assertThat(Files.getLastModifiedTime(destination.resolve("my-zip/some/sub/folder/file3.txt")))
        .isEqualTo(FileTime.from(Instant.parse("2018-08-30T15:16:12Z")));
  }

  private void verifyUnzip(Path destination) throws URISyntaxException, IOException {
    Path archive =
        Paths.get(Resources.getResource("plugins-common/test-archives/test.zip").toURI());

    ZipUtil.unzip(archive, destination);

    Assert.assertTrue(Files.isDirectory(destination.resolve("my-zip/some/sub/folder")));

    Path file1 = destination.resolve("file1.txt");
    Path file2 = destination.resolve("my-zip/file2.txt");
    Path file3 = destination.resolve("my-zip/some/sub/folder/file3.txt");
    Assert.assertEquals("file1", Files.readAllLines(file1).get(0));
    Assert.assertEquals("file2", Files.readAllLines(file2).get(0));
    Assert.assertEquals("file3", Files.readAllLines(file3).get(0));
  }

  private void verifyZipSlipSafe(Path archive) {
    try {
      ZipUtil.unzip(archive, tempFolder.getRoot().toPath());
      Assert.fail("Should block Zip-Slip");
    } catch (IOException ex) {
      MatcherAssert.assertThat(
          ex.getMessage(),
          CoreMatchers.startsWith("Blocked unzipping files outside destination: "));
    }
  }
}
