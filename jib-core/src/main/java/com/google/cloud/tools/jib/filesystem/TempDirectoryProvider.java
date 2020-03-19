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

package com.google.cloud.tools.jib.filesystem;

import com.google.common.io.MoreFiles;
import com.google.common.io.RecursiveDeleteOption;
import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Creates temporary directories and deletes them all when closed. */
public class TempDirectoryProvider implements Closeable {

  private final Set<Path> directories = Collections.synchronizedSet(new HashSet<>());

  /**
   * Creates a new temporary directory.
   *
   * @return the path to the temporary directory
   * @throws IOException if creating the directory fails
   */
  public Path newDirectory() throws IOException {
    Path path = Files.createTempDirectory(null);
    directories.add(path);
    return path;
  }

  /**
   * Creates a new temporary directory.
   *
   * @param parentDirectory the directory to create the temp directory inside
   * @return the path to the temporary directory
   * @throws IOException if creating the directory fails
   */
  public Path newDirectory(Path parentDirectory) throws IOException {
    Path path = Files.createTempDirectory(parentDirectory, null);
    directories.add(path);
    return path;
  }

  @Override
  public void close() {
    for (Path path : directories) {
      if (Files.exists(path)) {
        try {
          MoreFiles.deleteRecursively(path, RecursiveDeleteOption.ALLOW_INSECURE);
        } catch (IOException ignored) {
          // ignored
        }
      }
    }
    directories.clear();
  }
}
