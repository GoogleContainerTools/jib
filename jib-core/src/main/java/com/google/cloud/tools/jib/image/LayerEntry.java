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

import com.google.cloud.tools.jib.configuration.FilePermissions;
import com.google.cloud.tools.jib.filesystem.AbsoluteUnixPath;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import javax.annotation.Nullable;

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

  private final Path sourceFile;
  private final AbsoluteUnixPath extractionPath;
  private final FilePermissions permissions;
  private final long lastModifiedTime;

  /**
   * Instantiates with a source file and the path to place the source file in the container file
   * system.
   *
   * <p>For example, {@code new LayerEntry(Paths.get("HelloWorld.class"),
   * AbsoluteUnixPath.get("/app/classes/HelloWorld.class"))} adds a file {@code HelloWorld.class} to
   * the container file system at {@code /app/classes/HelloWorld.class}.
   *
   * <p>For example, {@code new LayerEntry(Paths.get("com"),
   * AbsoluteUnixPath.get("/app/classes/com"))} adds a directory to the container file system at
   * {@code /app/classes/com}. This does <b>not</b> add the contents of {@code com/}.
   *
   * @param sourceFile the source file to add to the layer
   * @param extractionPath the path in the container file system corresponding to the {@code
   *     sourceFile}
   * @param permissions the file permissions on the container. Use {@code null} to use defaults (644
   *     for files, 755 for directories)
   * @param lastModifiedTime the file modification time, default to 1 second since the epoch
   *     (https://github.com/GoogleContainerTools/jib/issues/1079)
   */
  public LayerEntry(
      Path sourceFile,
      AbsoluteUnixPath extractionPath,
      @Nullable FilePermissions permissions,
      long lastModifiedTime) {
    this.sourceFile = sourceFile;
    this.extractionPath = extractionPath;
    this.permissions =
        permissions == null
            ? Files.isDirectory(sourceFile)
                ? FilePermissions.DEFAULT_FOLDER_PERMISSIONS
                : FilePermissions.DEFAULT_FILE_PERMISSIONS
            : permissions;
    this.lastModifiedTime = lastModifiedTime;
  }

  /**
   * Instantiates with a source file and the path to place the source file in the container file
   * system.
   *
   * @param sourceFile the source file to add to the layer
   * @param extractionPath the path in the container file system corresponding to the {@code
   *     sourceFile}
   * @param permissions the file permissions on the container. Use {@code null} to use defaults (644
   *     for files, 755 for directories)
   */
  public LayerEntry(
      Path sourceFile, AbsoluteUnixPath extractionPath, @Nullable FilePermissions permissions) {
    this(sourceFile, extractionPath, permissions, 1000);
  }

  /**
   * Returns the modification time of the file in the entry.
   *
   * @return the modification time
   */
  public long getLastModifiedTime() {
    return lastModifiedTime;
  }

  /**
   * Gets the source file. The source file may be relative or absolute, so the caller should use
   * {@code getSourceFile().toAbsolutePath().toString()} for the serialized form since the
   * serialization could change independently of the path representation.
   *
   * @return the source file
   */
  public Path getSourceFile() {
    return sourceFile;
  }

  /**
   * Gets the extraction path.
   *
   * @return the extraction path
   */
  public AbsoluteUnixPath getExtractionPath() {
    return extractionPath;
  }

  /**
   * Gets the file permissions on the container.
   *
   * @return the file permissions on the container
   */
  public FilePermissions getPermissions() {
    return permissions;
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
        && extractionPath.equals(otherLayerEntry.extractionPath)
        && Objects.equals(permissions, otherLayerEntry.permissions);
  }

  @Override
  public int hashCode() {
    return Objects.hash(sourceFile, extractionPath, permissions);
  }
}
