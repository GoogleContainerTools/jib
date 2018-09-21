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

import com.google.common.annotations.VisibleForTesting;
import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Manages both the base image layers cache and the application image layers cache.
 *
 * <p>In general, the cache for base image layers should be shared between projects, while the cache
 * for the application image layers should be specific to a single project.
 */
public class Caches implements Closeable {

  /** Initializes a {@link Caches} with directory paths. */
  public static class Initializer {

    /** A file to store in the default base image layers cache to check ownership by Jib. */
    private static final String OWNERSHIP_FILE_NAME = ".jib";

    /**
     * Ensures ownership of {@code cacheDirectory} by checking for the existence of {@link
     * #OWNERSHIP_FILE_NAME}.
     *
     * <p>This is a safety check to make sure we are not writing to a directory not created by Jib.
     */
    @VisibleForTesting
    static void ensureOwnership(Path cacheDirectory)
        throws CacheDirectoryNotOwnedException, CacheDirectoryCreationException {
      Path ownershipFile = cacheDirectory.resolve(OWNERSHIP_FILE_NAME);

      if (Files.exists(cacheDirectory)) {
        // Checks for the ownership file.
        if (!Files.exists(ownershipFile)) {
          throw new CacheDirectoryNotOwnedException(cacheDirectory);
        }

      } else {
        try {
          // Creates the cache directory and ownership file.
          Files.createDirectories(cacheDirectory);
          Files.createFile(ownershipFile);

        } catch (IOException ex) {
          throw new CacheDirectoryCreationException(cacheDirectory, ex);
        }
      }
    }

    private final Path baseImageLayersCacheDirectory;
    private final boolean shouldEnsureOwnershipOfBaseImageLayersCacheDirectory;
    private final Path applicationLayersCacheDirectory;
    private final boolean shouldEnsureOwnershipOfApplicationLayersCacheDirectory;

    /**
     * @param baseImageLayersCacheDirectory cache for the application image layers - usually not
     *     local to the application project
     * @param shouldEnsureOwnershipOfBaseImageLayersCacheDirectory if {@code true}, ensures the base
     *     image layers cache directory is safe to write to
     * @param applicationLayersCacheDirectory cache for the application image layers - usually local
     *     to the application project
     * @param shouldEnsureOwnershipOfApplicationLayersCacheDirectory if {@code true}, ensures the
     *     base image layers cache directory is safe to write to
     */
    public Initializer(
        Path baseImageLayersCacheDirectory,
        boolean shouldEnsureOwnershipOfBaseImageLayersCacheDirectory,
        Path applicationLayersCacheDirectory,
        boolean shouldEnsureOwnershipOfApplicationLayersCacheDirectory) {
      this.baseImageLayersCacheDirectory = baseImageLayersCacheDirectory;
      this.shouldEnsureOwnershipOfBaseImageLayersCacheDirectory =
          shouldEnsureOwnershipOfBaseImageLayersCacheDirectory;
      this.applicationLayersCacheDirectory = applicationLayersCacheDirectory;
      this.shouldEnsureOwnershipOfApplicationLayersCacheDirectory =
          shouldEnsureOwnershipOfApplicationLayersCacheDirectory;
    }

    public Caches init()
        throws CacheMetadataCorruptedException, CacheDirectoryNotOwnedException,
            CacheDirectoryCreationException, IOException {
      if (shouldEnsureOwnershipOfBaseImageLayersCacheDirectory) {
        ensureOwnership(baseImageLayersCacheDirectory);
      }
      if (shouldEnsureOwnershipOfApplicationLayersCacheDirectory) {
        ensureOwnership(applicationLayersCacheDirectory);
      }

      return new Caches(baseImageLayersCacheDirectory, applicationLayersCacheDirectory);
    }
  }

  private final Cache baseCache;
  private final Cache applicationCache;

  /** Instantiate with {@link Initializer#init}. */
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
