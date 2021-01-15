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
import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer;
import com.google.cloud.tools.jib.api.buildplan.FileEntry;
import com.google.cloud.tools.jib.api.buildplan.FilePermissions;
import com.google.cloud.tools.jib.api.buildplan.FilePermissionsProvider;
import com.google.cloud.tools.jib.api.buildplan.ModificationTimeProvider;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.concurrent.Immutable;

/**
 * Configures how to build a layer in the container image. Instantiate with {@link #builder}.
 *
 * @deprecated Use {@link FileEntriesLayer}.
 */
@Deprecated
@Immutable
public class LayerConfiguration {

  /** Builds a {@link LayerConfiguration}. */
  public static class Builder {

    private FileEntriesLayer.Builder builder = FileEntriesLayer.builder();

    private Builder() {}

    /**
     * Sets a name for this layer. This name does not affect the contents of the layer.
     *
     * @param name the name
     * @return this
     */
    public Builder setName(String name) {
      builder.setName(name);
      return this;
    }

    /**
     * Sets entries for the layer.
     *
     * @param entries file entries in the layer
     * @return this
     */
    public Builder setEntries(List<LayerEntry> entries) {
      builder.setEntries(
          entries.stream().map(LayerEntry::toFileEntry).collect(Collectors.toList()));
      return this;
    }

    /**
     * Adds an entry to the layer.
     *
     * @param entry the layer entry to add
     * @return this
     */
    public Builder addEntry(LayerEntry entry) {
      builder.addEntry(entry.toFileEntry());
      return this;
    }

    /**
     * Adds an entry to the layer. Only adds the single source file to the exact path in the
     * container file system.
     *
     * <p>For example, {@code addEntry(Paths.get("myfile"),
     * AbsoluteUnixPath.get("/path/in/container"))} adds a file {@code myfile} to the container file
     * system at {@code /path/in/container}.
     *
     * <p>For example, {@code addEntry(Paths.get("mydirectory"),
     * AbsoluteUnixPath.get("/path/in/container"))} adds a directory {@code mydirectory/} to the
     * container file system at {@code /path/in/container/}. This does <b>not</b> add the contents
     * of {@code mydirectory}.
     *
     * @param sourceFile the source file to add to the layer
     * @param pathInContainer the path in the container file system corresponding to the {@code
     *     sourceFile}
     * @return this
     */
    public Builder addEntry(Path sourceFile, AbsoluteUnixPath pathInContainer) {
      builder.addEntry(sourceFile, pathInContainer);
      return this;
    }

    /**
     * Adds an entry to the layer with the given permissions. Only adds the single source file to
     * the exact path in the container file system. See {@link Builder#addEntry(Path,
     * AbsoluteUnixPath)} for more information.
     *
     * @param sourceFile the source file to add to the layer
     * @param pathInContainer the path in the container file system corresponding to the {@code
     *     sourceFile}
     * @param permissions the file permissions on the container
     * @return this
     * @see Builder#addEntry(Path, AbsoluteUnixPath)
     * @see FilePermissions#DEFAULT_FILE_PERMISSIONS
     * @see FilePermissions#DEFAULT_FOLDER_PERMISSIONS
     */
    public Builder addEntry(
        Path sourceFile, AbsoluteUnixPath pathInContainer, FilePermissions permissions) {
      builder.addEntry(sourceFile, pathInContainer, permissions);
      return this;
    }

    /**
     * Adds an entry to the layer with the given file modification time. Only adds the single source
     * file to the exact path in the container file system. See {@link Builder#addEntry(Path,
     * AbsoluteUnixPath)} for more information.
     *
     * @param sourceFile the source file to add to the layer
     * @param pathInContainer the path in the container file system corresponding to the {@code
     *     sourceFile}
     * @param modificationTime the file modification time
     * @return this
     * @see Builder#addEntry(Path, AbsoluteUnixPath)
     */
    public Builder addEntry(
        Path sourceFile, AbsoluteUnixPath pathInContainer, Instant modificationTime) {
      builder.addEntry(sourceFile, pathInContainer, modificationTime);
      return this;
    }

    /**
     * Adds an entry to the layer with the given permissions and file modification time. Only adds
     * the single source file to the exact path in the container file system. See {@link
     * Builder#addEntry(Path, AbsoluteUnixPath)} for more information.
     *
     * @param sourceFile the source file to add to the layer
     * @param pathInContainer the path in the container file system corresponding to the {@code
     *     sourceFile}
     * @param permissions the file permissions on the container
     * @param modificationTime the file modification time
     * @return this
     * @see Builder#addEntry(Path, AbsoluteUnixPath)
     * @see FilePermissions#DEFAULT_FILE_PERMISSIONS
     * @see FilePermissions#DEFAULT_FOLDER_PERMISSIONS
     */
    public Builder addEntry(
        Path sourceFile,
        AbsoluteUnixPath pathInContainer,
        FilePermissions permissions,
        Instant modificationTime) {
      builder.addEntry(new FileEntry(sourceFile, pathInContainer, permissions, modificationTime));
      return this;
    }

