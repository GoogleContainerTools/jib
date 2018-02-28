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

import com.google.common.annotations.VisibleForTesting;
import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Manages both the base image layers cache and the application image layers cache.
 *
 * <p>In general, the cache for base image layers should be shared between projects, while the cache
 * for the application image layers should be specific to a single project.
 */
public class Caches implements Closeable {

  /** Initializes a {@link Caches} with directory paths. */
  public static class Initializer {

    /** The default directory for caching the base image layers, in {@code $HOME/.jib-cache/}. */
    private static final Path DEFAULT_BASE_CACHE_DIRECTORY =
        Paths.get(System.getProperty("user.home")).resolve(".jib-cache");

    /** A file to store in the default base image layers cache to check ownership by Jib. */
    private static final String OWNERSHIP_FILE_NAME = ".jib";

    @VisibleForTesting
    /**
     * Ensures ownership of {@code cacheDirectory} by checking for the existence of {@link
     * #OWNERSHIP_FILE_NAME}.
     *
     * <p>This is a safety check to make sure we are not writing to a directory not created by Jib.
     */
    static void ensureOwnership(Path cacheDirectory)
        throws CacheDirectoryNotOwnedException, IOException {
      Path ownershipFile = cacheDirectory.resolve(OWNERSHIP_FILE_NAME);

      if (Files.exists(cacheDirectory)) {
        // Checks for the ownership file.
        if (!Files.exists(ownershipFile)) {
          throw new CacheDirectoryNotOwnedException(cacheDirectory);
        }

      } else {
        // Creates the cache directory and ownership file.
        Files.createDirectory(cacheDirectory);
        Files.createFile(ownershipFile);
      }
    }

    private Path baseCacheDirectory = DEFAULT_BASE_CACHE_DIRECTORY;
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

    public Caches init()
        throws CacheMetadataCorruptedException, IOException, CacheDirectoryNotOwnedException {
      if (applicationCacheDirectory == null) {
        throw new IllegalStateException(
            "Must initialize cache with an application image layer cache directory");
      }

      if (DEFAULT_BASE_CACHE_DIRECTORY.equals(baseCacheDirectory)) {
        ensureOwnership(DEFAULT_BASE_CACHE_DIRECTORY);
      }

      return new Caches(baseCacheDirectory, applicationCacheDirectory);
    }
  }

  public static Initializer initializer() {
    return new Initializer();
  }

  private final Cache baseCache;
  private final Cache applicationCache;

  private Caches(Path baseCacheDirectory, Path applicationCacheDirectory)
      throws CacheMetadataCorruptedException, IOException {
    applicationCache = Cache.init(applicationCacheDirectory);

    // Ensures that only one Cache is initialized if using the same directory.
    if (Files.isSameFile(baseCacheDirectory, applicationCacheDirectory)) {
      baseCache = applicationCache;
    } else {
      baseCache = Cache.init(baseCacheDirectory);
    }
  }

  public Cache getBaseCache() {
    return baseCache;
  }

  public Cache getApplicationCache() {
    return applicationCache;
  }

  @Override
  public void close() throws IOException {
    applicationCache.close();

    if (baseCache != applicationCache) {
      baseCache.close();
    }
  }
}
