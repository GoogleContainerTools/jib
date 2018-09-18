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

import com.google.common.base.Preconditions;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.StringJoiner;
import javax.annotation.concurrent.Immutable;

/**
 * Represents a Unix-style path in absolute form (containing all path components relative to the
 * file system root {@code /}).
 *
 * <p>This class is immutable and thread-safe.
 */
@Immutable
public class AbsoluteUnixPath {

  /**
   * Gets a new {@link AbsoluteUnixPath} from a Unix-style path string. The path must begin with a
   * forward slash ({@code /}).
   *
   * @param path the Unix-style path string in absolute form
   * @return a new {@link AbsoluteUnixPath}
   */
  public static AbsoluteUnixPath get(String path) {
    Preconditions.checkArgument(
        path.startsWith("/"), "Path does not start with forward slash (/): " + path);
    return fromPath(Paths.get(path));
  }

  /**
   * Gets a new {@link AbsoluteUnixPath} from a {@link Path}. The {@code path} must be absolute
   * (indicated by a non-null {@link Path#getRoot}).
   *
   * @param path the absolute {@link Path} to convert to an {@link AbsoluteUnixPath}.
   * @return a new {@link AbsoluteUnixPath}
   */
  private static AbsoluteUnixPath fromPath(Path path) {
    Preconditions.checkArgument(
        path.getRoot() != null, "Cannot create AbsoluteUnixPath from non-absolute Path: " + path);

    StringJoiner pathJoiner = new StringJoiner("/", "/", "");
    for (Path pathComponent : path) {
      pathJoiner.add(pathComponent.getFileName().toString());
    }
    return new AbsoluteUnixPath(pathJoiner.toString());
  }

  /**
   * Unix-style path, in absolute form. Does not end with trailing slash, except for the file system
   * root ({@code /}).
   */
  private final String unixPath;

  private AbsoluteUnixPath(String unixPath) {
    this.unixPath = unixPath;
  }

  /**
   * Resolves this path against another relative path.
   *
   * @param relativeUnixPath the relative path to resolve against
   * @return a new {@link AbsoluteUnixPath} representing the resolved path
   */
  public AbsoluteUnixPath resolve(RelativeUnixPath relativeUnixPath) {
    StringJoiner pathJoiner = new StringJoiner("/", unixPath + '/', "");
    for (String pathComponent : relativeUnixPath.getRelativePathComponents()) {
      pathJoiner.add(pathComponent);
    }
    return AbsoluteUnixPath.get(pathJoiner.toString());
  }

  /**
   * Returns the string form of the absolute Unix-style path.
   *
   * @return the string form
   */
  @Override
  public String toString() {
    return unixPath;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof AbsoluteUnixPath)) {
      return false;
    }
    AbsoluteUnixPath otherAbsoluteUnixPath = (AbsoluteUnixPath) other;
    return unixPath.equals(otherAbsoluteUnixPath.unixPath);
  }

  @Override
  public int hashCode() {
    return unixPath.hashCode();
  }
}