    /**
     * Adds an entry to the layer. If the source file is a directory, the directory and its contents
     * will be added recursively.
     *
     * <p>For example, {@code addEntryRecursive(Paths.get("mydirectory",
     * AbsoluteUnixPath.get("/path/in/container"))} adds {@code mydirectory} to the container file
     * system at {@code /path/in/container} such that {@code mydirectory/subfile} is found at {@code
     * /path/in/container/subfile}.
     *
     * @param sourceFile the source file to add to the layer recursively
     * @param pathInContainer the path in the container file system corresponding to the {@code
     *     sourceFile}
     * @return this
     * @throws IOException if an exception occurred when recursively listing the directory
     */
    public Builder addEntryRecursive(Path sourceFile, AbsoluteUnixPath pathInContainer)
        throws IOException {
      builder.addEntryRecursive(sourceFile, pathInContainer);
      return this;
    }

    /**
     * Adds an entry to the layer. If the source file is a directory, the directory and its contents
     * will be added recursively.
     *
     * @param sourceFile the source file to add to the layer recursively
     * @param pathInContainer the path in the container file system corresponding to the {@code
     *     sourceFile}
     * @param filePermissionProvider a provider that takes a source path and destination path on the
     *     container and returns the file permissions that should be set for that path
     * @return this
     * @throws IOException if an exception occurred when recursively listing the directory
     */
    public Builder addEntryRecursive(
        Path sourceFile,
        AbsoluteUnixPath pathInContainer,
        FilePermissionsProvider filePermissionProvider)
        throws IOException {
      builder.addEntryRecursive(sourceFile, pathInContainer, filePermissionProvider);
      return this;
    }

    /**
     * Adds an entry to the layer. If the source file is a directory, the directory and its contents
     * will be added recursively.
     *
     * @param sourceFile the source file to add to the layer recursively
     * @param pathInContainer the path in the container file system corresponding to the {@code
     *     sourceFile}
     * @param filePermissionProvider a provider that takes a source path and destination path on the
     *     container and returns the file permissions that should be set for that path
     * @param modificationTimeProvider a provider that takes a source path and destination path on
     *     the container and returns the file modification time that should be set for that path
     * @return this
     * @throws IOException if an exception occurred when recursively listing the directory
     */
    public Builder addEntryRecursive(
        Path sourceFile,
        AbsoluteUnixPath pathInContainer,
        FilePermissionsProvider filePermissionProvider,
        ModificationTimeProvider modificationTimeProvider)
        throws IOException {
      builder.addEntryRecursive(
          sourceFile, pathInContainer, filePermissionProvider, modificationTimeProvider);
      return this;
    }

    /**
     * Returns the built {@link LayerConfiguration}.
     *
     * @return the built {@link LayerConfiguration}
     */
    public LayerConfiguration build() {
      return new LayerConfiguration(builder.build());
    }
  }

  /** Provider that returns default file permissions (644 for files, 755 for directories). */
  public static final FilePermissionsProvider DEFAULT_FILE_PERMISSIONS_PROVIDER =
      FileEntriesLayer.DEFAULT_FILE_PERMISSIONS_PROVIDER;

  /** Default file modification time (EPOCH + 1 second). */
  public static final Instant DEFAULT_MODIFICATION_TIME =
      FileEntriesLayer.DEFAULT_MODIFICATION_TIME;

  /** Provider that returns default file modification time (EPOCH + 1 second). */
  public static final ModificationTimeProvider DEFAULT_MODIFICATION_TIME_PROVIDER =
      FileEntriesLayer.DEFAULT_MODIFICATION_TIME_PROVIDER;

  /**
   * Gets a new {@link Builder} for {@link LayerConfiguration}.
   *
   * @return a new {@link Builder}
   */
  public static Builder builder() {
    return new Builder();
  }

  private final FileEntriesLayer fileEntriesLayer;

  private LayerConfiguration(FileEntriesLayer fileEntriesLayer) {
    this.fileEntriesLayer = fileEntriesLayer;
  }

  /**
   * Gets the name.
   *
   * @return the name
   */
  public String getName() {
    return fileEntriesLayer.getName();
  }

  /**
   * Gets the list of entries.
   *
   * @return the list of entries
   */
  public ImmutableList<LayerEntry> getLayerEntries() {
    List<FileEntry> entries = fileEntriesLayer.getEntries();
    return entries.stream().map(LayerEntry::new).collect(ImmutableList.toImmutableList());
  }

  FileEntriesLayer toFileEntriesLayer() {
    return fileEntriesLayer;
  }
}
