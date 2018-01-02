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

import java.nio.file.NotDirectoryException;
import java.nio.file.Path;

/**
 * Manages both the base image layers cache and the application image layers cache.
 *
 * <p>In general, the cache for base image layers should be shared between projects, while the cache
 * for the application image layers should be specific to a single project.
 */
public class Caches {

  private final Cache baseCache;
  private final Cache applicationCache;

  /** Initializes a {@link Caches} with directory paths. */
  public static class Initializer {

    private Path baseCacheDirectory;
    private Path applicationCacheDirectory;

    private Initializer() {}

    public Initializer setBaseCacheDirectory(Path baseCacheDirectory) {
      this.baseCacheDirectory = baseCacheDirectory;
      return this;
    }

    public Initializer setApplicationCacheDirectory(Path applicationCacheDirectory) {
      this.applicationCacheDirectory = applicationCacheDirectory;
      return this;
    }

    public Caches init() throws CacheMetadataCorruptedException, NotDirectoryException {
      if (baseCacheDirectory == null || applicationCacheDirectory == null) {
        throw new IllegalStateException(
            "Must initialize cache with both base image layer cache directory and application image layer cache directory");
      }
      return new Caches(baseCacheDirectory, applicationCacheDirectory);
    }
  }

  public static Initializer initializer() {
    return new Initializer();
  }

  private Caches(Path baseCacheDirectory, Path applicationCacheDirectory)
      throws CacheMetadataCorruptedException, NotDirectoryException {
    baseCache = Cache.init(baseCacheDirectory);
    applicationCache = Cache.init(applicationCacheDirectory);
  }

  Cache getBaseCache() {
    return baseCache;
  }

  Cache getApplicationCache() {
    return applicationCache;
  }
}
