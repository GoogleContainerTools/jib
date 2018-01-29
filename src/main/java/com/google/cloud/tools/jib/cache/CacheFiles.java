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

import com.google.cloud.tools.jib.image.DescriptorDigest;
import java.nio.file.Path;

/** Methods for getting static cache filename properties. */
class CacheFiles {

  static final String METADATA_FILENAME = "metadata.json";
  private static final String LAYER_FILE_EXTENSION = ".tar.gz";

  static Path getMetadataFile(Path cacheDirectory) {
    return cacheDirectory.resolve(METADATA_FILENAME);
  }

  /**
   * Gets the path to the file for a layer digest. The file is {@code [cache directory]/[layer
   * hash].tar.gz}.
   */
  static Path getLayerFile(Path cacheDirectory, DescriptorDigest layerDigest) {
    return cacheDirectory.resolve(layerDigest.getHash() + LAYER_FILE_EXTENSION);
  }

  private CacheFiles() {}
}
