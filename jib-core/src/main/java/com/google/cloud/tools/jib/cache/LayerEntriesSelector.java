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
import com.google.cloud.tools.jib.api.buildplan.FileEntry;
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
 * Generates a selector based on {@link FileEntry}s for a layer. Selectors are secondary references
 * for a cache entries.
 *
 * <p>The selector is the SHA256 hash of the list of layer entries serialized in the following form:
 *
 * <pre>{@code
 * [
 *   {
 *     "sourceFile": "source/file/for/layer/entry/1",
 *     "extractionPath": "/extraction/path/for/layer/entry/1"
 *     "sourceModificationTime": "2018-10-03T15:48:32.416152Z"
 *     "targetModificationTime": "1970-01-01T00:00:01Z",
 *     "permissions": "777",
 *     "ownership": "0:0"
 *   },
 *   {
 *     "sourceFile": "source/file/for/layer/entry/2",
 *     "extractionPath": "/extraction/path/for/layer/entry/2"
 *     "sourceModificationTime": "2018-10-03T15:48:32.416152Z"
 *     "targetModificationTime": "1970-01-01T00:00:01Z",
 *     "permissions": "777",
 *     "ownership": "alice:1234"
 *   }
 * ]
 * }</pre>
 */
class LayerEntriesSelector {

  /** Serialized form of a {@link FileEntry}. */
  @VisibleForTesting
  static class LayerEntryTemplate implements JsonTemplate, Comparable<LayerEntryTemplate> {

    private final String sourceFile;
    private final String extractionPath;
    private final Instant sourceModificationTime;
    private final Instant targetModificationTime;
    private final String permissions;
    private final String ownership;
    private final boolean isSymlink;

    @VisibleForTesting
    LayerEntryTemplate(FileEntry layerEntry, boolean retainSymlinks) throws IOException {
      sourceFile = layerEntry.getSourceFile().toAbsolutePath().toString();
      extractionPath = layerEntry.getExtractionPath().toString();
      sourceModificationTime = Files.getLastModifiedTime(layerEntry.getSourceFile()).toInstant();
      targetModificationTime = layerEntry.getModificationTime();
      permissions = layerEntry.getPermissions().toOctalString();
      ownership = layerEntry.getOwnership();
      isSymlink = retainSymlinks && Files.isSymbolicLink(layerEntry.getSourceFile());
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
      int sourceModificationTimeComparison =
          sourceModificationTime.compareTo(otherLayerEntryTemplate.sourceModificationTime);
      if (sourceModificationTimeComparison != 0) {
        return sourceModificationTimeComparison;
      }
      int targetModificationTimeComparison =
          targetModificationTime.compareTo(otherLayerEntryTemplate.targetModificationTime);
      if (targetModificationTimeComparison != 0) {
        return targetModificationTimeComparison;
      }
      int permissionsComparison = permissions.compareTo(otherLayerEntryTemplate.permissions);
      if (permissionsComparison != 0) {
        return permissionsComparison;
      }
      int ownerShipComparison = ownership.compareTo(otherLayerEntryTemplate.ownership);
      if (ownerShipComparison != 0) {
    	  return ownerShipComparison;
      }
      return Boolean.compare(isSymlink, otherLayerEntryTemplate.isSymlink);
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
          && sourceModificationTime.equals(otherLayerEntryTemplate.sourceModificationTime)
          && targetModificationTime.equals(otherLayerEntryTemplate.targetModificationTime)
          && permissions.equals(otherLayerEntryTemplate.permissions)
          && ownership.equals(otherLayerEntryTemplate.ownership)
          && isSymlink == otherLayerEntryTemplate.isSymlink;
    }

    @Override
    public int hashCode() {
      return Objects.hash(
          sourceFile,
          extractionPath,
          sourceModificationTime,
          targetModificationTime,
          permissions,
          ownership,
          isSymlink);
    }
  }

  /**
   * Converts a list of {@link FileEntry}s into a list of {@link LayerEntryTemplate}. The list is
   * sorted by source file first, then extraction path (see {@link LayerEntryTemplate#compareTo}).
   *
   * @param layerEntries the list of {@link FileEntry} to convert
   * @param retainSymlinks - Whether symbolic links are to be retained or not.
   * @return list of {@link LayerEntryTemplate} after sorting
   * @throws IOException if checking the file creation time of a layer entry fails
   */
  @VisibleForTesting
  static List<LayerEntryTemplate> toSortedJsonTemplates(List<FileEntry> layerEntries, boolean retainSymlinks)
      throws IOException {
    List<LayerEntryTemplate> jsonTemplates = new ArrayList<>();
    for (FileEntry entry : layerEntries) {
      jsonTemplates.add(new LayerEntryTemplate(entry, retainSymlinks));
    }
    Collections.sort(jsonTemplates);
    return jsonTemplates;
  }

  /**
   * Generates a selector for the list of {@link FileEntry}s. The selector is unique to each unique
   * set of layer entries, regardless of order. TODO: Should we care about order?
   *
   * @param layerEntries the layer entries
   * @param retainSymlinks - Whether symbolic links are to be retained or not.
   * @return the selector
   * @throws IOException if an I/O exception occurs
   */
  static DescriptorDigest generateSelector(ImmutableList<FileEntry> layerEntries, boolean retainSymlinks)
      throws IOException {
    return Digests.computeJsonDigest(toSortedJsonTemplates(layerEntries, retainSymlinks));
  }

  private LayerEntriesSelector() {}
}
