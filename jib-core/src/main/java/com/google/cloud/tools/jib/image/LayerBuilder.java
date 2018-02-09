/*
 * Copyright 2017 Google Inc.
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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;

/** Builds an {@link UnwrittenLayer} from files. */
public class LayerBuilder {

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

  public LayerBuilder(List<Path> sourceFiles, String extractionPath) {
    this.sourceFiles = new ArrayList<>(sourceFiles);
    this.extractionPath = extractionPath;
  }

  /** Builds and returns the layer. */
  public UnwrittenLayer build() throws IOException {
    List<TarArchiveEntry> filesystemEntries = new ArrayList<>();

    for (Path sourceFile : sourceFiles) {
      if (Files.isDirectory(sourceFile)) {
        Files.walk(sourceFile)
            .filter(path -> !path.equals(sourceFile))
            .forEach(
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
        continue;
      }

      TarArchiveEntry tarArchiveEntry =
          new TarArchiveEntry(sourceFile.toFile(), extractionPath + "/" + sourceFile.getFileName());
      tarArchiveEntry.setModTime(0);
      filesystemEntries.add(tarArchiveEntry);
    }

    TarStreamBuilder tarStreamBuilder = new TarStreamBuilder();

    // Adds all the files to a tar stream.
    for (TarArchiveEntry entry : filesystemEntries) {
      tarStreamBuilder.addEntry(entry);
    }

    return new UnwrittenLayer(tarStreamBuilder.toBlob());
  }

  public List<Path> getSourceFiles() {
    return Collections.unmodifiableList(sourceFiles);
  }
}
