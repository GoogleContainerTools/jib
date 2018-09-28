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

import com.google.cloud.tools.jib.image.DescriptorDigest;
import com.google.cloud.tools.jib.image.LayerEntry;
import com.google.cloud.tools.jib.json.JsonTemplate;
import com.google.cloud.tools.jib.json.JsonTemplateMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteStreams;
import java.io.IOException;
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
 *   },
 *   {
 *     "sourceFile": "source/file/for/layer/entry/2",
 *     "extractionPath": "/extraction/path/for/layer/entry/2"
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

    @VisibleForTesting
    LayerEntryTemplate(LayerEntry layerEntry) {
      sourceFile = layerEntry.getAbsoluteSourceFileString();
      extractionPath = layerEntry.getAbsoluteExtractionPathString();
    }

    @Override
    public int compareTo(LayerEntryTemplate otherLayerEntryTemplate) {
      int sourceFileComparison = sourceFile.compareTo(otherLayerEntryTemplate.sourceFile);
      if (sourceFileComparison != 0) {
        return sourceFileComparison;
      }
      return extractionPath.compareTo(otherLayerEntryTemplate.extractionPath);
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
          && extractionPath.equals(otherLayerEntryTemplate.extractionPath);
    }

    @Override
    public int hashCode() {
      return Objects.hash(sourceFile, extractionPath);
    }
  }

  /**
   * Converts a list of {@link LayerEntry}s into a list of {@link LayerEntriesTemplate}. The list is
   * sorted by source file first, then extraction path (see {@link LayerEntryTemplate#compareTo}).
   *
   * @param layerEntries
   * @return list of {@link LayerEntryTemplate} after sorting
   */
  @VisibleForTesting
  static List<LayerEntryTemplate> toSortedJsonTemplates(List<LayerEntry> layerEntries) {
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
    return JsonTemplateMapper.toBlob(toSortedJsonTemplates(layerEntries))
        .writeTo(ByteStreams.nullOutputStream())
        .getDigest();
  }

  private LayerEntriesSelector() {}
}
