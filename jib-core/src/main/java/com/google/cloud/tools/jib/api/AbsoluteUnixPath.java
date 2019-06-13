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

package com.google.cloud.tools.jib.api;

import com.google.cloud.tools.jib.filesystem.UnixPathParser;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
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
   * @param unixPath the Unix-style path string in absolute form
   * @return a new {@link AbsoluteUnixPath}
   */
  public static AbsoluteUnixPath get(String unixPath) {
    Preconditions.checkArgument(
        unixPath.startsWith("/"), "Path does not start with forward slash (/): " + unixPath);

    return new AbsoluteUnixPath(UnixPathParser.parse(unixPath));
  }

  /**
   * Gets a new {@link AbsoluteUnixPath} from a {@link Path}. The {@code path} must be absolute
   * (indicated by a non-null {@link Path#getRoot}).
   *
   * @param path the absolute {@link Path} to convert to an {@link AbsoluteUnixPath}.
   * @return a new {@link AbsoluteUnixPath}
   */
  public static AbsoluteUnixPath fromPath(Path path) {
    Preconditions.checkArgument(
        path.getRoot() != null, "Cannot create AbsoluteUnixPath from non-absolute Path: " + path);

    ImmutableList.Builder<String> pathComponents =
        ImmutableList.builderWithExpectedSize(path.getNameCount());
    for (Path pathComponent : path) {
      pathComponents.add(pathComponent.toString());
    }
    return new AbsoluteUnixPath(pathComponents.build());
  }

  /** Path components after the file system root. This should always match {@link #unixPath}. */
  private final ImmutableList<String> pathComponents;

  /**
   * Unix-style path, in absolute form. Does not end with trailing slash, except for the file system
   * root ({@code /}). This should always match {@link #pathComponents}.
   */
  private final String unixPath;

  private AbsoluteUnixPath(ImmutableList<String> pathComponents) {
    this.pathComponents = pathComponents;

    StringJoiner pathJoiner = new StringJoiner("/", "/", "");
    for (String pathComponent : pathComponents) {
      pathJoiner.add(pathComponent);
    }
    unixPath = pathJoiner.toString();
  }

  /**
   * Resolves this path against another relative path.
   *
   * @param relativeUnixPath the relative path to resolve against
   * @return a new {@link AbsoluteUnixPath} representing the resolved path
   */
  public AbsoluteUnixPath resolve(RelativeUnixPath relativeUnixPath) {
    ImmutableList.Builder<String> newPathComponents =
        ImmutableList.builderWithExpectedSize(
            pathComponents.size() + relativeUnixPath.getRelativePathComponents().size());
    newPathComponents.addAll(pathComponents);
    newPathComponents.addAll(relativeUnixPath.getRelativePathComponents());
    return new AbsoluteUnixPath(newPathComponents.build());
  }

  /**
   * Resolves this path against another relative path (by the name elements of {@code
   * relativePath}).
   *
   * @param relativePath the relative path to resolve against
   * @return a new {@link AbsoluteUnixPath} representing the resolved path
   */
  public AbsoluteUnixPath resolve(Path relativePath) {
    Preconditions.checkArgument(
        relativePath.getRoot() == null, "Cannot resolve against absolute Path: " + relativePath);

    return AbsoluteUnixPath.fromPath(Paths.get(unixPath).resolve(relativePath));
  }

  /**
   * Resolves this path against another relative Unix path in string form.
   *
   * @param relativeUnixPath the relative path to resolve against
   * @return a new {@link AbsoluteUnixPath} representing the resolved path
   */
  public AbsoluteUnixPath resolve(String relativeUnixPath) {
    return resolve(RelativeUnixPath.get(relativeUnixPath));
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
