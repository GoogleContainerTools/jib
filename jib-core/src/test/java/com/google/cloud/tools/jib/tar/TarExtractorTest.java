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

import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
}
