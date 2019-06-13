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

package com.google.cloud.tools.jib.cache;

import com.google.cloud.tools.jib.api.DescriptorDigest;
import com.google.cloud.tools.jib.api.LayerEntry;
import com.google.cloud.tools.jib.hash.Digests;
import com.google.cloud.tools.jib.json.JsonTemplate;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Generates a selector based on {@link LayerEntry}s for a layer. Selectors are secondary references
 * for a cache entries.
 *
 * <p>The selector is the SHA256 hash of the list of layer entries serialized in the following form:
 *
 * <pre>{@code
 * [
 *   {
 *     "sourceFile": "source/file/for/layer/entry/1",
 *     "extractionPath": "/extraction/path/for/layer/entry/1"
 *     "lastModifiedTime": "2018-10-03T15:48:32.416152Z"
 *     "permissions": "777"
 *   },
 *   {
 *     "sourceFile": "source/file/for/layer/entry/2",
 *     "extractionPath": "/extraction/path/for/layer/entry/2"
 *     "lastModifiedTime": "2018-10-03T15:48:32.416152Z"
 *     "permissions": "777"
 *   }
 * ]
 * }</pre>
 */
class LayerEntriesSelector {

  /** Serialized form of a {@link LayerEntry}. */
  @VisibleForTesting
  static class LayerEntryTemplate implements JsonTemplate, Comparable<LayerEntryTemplate> {

    private final String sourceFile;
    private final String extractionPath;
    private final Instant lastModifiedTime;
    private final String permissions;

    @VisibleForTesting
    LayerEntryTemplate(LayerEntry layerEntry) throws IOException {
      sourceFile = layerEntry.getSourceFile().toAbsolutePath().toString();
      extractionPath = layerEntry.getExtractionPath().toString();
      lastModifiedTime = Files.getLastModifiedTime(layerEntry.getSourceFile()).toInstant();
      permissions = layerEntry.getPermissions().toOctalString();
    }

    @Override
    public int compareTo(LayerEntryTemplate otherLayerEntryTemplate) {
      int sourceFileComparison = sourceFile.compareTo(otherLayerEntryTemplate.sourceFile);
      if (sourceFileComparison != 0) {
        return sourceFileComparison;
      }
      int extractionPathComparison =
          extractionPath.compareTo(otherLayerEntryTemplate.extractionPath);
      if (extractionPathComparison != 0) {
        return extractionPathComparison;
      }
      int lastModifiedTimeComparison =
          lastModifiedTime.compareTo(otherLayerEntryTemplate.lastModifiedTime);
      if (lastModifiedTimeComparison != 0) {
        return lastModifiedTimeComparison;
      }
      return permissions.compareTo(otherLayerEntryTemplate.permissions);
    }

    @Override
    public boolean equals(Object other) {
      if (this == other) {
        return true;
      }
      if (!(other instanceof LayerEntryTemplate)) {
        return false;
      }
      LayerEntryTemplate otherLayerEntryTemplate = (LayerEntryTemplate) other;
      return sourceFile.equals(otherLayerEntryTemplate.sourceFile)
          && extractionPath.equals(otherLayerEntryTemplate.extractionPath)
          && lastModifiedTime.equals(otherLayerEntryTemplate.lastModifiedTime)
          && permissions.equals(otherLayerEntryTemplate.permissions);
    }

    @Override
    public int hashCode() {
      return Objects.hash(sourceFile, extractionPath, lastModifiedTime, permissions);
    }
  }

  /**
   * Converts a list of {@link LayerEntry}s into a list of {@link LayerEntryTemplate}. The list is
   * sorted by source file first, then extraction path (see {@link LayerEntryTemplate#compareTo}).
   *
   * @param layerEntries the list of {@link LayerEntry} to convert
   * @return list of {@link LayerEntryTemplate} after sorting
   * @throws IOException if checking the file creation time of a layer entry fails
   */
  @VisibleForTesting
  static List<LayerEntryTemplate> toSortedJsonTemplates(List<LayerEntry> layerEntries)
      throws IOException {
    List<LayerEntryTemplate> jsonTemplates = new ArrayList<>();
    for (LayerEntry entry : layerEntries) {
      jsonTemplates.add(new LayerEntryTemplate(entry));
    }
    Collections.sort(jsonTemplates);
    return jsonTemplates;
  }

  /**
   * Generates a selector for the list of {@link LayerEntry}s. The selector is unique to each unique
   * set of layer entries, regardless of order. TODO: Should we care about order?
   *
   * @param layerEntries the layer entries
   * @return the selector
   * @throws IOException if an I/O exception occurs
   */
  static DescriptorDigest generateSelector(ImmutableList<LayerEntry> layerEntries)
      throws IOException {
    return Digests.computeJsonDigest(toSortedJsonTemplates(layerEntries));
  }

  private LayerEntriesSelector() {}
}
