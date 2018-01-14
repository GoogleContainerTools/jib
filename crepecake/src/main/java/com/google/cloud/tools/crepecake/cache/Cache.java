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

package com.google.cloud.tools.crepecake.cache;

import com.google.cloud.tools.crepecake.cache.json.CacheMetadataTemplate;
import com.google.cloud.tools.crepecake.json.JsonTemplateMapper;
import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;

/** Manages a cache. */
class Cache {

  /** The path to the root of the cache. */
  private final Path cacheDirectory;

  /** The metadata that corresponds to the cache at {@link #cacheDirectory}. */
  private final CacheMetadata cacheMetadata;

  /**
   * Initializes a cache with a directory. This also loads the cache metadata if it exists in the
   * directory.
   */
  static Cache init(Path cacheDirectory)
      throws NotDirectoryException, CacheMetadataCorruptedException {
    if (!Files.isDirectory(cacheDirectory)) {
      throw new NotDirectoryException("The cache can only write to a directory");
    }
    CacheMetadata cacheMetadata = loadCacheMetadata(cacheDirectory);

    return new Cache(cacheDirectory, cacheMetadata);
  }

  private static CacheMetadata loadCacheMetadata(Path cacheDirectory)
      throws CacheMetadataCorruptedException {
    Path cacheMetadataJsonFile = cacheDirectory.resolve(CacheFiles.METADATA_FILENAME);

    if (!Files.exists(cacheMetadataJsonFile)) {
      return new CacheMetadata();
    }

    try {
      CacheMetadataTemplate cacheMetadataJson =
          JsonTemplateMapper.readJsonFromFile(cacheMetadataJsonFile, CacheMetadataTemplate.class);
      return CacheMetadataTranslator.fromTemplate(cacheMetadataJson, cacheDirectory);

    } catch (IOException ex) {
      // The cache metadata is probably corrupted.
      throw new CacheMetadataCorruptedException(ex);
    }
  }

  private Cache(Path cacheDirectory, CacheMetadata cacheMetadata) {
    this.cacheDirectory = cacheDirectory;
    this.cacheMetadata = cacheMetadata;
  }

  @VisibleForTesting
  Path getCacheDirectory() {
    return cacheDirectory;
  }

  @VisibleForTesting
  CacheMetadata getMetadata() {
    return cacheMetadata;
  }
}
