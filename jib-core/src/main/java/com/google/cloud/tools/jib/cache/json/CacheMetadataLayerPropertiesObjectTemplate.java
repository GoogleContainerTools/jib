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

package com.google.cloud.tools.jib.cache.json;

import com.google.cloud.tools.jib.json.JsonTemplate;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Inner JSON template for extra properties for an application layer, as part of {@link
 * CacheMetadataLayerObjectTemplate}.
 */
public class CacheMetadataLayerPropertiesObjectTemplate implements JsonTemplate {

  /** The paths to the source files that the layer was constructed from. */
  private List<String> sourceFiles = new ArrayList<>();

  /** The last time the layer was constructed. */
  private long lastModifiedTime;

  public List<String> getSourceFiles() {
    return sourceFiles;
  }

  public FileTime getLastModifiedTime() {
    return FileTime.fromMillis(lastModifiedTime);
  }

  public CacheMetadataLayerPropertiesObjectTemplate setSourceFiles(List<String> sourceFiles) {
    this.sourceFiles = sourceFiles;
    return this;
  }

  public CacheMetadataLayerPropertiesObjectTemplate setLastModifiedTime(FileTime lastModifiedTime) {
    this.lastModifiedTime = lastModifiedTime.toMillis();
    return this;
  }
}
