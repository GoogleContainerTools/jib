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

package com.google.cloud.tools.jib.cache;

import com.google.common.annotations.VisibleForTesting;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Metadata about an application layer stored in the cache. This is part of the {@link
 * CacheMetadata}.
 */
class LayerMetadata {

  static LayerMetadata from(List<Path> sourceFiles, FileTime lastModifiedTime) {
    List<String> sourceFilesStrings = new ArrayList<>(sourceFiles.size());
    for (Path sourceFile : sourceFiles) {
      sourceFilesStrings.add(sourceFile.toString());
    }
    return new LayerMetadata(sourceFilesStrings, lastModifiedTime);
  }

  /** The paths to the source files that the layer was constructed from. */
  private List<String> sourceFiles;

  /** The last time the layer was constructed. */
  private final FileTime lastModifiedTime;

  LayerMetadata(List<String> sourceFiles, FileTime lastModifiedTime) {
    this.sourceFiles = sourceFiles;
    this.lastModifiedTime = lastModifiedTime;
  }

  List<String> getSourceFiles() {
    return sourceFiles;
  }

  public FileTime getLastModifiedTime() {
    return lastModifiedTime;
  }

  @VisibleForTesting
  void setSourceFiles(List<String> sourceFiles) {
    this.sourceFiles = sourceFiles;
  }
}
