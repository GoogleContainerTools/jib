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

import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.buildplan.FileEntry;
import com.google.cloud.tools.jib.api.buildplan.FilePermissions;
import java.nio.file.Path;
import java.time.Instant;
import javax.annotation.concurrent.Immutable;

/**
 * Represents an entry in the layer. A layer consists of many entries that can be converted into tar
 * archive entries.
 *
 * <p>This class is immutable and thread-safe.
 *
 * @deprecated Use {@link FileEntry}.
 */
@Deprecated
@Immutable
public class LayerEntry {

  private final FileEntry fileEntry;

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
   * <p>Note that:
   *
   * <ul>
   *   <li>Entry source files can be either files or directories.
   *   <li>Adding a directory does not include the contents of the directory. Each file under a
   *       directory must be added as a separate {@link LayerEntry}.
   * </ul>
   *
   * @param sourceFile the source file to add to the layer
   * @param extractionPath the path in the container file system corresponding to the {@code
   *     sourceFile}
   * @param permissions the file permissions on the container
   * @param modificationTime the file modification time
   */
  public LayerEntry(
      Path sourceFile,
      AbsoluteUnixPath extractionPath,
      FilePermissions permissions,
      Instant modificationTime) {
    this(new FileEntry(sourceFile, extractionPath, permissions, modificationTime));
  }

  LayerEntry(FileEntry entry) {
    fileEntry = entry;
  }

  /**
   * Returns the modification time of the file in the entry.
   *
   * @return the modification time
   */
  public Instant getModificationTime() {
    return fileEntry.getModificationTime();
  }

  /**
   * Gets the source file. The source file may be relative or absolute, so the caller should use
   * {@code getSourceFile().toAbsolutePath().toString()} for the serialized form since the
   * serialization could change independently of the path representation.
   *
   * @return the source file
   */
  public Path getSourceFile() {
    return fileEntry.getSourceFile();
  }

  /**
   * Gets the extraction path.
   *
   * @return the extraction path
   */
  public AbsoluteUnixPath getExtractionPath() {
    return fileEntry.getExtractionPath();
  }

  /**
   * Gets the file permissions on the container.
   *
   * @return the file permissions on the container
   */
  public FilePermissions getPermissions() {
    return fileEntry.getPermissions();
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
    return toFileEntry().equals(otherLayerEntry.toFileEntry());
  }

  @Override
  public int hashCode() {
    return fileEntry.hashCode();
  }

  FileEntry toFileEntry() {
    return fileEntry;
  }
}
