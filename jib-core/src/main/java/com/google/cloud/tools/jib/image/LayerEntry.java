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

package com.google.cloud.tools.jib.image;

import com.google.common.annotations.VisibleForTesting;
import java.nio.file.Path;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * Represents an entry in the layer. A layer consists of many entries that can be converted into tar
 * archive entries.
 *
 * <p>Note that:
 *
 * <ul>
 *   <li>Entry source files can be either files or directories.
 *   <li>Adding a directory does not include the contents of the directory. Each file under a
 *       directory must be added as a separate {@link LayerEntry}.
 * </ul>
 */
public class LayerEntry {

  /**
   * Stringifies {@code path} in unix form.
   *
   * @param path the path
   * @return the string form of the path
   */
  private static String toUnixPath(Path path) {
    StringJoiner pathJoiner =
        path.isAbsolute() ? new StringJoiner("/", "/", "") : new StringJoiner("/");
    for (Path pathComponent : path) {
      pathJoiner.add(pathComponent.toString());
    }
    return pathJoiner.toString();
  }

  private final Path sourceFile;
  private final Path extractionPath;

  /**
   * Instantiates with a source file and the path to place the source file in the container file
   * system.
   *
   * <p>For example, {@code new LayerEntry(Paths.get("HelloWorld.class"),
   * Paths.get("/app/classes/HelloWorld.class"))} adds a file {@code HelloWorld.class} to the
   * container file system at {@code /app/classes/HelloWorld.class}.
   *
   * <p>For example, {@code new LayerEntry(Paths.get("com"), Paths.get("/app/classes/com"))} adds a
   * directory to the container file system at {@code /app/classes/com}. This does <b>not</b> add
   * the contents of {@code com/}.
   *
   * @param sourceFile the source file to add to the layer
   * @param extractionPath the path in the container file system corresponding to the {@code
   *     sourceFile} (relative to root {@code /})
   */
  public LayerEntry(Path sourceFile, Path extractionPath) {
    this.sourceFile = sourceFile.toAbsolutePath();
    this.extractionPath = extractionPath;
  }

  /**
   * Gets the source file.
   *
   * <p>Do <b>not</b> call {@link Path#toString} on this - use {@link #getSourceFileString} instead.
   * This path can be relative or absolute, and {@link #getSourceFileString} can also be relative or
   * absolute, but callers should rely on {@link #getSourceFileString} for the serialized form since
   * the serialization could change independently of the path representation.
   *
   * @return the source file
   */
  public Path getSourceFile() {
    return sourceFile;
  }

  /**
   * Gets the extraction path.
   *
   * <p>Do <b>not</b> call {@link Path#toString} on this - use {@link #getExtractionPathString}
   * instead. This path can be relative or absolute, and {@link #getExtractionPathString} can also
   * be relative or absolute, but callers should rely on {@link #getExtractionPathString} for the
   * serialized form since the serialization could change independently of the path representation.
   *
   * @return the extraction path
   */
  public Path getExtractionPath() {
    return extractionPath;
  }

  /**
   * Gets the source file absolute path in string form.
   *
   * @return the source file path
   */
  public String getSourceFileString() {
    return toUnixPath(sourceFile.toAbsolutePath());
  }

  /**
   * Gets the extraction path in string form. This does <b>not</b> convert the extraction path to an
   * absolute path.
   *
   * @return the extraction path
   */
  public String getExtractionPathString() {
    return toUnixPath(extractionPath);
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof LayerEntry)) {
      return false;
    }
    LayerEntry otherLayerEntry = (LayerEntry) other;
    return sourceFile.equals(otherLayerEntry.sourceFile)
        && extractionPath.equals(otherLayerEntry.extractionPath);
  }

  @Override
  public int hashCode() {
    return Objects.hash(sourceFile, extractionPath);
  }

  @Override
  @VisibleForTesting
  public String toString() {
    return getSourceFileString() + "\t" + getExtractionPathString();
  }
}
