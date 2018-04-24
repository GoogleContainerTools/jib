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

package com.google.cloud.tools.jib.filesystem;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;

/** Recursively applies a function to each file in a directory. */
public class DirectoryWalker {

  private final Path rootDir;

  @Nullable private Predicate<? super Path> pathFilter;

  /** Initialize with a root directory to walk. */
  public DirectoryWalker(Path rootDir) throws NotDirectoryException {
    if (!Files.isDirectory(rootDir)) {
      throw new NotDirectoryException(rootDir + " is not a directory");
    }
    this.rootDir = rootDir;
  }

  /** Adds a filter to the walked paths. */
  public DirectoryWalker filter(@Nullable Predicate<? super Path> pathFilter) {
    this.pathFilter = pathFilter;
    return this;
  }

  /**
   * Walks {@link #rootDir} and applies {@code pathConsumer} to each file. Note that {@link
   * #rootDir} itself is visited as well.
   */
  public List<Path> walk(PathConsumer pathConsumer) throws IOException {
    List<Path> files = walk();
    for (Path path : files) {
      pathConsumer.accept(path);
    }
    return files;
  }

  /** Walks {@link #rootDir} and returns the walked files. */
  public List<Path> walk() throws IOException {
    try (Stream<Path> fileStream = Files.walk(rootDir)) {
      Stream<Path> filteredFileStream = fileStream;
      if (pathFilter != null) {
        filteredFileStream = fileStream.filter(pathFilter);
      }
      return filteredFileStream.sorted().collect(Collectors.toList());
    }
  }
}
