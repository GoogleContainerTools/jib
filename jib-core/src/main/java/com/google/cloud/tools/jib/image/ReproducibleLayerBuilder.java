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

import com.google.cloud.tools.jib.tar.TarStreamBuilder;
import com.google.common.base.Verify;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;

/**
 * Builds a reproducible {@link UnwrittenLayer} from files. The reproducibility is implemented by
 * strips out all non-reproducible elements (modification time, group ID, user ID, user name, and
 * group name) from name-sorted tar archive entries.
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
     * modification time, which is wiped away in {@link #build}).
     */
    private static final File DIRECTORY_FILE = Paths.get(".").toFile();

    private final List<TarArchiveEntry> entries = new ArrayList<>();
    private final Set<String> names = new HashSet<>();

    /**
     * Adds a {@link TarArchiveEntry} if its extraction path does not exist yet. Also adds all of
     * the parent directories on the extraction path.
     *
     * @param tarArchiveEntry the {@link TarArchiveEntry}
     */
    private void add(TarArchiveEntry tarArchiveEntry) {
      if (names.contains(tarArchiveEntry.getName())) {
        return;
      }

      // Adds all directories along extraction paths to explicitly set permissions for those
      // directories.
      Path namePath = Paths.get(tarArchiveEntry.getName());
      if (namePath.getParent() != namePath.getRoot()) {
        add(new TarArchiveEntry(DIRECTORY_FILE, namePath.getParent().toString()));
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

  private final ImmutableList<LayerEntry> layerEntries;

  public ReproducibleLayerBuilder(ImmutableList<LayerEntry> layerEntries) {
    this.layerEntries = layerEntries;
  }

  /**
   * Builds and returns the layer.
   *
   * @return the new layer
   */
  public UnwrittenLayer build() {
    UniqueTarArchiveEntries uniqueTarArchiveEntries = new UniqueTarArchiveEntries();

    // Adds all the layer entries as tar entries.
    for (LayerEntry layerEntry : layerEntries) {
      // Adds the entries to uniqueTarArchiveEntries, which makes sure all entries are unique and
      // adds parent directories for each extraction path.
      uniqueTarArchiveEntries.add(
          new TarArchiveEntry(
              layerEntry.getSourceFile().toFile(), layerEntry.getAbsoluteExtractionPathString()));
    }

    // Gets the entries sorted by extraction path.
    List<TarArchiveEntry> sortedFilesystemEntries = uniqueTarArchiveEntries.getSortedEntries();

    Set<String> names = new HashSet<>();

    // Adds all the files to a tar stream.
    TarStreamBuilder tarStreamBuilder = new TarStreamBuilder();
    for (TarArchiveEntry entry : sortedFilesystemEntries) {
      // Strips out all non-reproducible elements from tar archive entries.
      entry.setModTime(0);
      entry.setGroupId(0);
      entry.setUserId(0);
      entry.setUserName("");
      entry.setGroupName("");

      Verify.verify(!names.contains(entry.getName()));
      names.add(entry.getName());

      tarStreamBuilder.addTarArchiveEntry(entry);
    }

    return new UnwrittenLayer(tarStreamBuilder.toBlob());
  }

  public ImmutableList<LayerEntry> getLayerEntries() {
    return layerEntries;
  }
}
