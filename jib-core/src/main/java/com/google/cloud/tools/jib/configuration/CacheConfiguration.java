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

package com.google.cloud.tools.jib.configuration;

import com.google.cloud.tools.jib.cache.CacheDirectoryCreationException;
import com.google.cloud.tools.jib.filesystem.UserCacheHome;
import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Represents the location of the cache. Provides static methods for resolving a location for the
 * cache.
 */
public class CacheConfiguration {

  /**
   * The default directory for caching the base image layers, in {@code [user cache
   * home]/google-cloud-tools-java/jib}.
   */
  @VisibleForTesting
  static final Path DEFAULT_BASE_CACHE_DIRECTORY =
      UserCacheHome.getCacheHome().resolve("google-cloud-tools-java").resolve("jib");

  /**
   * The cache is at an arbitrary path.
   *
   * @param cacheDirectory the path to the cache directory. This must be a non-existent directory or
   *     a previously-used cache directory.
   * @return the corresponding {@link CacheConfiguration}
   */
  public static CacheConfiguration forPath(Path cacheDirectory) {
    return new CacheConfiguration(cacheDirectory, true);
  }

  /**
   * The cache is a temporary directory that is deleted afterwards.
   *
   * @return the corresponding {@link CacheConfiguration}
   * @throws CacheDirectoryCreationException if a temporary directory cannot be created
   */
  public static CacheConfiguration makeTemporary() throws CacheDirectoryCreationException {
    try {
      Path temporaryDirectory = Files.createTempDirectory(null);
      temporaryDirectory.toFile().deleteOnExit();
      return new CacheConfiguration(temporaryDirectory, false);

    } catch (IOException ex) {
      throw new CacheDirectoryCreationException(ex);
    }
  }

  /**
   * The cache is at the default user-level cache directory. This is usually to store base image
   * layers, which can be shared between projects. The default user-level cache directory is {@code
   * [user cache home]/google-cloud-tools-java/jib}.
   *
   * @return the corresponding {@link CacheConfiguration}
   */
  public static CacheConfiguration forDefaultUserLevelCacheDirectory() {
    return new CacheConfiguration(DEFAULT_BASE_CACHE_DIRECTORY, true);
  }

  private final Path cacheDirectory;
  private final boolean shouldEnsureOwnership;

  private CacheConfiguration(Path cacheDirectory, boolean shouldEnsureOwnership) {
    this.cacheDirectory = cacheDirectory;
    this.shouldEnsureOwnership = shouldEnsureOwnership;
  }

  /**
   * Gets the path to the cache directory.
   *
   * @return the cache directory path
   */
  public Path getCacheDirectory() {
    return cacheDirectory;
  }

  /**
   * Gets whether or not the cache directory should be checked for write safety.
   *
   * @return {@code true} if ownership by Jib should be checked
   */
  public boolean shouldEnsureOwnership() {
    return shouldEnsureOwnership;
  }
}
