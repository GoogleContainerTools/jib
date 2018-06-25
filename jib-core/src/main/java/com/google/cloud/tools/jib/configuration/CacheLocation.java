/*
 * Copyright 2018 Google LLC. All rights reserved.
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

import com.google.cloud.tools.jib.filesystem.UserCacheHome;
import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Represents the location of the cache. Provides static methods for resolving a location for the cache. */
public class CacheLocation {

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
   * @param cacheDirectory the path to the cache directory. This must be a non-existent directory or a previously-used cache directory.
   * @return the corresponding {@link CacheLocation}
   */
  public static CacheLocation atPath(Path cacheDirectory) {
    return new CacheLocation(cacheDirectory);
  }

  /**
   * The cache is a temporary directory that is deleted afterwards.
   *
   * @return the corresponding {@link CacheLocation}
   * @throws IOException if a temporary directory cannot be created
   */
  public static CacheLocation makeTemporary() throws IOException {
    Path temporaryDirectory = Files.createTempDirectory(null);
    temporaryDirectory.toFile().deleteOnExit();
    return new CacheLocation(temporaryDirectory);
  }

  /**
   * The cache is at the default user-level cache directory. This is usually to store base image layers, which can be shared between projects. The default user-level cache directory is {@code [user cache
   * home]/google-cloud-tools-java/jib}.
   *
   * @return the corresponding {@link CacheLocation}
   */
  public static CacheLocation atDefaultUserLevelCacheDirectory() {
    return new CacheLocation(DEFAULT_BASE_CACHE_DIRECTORY);
  }

  private final Path cacheDirectory;

  private CacheLocation(Path cacheDirectory) {
    this.cacheDirectory = cacheDirectory;
  }

  /**
   * Gets the path to the cache directory.
   *
   * @return the cache directory path
   */
  public Path getCacheDirectory() {
    return cacheDirectory;
  }
}
