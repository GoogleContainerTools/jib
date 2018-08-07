/*
 * Copyright 2017 Google LLC. All rights reserved.
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

import com.google.cloud.tools.jib.filesystem.DirectoryWalker;
import com.google.cloud.tools.jib.tar.TarStreamBuilder;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;

/**
 * Builds a reproducible {@link UnwrittenLayer} from files. The reproducibility is implemented by
 * strips out all non-reproducible elements (modification time, group ID, user ID, user name, and
 * group name) from name-sorted tar archive entries.
 */
public class ReproducibleLayerBuilder {

  /**
   * Builds the {@link TarArchiveEntry}s for adding this {@link LayerEntry} to a tarball archive.
   *
   * @return the list of {@link TarArchiveEntry}
   * @throws IOException if walking a source file that is a directory failed
   */
  private static List<TarArchiveEntry> buildAsTarArchiveEntries(LayerEntry layerEntry)
      throws IOException {
    List<TarArchiveEntry> tarArchiveEntries = new ArrayList<>();

    // Adds the files to extract relative to the extraction path.
    for (Path sourceFile : layerEntry.getSourceFiles()) {
      if (Files.isDirectory(sourceFile)) {
        new DirectoryWalker(sourceFile)
            .filterRoot()
            .walk(
                path -> {
                  /*
                   * Builds the same file path as in the source file for extraction. The iteration
                   * is necessary because the path needs to be in Unix-style.
                   */
                  StringBuilder subExtractionPath =
                      new StringBuilder(layerEntry.getExtractionPath());
                  Path sourceFileRelativePath = sourceFile.getParent().relativize(path);
                  for (Path sourceFileRelativePathComponent : sourceFileRelativePath) {
                    subExtractionPath.append('/').append(sourceFileRelativePathComponent);
                  }
                  tarArchiveEntries.add(
                      new TarArchiveEntry(path.toFile(), subExtractionPath.toString()));
                });

      } else {
        TarArchiveEntry tarArchiveEntry =
            new TarArchiveEntry(
                sourceFile.toFile(),
                layerEntry.getExtractionPath() + "/" + sourceFile.getFileName());
        tarArchiveEntries.add(tarArchiveEntry);
      }
    }

    return tarArchiveEntries;
  }

  /**
   * Gets the parent directories for {@code directory}, excluding root {@code /}. The {@code file}
   * itself will be included only if it is a directory.
   *
   * @param file the file to get parents for
   * @return the list of parent directories
   */
  private static List<Path> getParentDirectories(Path file) {
    List<Path> parentDirectories = new ArrayList<>();
    Path currentPath = Paths.get("/");
    Path fullPath = Files.isDirectory(file) ? file : file.getParent();
    for (Path element : fullPath) {
      currentPath = currentPath.resolve(element);
      parentDirectories.add(currentPath);
    }
    return parentDirectories;
  }

  private final ImmutableList.Builder<LayerEntry> layerEntries = ImmutableList.builder();

  public ReproducibleLayerBuilder() {}

  /**
   * Adds the {@code sourceFiles} to be extracted on the image at {@code extractionPath}. The order
   * in which files are added matters.
   *
   * @param sourceFiles the source files to build from
   * @param extractionPath the Unix-style path to add the source files to in the container image
   *     filesystem
   * @return this
   */
  public ReproducibleLayerBuilder addFiles(List<Path> sourceFiles, String extractionPath) {
    this.layerEntries.add(new LayerEntry(ImmutableList.copyOf(sourceFiles), extractionPath));
    return this;
  }

  /**
   * Builds and returns the layer.
   *
   * @return the new layer
   * @throws IOException if walking the source files fails
   */
  public UnwrittenLayer build() throws IOException {
    List<TarArchiveEntry> filesystemEntries = new ArrayList<>();
    List<LayerEntry> layerEntries = this.layerEntries.build();

    // Keeps track of all the added directories so that the same directory is not added twice.
    LinkedHashSet<String> addedDirectories = new LinkedHashSet<>();
    // We are using the current directory to act as the file input (since all directories are
    // treated the same in TarArchiveEntry).
    File directoryFile = Paths.get(".").toFile();

    // Adds all the layer entries as tar entries.
    for (LayerEntry layerEntry : layerEntries) {
      // Converts layerEntry to list of TarArchiveEntrys.
      List<TarArchiveEntry> tarArchiveEntries = buildAsTarArchiveEntries(layerEntry);

      // Adds all directories along extraction paths to explicitly set permissions for those
      // directories.
      for (TarArchiveEntry tarArchiveEntry : tarArchiveEntries) {
        for (Path parentDirectory : getParentDirectories(Paths.get(tarArchiveEntry.getName()))) {
          if (addedDirectories.contains(parentDirectory.toString())) {
            continue;
          }
          filesystemEntries.add(new TarArchiveEntry(directoryFile, parentDirectory.toString()));
          addedDirectories.add(parentDirectory.toString());
        }
      }

      // Adds the actual files.
      filesystemEntries.addAll(tarArchiveEntries);
    }

    // Sorts the entries by name.
    filesystemEntries.sort(Comparator.comparing(TarArchiveEntry::getName));

    // Adds all the files to a tar stream.
    TarStreamBuilder tarStreamBuilder = new TarStreamBuilder();
    for (TarArchiveEntry entry : filesystemEntries) {
      // Strips out all non-reproducible elements from tar archive entries.
      entry.setModTime(0);
      entry.setGroupId(0);
      entry.setUserId(0);
      entry.setUserName("");
      entry.setGroupName("");

      tarStreamBuilder.addTarArchiveEntry(entry);
    }

    return new UnwrittenLayer(tarStreamBuilder.toBlob());
  }

  public ImmutableList<LayerEntry> getLayerEntries() {
    return layerEntries.build();
  }
}
