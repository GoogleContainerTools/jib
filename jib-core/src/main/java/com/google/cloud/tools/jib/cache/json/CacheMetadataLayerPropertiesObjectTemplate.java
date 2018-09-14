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

package com.google.cloud.tools.jib.cache.json;

import com.google.cloud.tools.jib.json.JsonTemplate;
import java.nio.file.attribute.FileTime;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Inner JSON template for extra properties for an application layer, as part of {@link
 * CacheMetadataLayerObjectTemplate}.
 */
public class CacheMetadataLayerPropertiesObjectTemplate implements JsonTemplate {

  /** Represents a pair of source files and extraction path. */
  public static class LayerEntryTemplate implements JsonTemplate {

    /** The path to the source file for this layer entry. */
    @Nullable private String sourceFile;

    /** The intended path to extract the source file to in the container. */
    @Nullable private String extractionPath;

    @Nullable
    public String getSourceFileString() {
      return sourceFile;
    }

    @Nullable
    public String getExtractionPathString() {
      return extractionPath;
    }

    public LayerEntryTemplate(String absoluteSourceFile, String absoluteExtractionPath) {
      sourceFile = absoluteSourceFile;
      extractionPath = absoluteExtractionPath;
    }

    /** For Jackson JSON templating. */
    public LayerEntryTemplate() {}
  }

  /** The content entries for the layer. */
  private List<LayerEntryTemplate> layerEntries = Collections.emptyList();

  /** The last time the layer was constructed. */
  private long lastModifiedTime;

  public List<LayerEntryTemplate> getLayerEntries() {
    return layerEntries;
  }

  public FileTime getLastModifiedTime() {
    return FileTime.fromMillis(lastModifiedTime);
  }

  public CacheMetadataLayerPropertiesObjectTemplate setLayerEntries(
      List<LayerEntryTemplate> layerEntries) {
    this.layerEntries = layerEntries;
    return this;
  }

  public CacheMetadataLayerPropertiesObjectTemplate setLastModifiedTime(FileTime lastModifiedTime) {
    this.lastModifiedTime = lastModifiedTime.toMillis();
    return this;
  }
}
