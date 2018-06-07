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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;

/**
 * Builds a reproducible {@link UnwrittenLayer} from files. The reproducibility is implemented by
 * strips out all non-reproducible elements (modification time, group ID, user ID, user name, and
 * group name) from name-sorted tar archive entries.
 */
public class ReproducibleLayerBuilder {

  /**
   * The source files to build the layer from. Source files that are directories will have all
   * subfiles in the directory added (but not the directory itself).
   *
   * <p>The source files are specified as a list instead of a set to define the order in which they
   * are added.
   */
  private final List<Path> sourceFiles;

  /** The Unix-style path of the file in the partial filesystem changeset. */
  private final String extractionPath;

  public ReproducibleLayerBuilder(List<Path> sourceFiles, String extractionPath) {
    this.sourceFiles = new ArrayList<>(sourceFiles);
    this.extractionPath = extractionPath;
  }

  /**
   * Builds and returns the layer.
   *
   * @return the new layer
   * @throws IOException if walking the source files fails
   */
  public UnwrittenLayer build() throws IOException {
    List<TarArchiveEntry> filesystemEntries = new ArrayList<>();

    for (Path sourceFile : sourceFiles) {
      if (Files.isDirectory(sourceFile)) {
        new DirectoryWalker(sourceFile)
            .filterRoot()
            .walk(
                path -> {
                  /*
                   * Builds the same file path as in the source file for extraction. The iteration
                   * is necessary because the path needs to be in Unix-style.
                   */
                  StringBuilder subExtractionPath = new StringBuilder(extractionPath);
                  Path sourceFileRelativePath = sourceFile.getParent().relativize(path);
                  for (Path sourceFileRelativePathComponent : sourceFileRelativePath) {
                    subExtractionPath.append('/').append(sourceFileRelativePathComponent);
                  }
                  filesystemEntries.add(
                      new TarArchiveEntry(path.toFile(), subExtractionPath.toString()));
                });

      } else {
        TarArchiveEntry tarArchiveEntry =
            new TarArchiveEntry(
                sourceFile.toFile(), extractionPath + "/" + sourceFile.getFileName());
        filesystemEntries.add(tarArchiveEntry);
      }
    }

    // Adds all the files to a tar stream.
    TarStreamBuilder tarStreamBuilder = new TarStreamBuilder();
    filesystemEntries.sort(Comparator.comparing(TarArchiveEntry::getName));
    for (TarArchiveEntry entry : filesystemEntries) {
      // Strips out all non-reproducible elements from tar archive entries.
      entry.setModTime(0);
      entry.setGroupId(0);
      entry.setUserId(0);
      entry.setUserName("");
      entry.setGroupName("");

      tarStreamBuilder.addEntry(entry);
    }

    return new UnwrittenLayer(tarStreamBuilder.toBlob());
  }

  public List<Path> getSourceFiles() {
    return Collections.unmodifiableList(sourceFiles);
  }
}
