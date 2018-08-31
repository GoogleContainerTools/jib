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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.util.function.Predicate;
import java.util.stream.Stream;

/** Recursively applies a function to each file in a directory. */
public class DirectoryWalker {

  private final Path rootDir;

  private Predicate<Path> pathFilter = path -> true;

  /**
   * Initialize with a root directory to walk.
   *
   * @param rootDir the root directory.
   * @throws NotDirectoryException if the root directory is not a directory.
   */
  public DirectoryWalker(Path rootDir) throws NotDirectoryException {
    if (!Files.isDirectory(rootDir)) {
      throw new NotDirectoryException(rootDir + " is not a directory");
    }
    this.rootDir = rootDir;
  }

  /**
   * Adds a filter to the walked paths.
   *
   * @param pathFilter the filter. {@code pathFilter} returns {@code true} if the path should be
   *     accepted and {@code false} otherwise.
   * @return this
   */
  public DirectoryWalker filter(Predicate<Path> pathFilter) {
    this.pathFilter = this.pathFilter.and(pathFilter);
    return this;
  }

  /**
   * Filters away the {@code rootDir}.
   *
   * @return this
   */
  public DirectoryWalker filterRoot() {
    filter(path -> !path.equals(rootDir));
    return this;
  }

  /**
   * Walks {@link #rootDir} and applies {@code pathConsumer} to each file. Note that {@link
   * #rootDir} itself is visited as well.
   *
   * @param pathConsumer the consumer that is applied to each file.
   * @return a list of Paths that were walked.
   * @throws IOException if the walk fails.
   */
  public ImmutableList<Path> walk(PathConsumer pathConsumer) throws IOException {
    ImmutableList<Path> files = walk();
    for (Path path : files) {
      pathConsumer.accept(path);
    }
    return files;
  }

  /**
   * Walks {@link #rootDir}.
   *
   * @return the walked files.
   * @throws IOException if walking the files fails.
   */
  public ImmutableList<Path> walk() throws IOException {
    try (Stream<Path> fileStream = Files.walk(rootDir)) {
      return fileStream.filter(pathFilter).sorted().collect(ImmutableList.toImmutableList());
    }
  }
}
