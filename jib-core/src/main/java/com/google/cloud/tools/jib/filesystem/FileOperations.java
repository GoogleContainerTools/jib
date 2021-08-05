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

import com.google.common.base.Verify;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Static methods for operating on the filesystem. */
public class FileOperations {

  /**
   * Copies {@code sourceFiles} to the {@code destDir} directory.
   *
   * @param sourceFiles the list of source files.
   * @param destDir the directory to copy the files to.
   * @throws IOException if the copy fails.
   */
  public static void copy(ImmutableList<Path> sourceFiles, Path destDir) throws IOException {
    for (Path sourceFile : sourceFiles) {
      PathConsumer copyPathConsumer =
          path -> {
            // Creates the same path in the destDir.
            Path parent = Verify.verifyNotNull(sourceFile.getParent());
            Path destPath = destDir.resolve(parent.relativize(path));
            if (Files.isDirectory(path)) {
              Files.createDirectories(destPath);
            } else {
              Files.copy(path, destPath);
            }
          };

      if (Files.isDirectory(sourceFile)) {
        new DirectoryWalker(sourceFile).walk(copyPathConsumer);
      } else {
        copyPathConsumer.accept(sourceFile);
      }
    }
  }

  private FileOperations() {}
}
