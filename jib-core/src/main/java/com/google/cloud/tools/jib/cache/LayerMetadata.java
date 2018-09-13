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
import java.nio.file.attribute.FileTime;

/**
 * Metadata about an application layer stored in the cache. This is part of the {@link
 * CacheMetadata}.
 */
class LayerMetadata {

  /** Entry into the layer metadata. */
  static class LayerMetadataEntry {

    /** The source file path string, in Unix form. The path should be an absolute path. */
    private final String absoluteSourceFileString;

    /** The extraction path string, in Unix form. The path should be an absolute path. */
    private final String absoluteExtractionPathString;

    String getAbsoluteSourceFileString() {
      return absoluteSourceFileString;
    }

    String getAbsoluteExtractionPathString() {
      return absoluteExtractionPathString;
    }

    @VisibleForTesting
    LayerMetadataEntry(String absoluteSourceFileString, String absoluteExtractionPathString) {
      this.absoluteSourceFileString = absoluteSourceFileString;
      this.absoluteExtractionPathString = absoluteExtractionPathString;
    }
  }

  static LayerMetadata from(ImmutableList<LayerEntry> layerEntries, FileTime lastModifiedTime) {
    ImmutableList.Builder<LayerMetadataEntry> entries =
        ImmutableList.builderWithExpectedSize(layerEntries.size());

    for (LayerEntry layerEntry : layerEntries) {
      entries.add(
          new LayerMetadataEntry(
              layerEntry.getAbsoluteSourceFileString(),
              layerEntry.getAbsoluteExtractionPathString()));
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
  void setEntries(ImmutableList<LayerMetadataEntry> layerMetadataEntries) {
    entries = layerMetadataEntries;
  }
}
