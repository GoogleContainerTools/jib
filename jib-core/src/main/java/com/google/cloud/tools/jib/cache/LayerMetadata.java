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

package com.google.cloud.tools.jib.cache;

import com.google.cloud.tools.jib.image.LayerEntry;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;

/**
 * Metadata about an application layer stored in the cache. This is part of the {@link
 * CacheMetadata}.
 */
class LayerMetadata {

  /** Entry into the layer metadata. */
  static class LayerMetadataEntry {

    private ImmutableList<String> sourceFilesStrings;
    private String extractionPath;

    ImmutableList<String> getSourceFilesStrings() {
      return sourceFilesStrings;
    }

    String getExtractionPath() {
      return extractionPath;
    }

    private LayerMetadataEntry(ImmutableList<String> sourceFilesStrings, String extractionPath) {
      this.sourceFilesStrings = sourceFilesStrings;
      this.extractionPath = extractionPath;
    }
  }

  static LayerMetadata from(ImmutableList<LayerEntry> layerEntries, FileTime lastModifiedTime) {
    ImmutableList.Builder<LayerMetadataEntry> entries =
        ImmutableList.builderWithExpectedSize(layerEntries.size());

    for (LayerEntry layerEntry : layerEntries) {
      List<Path> sourceFiles = layerEntry.getSourceFiles();
      ImmutableList.Builder<String> sourceFilesStrings =
          ImmutableList.builderWithExpectedSize(sourceFiles.size());
      for (Path sourceFile : sourceFiles) {
        sourceFilesStrings.add(sourceFile.toString());
      }
      entries.add(
          new LayerMetadataEntry(sourceFilesStrings.build(), layerEntry.getExtractionPath()));
    }

    return new LayerMetadata(entries.build(), lastModifiedTime);
  }

  /** The entries that define the layer contents. */
  private ImmutableList<LayerMetadataEntry> entries;

  /** The last time the layer was constructed. */
  private final FileTime lastModifiedTime;

  LayerMetadata(ImmutableList<LayerMetadataEntry> entries, FileTime lastModifiedTime) {
    this.entries = entries;
    this.lastModifiedTime = lastModifiedTime;
  }

  ImmutableList<LayerMetadataEntry> getEntries() {
    return entries;
  }

  FileTime getLastModifiedTime() {
    return lastModifiedTime;
  }

  @VisibleForTesting
  void setEntry(ImmutableList<String> sourceFilesStrings, String extractionPath) {
    entries = ImmutableList.of(new LayerMetadataEntry(sourceFilesStrings, extractionPath));
  }
}
