/*
 * Copyright 2017 Google LLC.
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

import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer;
import com.google.cloud.tools.jib.api.buildplan.FileEntry;
import com.google.cloud.tools.jib.blob.Blob;
import com.google.cloud.tools.jib.blob.Blobs;
import com.google.cloud.tools.jib.tar.TarStreamBuilder;
import com.google.common.base.Verify;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;

/**
 * Builds a reproducible layer {@link Blob} from files. The reproducibility is implemented by strips
 * out all non-reproducible elements (modification time, group ID, user ID, user name, and group
 * name) from name-sorted tar archive entries.
 */
public class ReproducibleLayerBuilder {

  /**
   * Holds a list of {@link TarArchiveEntry}s with unique extraction paths. The list also includes
   * all parent directories for each extraction path.
   */
  private static class UniqueTarArchiveEntries {

    /**
     * Uses the current directory to act as the file input to TarArchiveEntry (since all directories
     * are treated the same in {@link TarArchiveEntry#TarArchiveEntry(File, String)}, except for
     * modification time, UID, GID, etc., which are wiped away in {@link #build}).
     */
    private static final Path DIRECTORY_FILE = Paths.get(".");

    private final List<TarArchiveEntry> entries = new ArrayList<>();
    private final Set<String> names = new HashSet<>();

    /**
     * Adds a {@link TarArchiveEntry} if its extraction path does not exist yet. Also adds all of
     * the parent directories on the extraction path, if the parent does not exist. Parent will have
     * modification time set to {@link FileEntriesLayer#DEFAULT_MODIFICATION_TIME}.
     *
     * @param tarArchiveEntry the {@link TarArchiveEntry}
     * @throws IOException if an I/O error occurs
     */
    private void add(TarArchiveEntry tarArchiveEntry) throws IOException {
      if (names.contains(tarArchiveEntry.getName())) {
        return;
      }

      // Adds all directories along extraction paths to explicitly set permissions for those
      // directories.
      Path namePath = Paths.get(tarArchiveEntry.getName());
      if (namePath.getParent() != namePath.getRoot()) {
        Path tarArchiveParentDir = Verify.verifyNotNull(namePath.getParent());
        TarArchiveEntry dir = new TarArchiveEntry(DIRECTORY_FILE, tarArchiveParentDir.toString());
        dir.setModTime(FileEntriesLayer.DEFAULT_MODIFICATION_TIME.toEpochMilli());
        dir.setUserId(0);
        dir.setGroupId(0);
        dir.setUserName("");
        dir.setGroupName("");
        clearPaxTimeHeaders(dir);
        add(dir);
      }

      entries.add(tarArchiveEntry);
      names.add(tarArchiveEntry.getName());
    }

    private List<TarArchiveEntry> getSortedEntries() {
      List<TarArchiveEntry> sortedEntries = new ArrayList<>(entries);
      sortedEntries.sort(Comparator.comparing(TarArchiveEntry::getName));
      return sortedEntries;
    }
  }

  private static void clearPaxTimeHeaders(TarArchiveEntry entry) {
    entry.addPaxHeader("mtime", "1"); // EPOCH plus 1 second
    entry.addPaxHeader("atime", "1");
    entry.addPaxHeader("ctime", "1");
    entry.addPaxHeader("LIBARCHIVE.creationtime", "1");
  }

  private static void setUserAndGroup(TarArchiveEntry entry, FileEntry layerEntry) {
    entry.setUserId(0);
    entry.setGroupId(0);
    entry.setUserName("");
    entry.setGroupName("");

    if (!layerEntry.getOwnership().isEmpty()) {
      // Parse "<user>:<group>" string.
      String user = layerEntry.getOwnership();
      String group = "";
      int colonIndex = user.indexOf(':');
      if (colonIndex != -1) {
        group = user.substring(colonIndex + 1);
        user = user.substring(0, colonIndex);
      }

      if (!user.isEmpty()) {
        // Check if it's a number, and set either UID or user name.
        try {
          entry.setUserId(Long.parseLong(user));
        } catch (NumberFormatException ignored) {
          entry.setUserName(user);
        }
      }
      if (!group.isEmpty()) {
        // Check if it's a number, and set either GID or group name.
        try {
          entry.setGroupId(Long.parseLong(group));
        } catch (NumberFormatException ignored) {
          entry.setGroupName(group);
        }
      }
    }
  }

  private final ImmutableList<FileEntry> layerEntries;

  public ReproducibleLayerBuilder(ImmutableList<FileEntry> layerEntries) {
    this.layerEntries = layerEntries;
  }

  /**
   * Builds and returns the layer {@link Blob}.
   *
   * @return the new layer
   * @throws IOException if an I/O error occurs
   */
  public Blob build() throws IOException {
    UniqueTarArchiveEntries uniqueTarArchiveEntries = new UniqueTarArchiveEntries();

    // Adds all the layer entries as tar entries.
    for (FileEntry layerEntry : layerEntries) {
      // Adds the entries to uniqueTarArchiveEntries, which makes sure all entries are unique and
      // adds parent directories for each extraction path.
      TarArchiveEntry entry =
          new TarArchiveEntry(
              layerEntry.getSourceFile(), layerEntry.getExtractionPath().toString());

      // Sets the entry's permissions by masking out the permission bits from the entry's mode (the
      // lowest 9 bits) then using a bitwise OR to set them to the layerEntry's permissions.
      entry.setMode((entry.getMode() & ~0777) | layerEntry.getPermissions().getPermissionBits());
      entry.setModTime(layerEntry.getModificationTime().toEpochMilli());
      setUserAndGroup(entry, layerEntry);
      clearPaxTimeHeaders(entry);

      uniqueTarArchiveEntries.add(entry);
    }

    // Gets the entries sorted by extraction path.
    List<TarArchiveEntry> sortedFilesystemEntries = uniqueTarArchiveEntries.getSortedEntries();

    Set<String> names = new HashSet<>();

    // Adds all the files to a tar stream.
    TarStreamBuilder tarStreamBuilder = new TarStreamBuilder();
    for (TarArchiveEntry entry : sortedFilesystemEntries) {
      Verify.verify(!names.contains(entry.getName()));
      names.add(entry.getName());

      tarStreamBuilder.addTarArchiveEntry(entry);
    }

    return Blobs.from(tarStreamBuilder::writeAsTarArchiveTo, false);
  }
}
