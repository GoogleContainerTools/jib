/*
 * Copyright 2019 Google LLC.
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

package com.google.cloud.tools.jib.tar;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Tests for {@link TarExtractor}. */
public class TarExtractorTest {

  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void testExtract() throws URISyntaxException, IOException {
    Path source = Paths.get(Resources.getResource("core/extract.tar").toURI());
    Path destination = temporaryFolder.getRoot().toPath();
    TarExtractor.extract(source, destination);

    Assert.assertTrue(Files.exists(destination.resolve("file A")));
    Assert.assertTrue(Files.exists(destination.resolve("file B")));
    Assert.assertTrue(
        Files.exists(destination.resolve("folder").resolve("nested folder").resolve("file C")));

    try (Stream<String> lines = Files.lines(destination.resolve("file A"))) {
      String contents = lines.collect(Collectors.joining());
      Assert.assertEquals("Hello", contents);
    }
  }

  @Test
  public void testExtract_missingDirectoryEntries() throws URISyntaxException, IOException {
    Path source = Paths.get(Resources.getResource("core/extract-missing-dirs.tar").toURI());
    Path destination = temporaryFolder.getRoot().toPath();
    TarExtractor.extract(source, destination);

    Assert.assertTrue(Files.exists(destination.resolve("world")));
    Assert.assertTrue(
        Files.exists(destination.resolve("a").resolve("b").resolve("c").resolve("world")));

    try (Stream<String> lines = Files.lines(destination.resolve("world"))) {
      String contents = lines.collect(Collectors.joining());
      Assert.assertEquals("world", contents);
    }
  }

  @Test
  public void testExtract_symlinks() throws URISyntaxException, IOException {
    Path source = Paths.get(Resources.getResource("core/symlinks.tar").toURI());
    Path destination = temporaryFolder.getRoot().toPath();
    TarExtractor.extract(source, destination);

    Assert.assertTrue(Files.isDirectory(destination.resolve("directory1")));
    Assert.assertTrue(Files.isDirectory(destination.resolve("directory2")));
    Assert.assertTrue(Files.isRegularFile(destination.resolve("directory2/regular")));
    Assert.assertTrue(Files.isSymbolicLink(destination.resolve("directory-symlink")));
    Assert.assertTrue(Files.isSymbolicLink(destination.resolve("directory1/file-symlink")));
  }

  @Test
  public void testExtract_modificationTimePreserved() throws URISyntaxException, IOException {
    Path source = Paths.get(Resources.getResource("core/extract.tar").toURI());
    Path destination = temporaryFolder.getRoot().toPath();

    TarExtractor.extract(source, destination);

    assertThat(Files.getLastModifiedTime(destination.resolve("file A")))
        .isEqualTo(FileTime.from(Instant.parse("2019-08-01T16:13:09Z")));
    assertThat(Files.getLastModifiedTime(destination.resolve("file B")))
        .isEqualTo(FileTime.from(Instant.parse("2019-08-01T16:12:00Z")));
    assertThat(Files.getLastModifiedTime(destination.resolve("folder")))
        .isEqualTo(FileTime.from(Instant.parse("2019-08-01T16:12:33Z")));
    assertThat(Files.getLastModifiedTime(destination.resolve("folder/nested folder")))
        .isEqualTo(FileTime.from(Instant.parse("2019-08-01T16:13:30Z")));
    assertThat(Files.getLastModifiedTime(destination.resolve("folder/nested folder/file C")))
        .isEqualTo(FileTime.from(Instant.parse("2019-08-01T16:12:21Z")));
  }

  @Test
  public void testExtract_reproducibleTimestampsEnabled() throws URISyntaxException, IOException {
    // The tarfile has only level1/level2/level3/file.txt packaged
    Path source = Paths.get(Resources.getResource("core/tarfile-only-file-packaged.tar").toURI());

    Path destination = temporaryFolder.getRoot().toPath();

    TarExtractor.extract(source, destination, true);

    assertThat(Files.getLastModifiedTime(destination.resolve("level-1")))
        .isEqualTo(FileTime.fromMillis(1000L));
    assertThat(Files.getLastModifiedTime(destination.resolve("level-1/level-2")))
        .isEqualTo(FileTime.fromMillis(1000L));
    assertThat(Files.getLastModifiedTime(destination.resolve("level-1/level-2/level-3")))
        .isEqualTo(FileTime.fromMillis(1000L));
    assertThat(Files.getLastModifiedTime(destination.resolve("level-1/level-2/level-3/file.txt")))
        .isEqualTo(FileTime.from(Instant.parse("2021-01-29T21:10:02Z")));
  }

  @Test
  public void testExtract_reproducibleTimestampsEnabled_destinationNotEmpty() throws IOException {
    Path destination = temporaryFolder.getRoot().toPath();
    temporaryFolder.newFile();

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> TarExtractor.extract(Paths.get("ignore"), destination, true));
    assertThat(exception).hasMessageThat().startsWith("Cannot enable reproducible timestamps");
  }
}
