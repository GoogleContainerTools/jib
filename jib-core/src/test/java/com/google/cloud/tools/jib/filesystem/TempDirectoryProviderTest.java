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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Assert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests for {@link TempDirectoryProvider}. */
class TempDirectoryProviderTest {

  private static void createFilesInDirectory(Path directory)
      throws IOException, URISyntaxException {
    Path testFilesDirectory = Paths.get(Resources.getResource("core/layer").toURI());
    new DirectoryWalker(testFilesDirectory)
        .filterRoot()
        .walk(path -> Files.copy(path, directory.resolve(testFilesDirectory.relativize(path))));
  }

  @TempDir public Path temporaryFolder;

  @Test
  void testClose_directoriesDeleted() throws IOException, URISyntaxException {
    Path parent = Files.createTempDirectory(temporaryFolder, "jib");

    try (TempDirectoryProvider tempDirectoryProvider = new TempDirectoryProvider()) {
      Path directory1 = tempDirectoryProvider.newDirectory(parent);
      createFilesInDirectory(directory1);
      Path directory2 = tempDirectoryProvider.newDirectory(parent);
      createFilesInDirectory(directory2);

      tempDirectoryProvider.close();
      Assert.assertFalse(Files.exists(directory1));
      Assert.assertFalse(Files.exists(directory2));
    }
  }

  @Test
  void testClose_directoryNotDeletedIfMoved() throws IOException, URISyntaxException {
    Path destinationParent = Files.createTempDirectory(temporaryFolder, "jib");

    try (TempDirectoryProvider tempDirectoryProvider = new TempDirectoryProvider()) {
      Path directory = tempDirectoryProvider.newDirectory(destinationParent);
      createFilesInDirectory(directory);

      Assert.assertFalse(Files.exists(destinationParent.resolve("destination")));
      Files.move(directory, destinationParent.resolve("destination"));

      tempDirectoryProvider.close();
      Assert.assertFalse(Files.exists(directory));
      Assert.assertTrue(Files.exists(destinationParent.resolve("destination")));
    }
  }
}
