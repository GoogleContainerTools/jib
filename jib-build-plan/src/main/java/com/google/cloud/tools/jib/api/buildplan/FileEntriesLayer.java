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

package com.google.cloud.tools.jib.api.buildplan;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.concurrent.Immutable;

/** Configures how to build a layer in the container image. Instantiate with {@link #builder}. */
@Immutable
public class FileEntriesLayer implements LayerObject {

  /** Builds a {@link FileEntriesLayer}. */
  public static class Builder {

    private String name = "";
    private List<FileEntry> entries = new ArrayList<>();

    private Builder() {}

    /**
     * Sets a name for this layer. This name does not affect the contents of the layer.
     *
     * @param name the name
     * @return this
     */
    public Builder setName(String name) {
      this.name = name;
      return this;
    }

    /**
     * Sets entries for the layer.
     *
     * @param entries file entries in the layer
     * @return this
     */
    public Builder setEntries(List<FileEntry> entries) {
      this.entries = new ArrayList<>(entries);
      return this;
    }

    /**
     * Adds an entry to the layer.
     *
     * @param entry the layer entry to add
     * @return this
     */
    public Builder addEntry(FileEntry entry) {
      entries.add(entry);
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
      return addEntry(
          sourceFile,
          pathInContainer,
          DEFAULT_FILE_PERMISSIONS_PROVIDER.get(sourceFile, pathInContainer));
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
      return addEntry(sourceFile, pathInContainer, permissions, DEFAULT_MODIFICATION_TIME);
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
      return addEntry(
          sourceFile,
          pathInContainer,
          DEFAULT_FILE_PERMISSIONS_PROVIDER.get(sourceFile, pathInContainer),
          modificationTime);
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
      return addEntry(new FileEntry(sourceFile, pathInContainer, permissions, modificationTime));
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
     * @param ownership file ownership. For example, "1234", "user", ":5678", ":group", "1234:5678",
     *     and "user:group". Note that "" (empty string), ":" (single colon), "0:", ":0" are allowed
     *     and representative of "0:0" or "root:root", but prefer an empty string for "0:0".
     * @return this
     * @see Builder#addEntry(Path, AbsoluteUnixPath)
     * @see FilePermissions#DEFAULT_FILE_PERMISSIONS
     * @see FilePermissions#DEFAULT_FOLDER_PERMISSIONS
     */
    public Builder addEntry(
        Path sourceFile,
        AbsoluteUnixPath pathInContainer,
        FilePermissions permissions,
        Instant modificationTime,
        String ownership) {
      return addEntry(
          new FileEntry(sourceFile, pathInContainer, permissions, modificationTime, ownership));
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
      return addEntryRecursive(sourceFile, pathInContainer, DEFAULT_FILE_PERMISSIONS_PROVIDER);
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
      return addEntryRecursive(
          sourceFile, pathInContainer, filePermissionProvider, DEFAULT_MODIFICATION_TIME_PROVIDER);
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
      return addEntryRecursive(
          sourceFile,
          pathInContainer,
          filePermissionProvider,
          modificationTimeProvider,
          DEFAULT_OWNERSHIP_PROVIDER);
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
     * @param ownershipProvider a provider that takes a source path and destination path on the
     *     container and returns the ownership that should be set for that path
     * @return this
     * @throws IOException if an exception occurred when recursively listing the directory
     */
    public Builder addEntryRecursive(
        Path sourceFile,
        AbsoluteUnixPath pathInContainer,
        FilePermissionsProvider filePermissionProvider,
        ModificationTimeProvider modificationTimeProvider,
        OwnershipProvider ownershipProvider)
        throws IOException {
      FilePermissions permissions = filePermissionProvider.get(sourceFile, pathInContainer);
      Instant modificationTime = modificationTimeProvider.get(sourceFile, pathInContainer);
      String ownership = ownershipProvider.get(sourceFile, pathInContainer);
      addEntry(sourceFile, pathInContainer, permissions, modificationTime, ownership);
      if (!Files.isDirectory(sourceFile)) {
        return this;
      }
      try (Stream<Path> files = Files.list(sourceFile)) {
        for (Path file : files.collect(Collectors.toList())) {
          addEntryRecursive(
              file,
              pathInContainer.resolve(file.getFileName()),
              filePermissionProvider,
              modificationTimeProvider,
              ownershipProvider);
        }
      }
      return this;
    }

    /**
     * Returns the built {@link FileEntriesLayer}.
     *
     * @return the built {@link FileEntriesLayer}
     */
    public FileEntriesLayer build() {
      return new FileEntriesLayer(name, entries);
    }
  }

  /** Provider that returns default file permissions (644 for files, 755 for directories). */
  public static final FilePermissionsProvider DEFAULT_FILE_PERMISSIONS_PROVIDER =
      (sourcePath, destinationPath) ->
          Files.isDirectory(sourcePath)
              ? FilePermissions.DEFAULT_FOLDER_PERMISSIONS
              : FilePermissions.DEFAULT_FILE_PERMISSIONS;

  /** Default file modification time (EPOCH + 1 second). */
  public static final Instant DEFAULT_MODIFICATION_TIME = Instant.ofEpochSecond(1);

  /** Provider that returns default file modification time (EPOCH + 1 second). */
  public static final ModificationTimeProvider DEFAULT_MODIFICATION_TIME_PROVIDER =
      (sourcePath, destinationPath) -> DEFAULT_MODIFICATION_TIME;

  /**
   * Provider that returns default file ownership (an empty string "" effectively representing
   * "0:0").
   */
  public static final OwnershipProvider DEFAULT_OWNERSHIP_PROVIDER =
      (sourcePath, destinationPath) -> "";

  /**
   * Gets a new {@link Builder} for {@link FileEntriesLayer}.
   *
   * @return a new {@link Builder}
   */
  public static Builder builder() {
    return new Builder();
  }

  private final String name;
  private final List<FileEntry> entries;

  /**
   * Use {@link #builder} to instantiate.
   *
   * @param name an optional name for the layer
   * @param entries the list of {@link FileEntry}s
   */
  private FileEntriesLayer(String name, List<FileEntry> entries) {
    this.name = name;
    this.entries = entries;
  }

  @Override
  public Type getType() {
    return Type.FILE_ENTRIES;
  }

  /**
   * Gets the name.
   *
   * @return the name
   */
  @Override
  public String getName() {
    return name;
  }

  /**
   * Gets the list of entries.
   *
   * @return the list of entries
   */
  public List<FileEntry> getEntries() {
    return new ArrayList<>(entries);
  }

  /**
   * Creates a builder configured with the current values.
   *
   * @return {@link Builder} configured with the current values
   */
  public Builder toBuilder() {
    return builder().setName(name).setEntries(entries);
  }
}
