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

import com.google.common.collect.ImmutableList;
import java.nio.file.Path;

/**
 * Represents an entry in the layer. A layer consists of many entries that can be converted into tar
 * archive entries.
 */
public class LayerEntry {

  /**
   * The source files to build from. Source files that are directories will have all subfiles in the
   * directory added (but not the directory itself).
   *
   * <p>The source files are specified as a list instead of a set to define the order in which they
   * are added.
   */
  private final ImmutableList<Path> sourceFiles;

  /** The Unix-style path to add the source files to in the container image filesystem. */
  private final String extractionPath;

  public LayerEntry(ImmutableList<Path> sourceFiles, String extractionPath) {
    this.sourceFiles = sourceFiles;
    this.extractionPath = extractionPath;
  }

  public ImmutableList<Path> getSourceFiles() {
    return sourceFiles;
  }

  public String getExtractionPath() {
    return extractionPath;
  }
}
