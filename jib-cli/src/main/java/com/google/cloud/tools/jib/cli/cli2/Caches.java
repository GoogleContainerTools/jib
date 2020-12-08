/*
 * Copyright 2020 Google LLC.
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

package com.google.cloud.tools.jib.cli.cli2;

import com.google.cloud.tools.jib.filesystem.XdgDirectories;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import javax.annotation.Nullable;

/** A class to determine cache locations for any cli commands. */
public class Caches {

  @Nullable private final Path baseImageCache;
  private final Path projectCache;

  /**
   * Create a caches helper for cli cache locations.
   *
   * @param commonCliOptions cli options
   * @param contextRoot the context root, if a single file, use the parent directory
   * @return an instance of Caches with cli specific cache locations
   */
  public static Caches from(CommonCliOptions commonCliOptions, Path contextRoot) {
    Path defaultProjectCache =
        XdgDirectories.getCacheHome()
            .resolve("cli")
            .resolve("projects")
            .resolve(hashPath(contextRoot));
    return new Caches(
        commonCliOptions.getBaseImageCache().orElse(null),
        commonCliOptions.getProjectCache().orElse(defaultProjectCache));
  }

  @VisibleForTesting
  static String hashPath(Path path) {
    try {
      return new String(
          MessageDigest.getInstance("SHA-256")
              .digest(path.toAbsolutePath().normalize().toString().getBytes(Charsets.UTF_8)),
          Charsets.UTF_8);
    } catch (NoSuchAlgorithmException ex) {
      throw new RuntimeException(
          "SHA-256 algorithm implementation not found - might be a broken JVM");
    }
  }

  public Caches(@Nullable Path baseImageCache, Path projectCache) {
    this.baseImageCache = baseImageCache;
    this.projectCache = projectCache;
  }

  public Optional<Path> getBaseImageCache() {
    return Optional.ofNullable(baseImageCache);
  }

  public Path getProjectCache() {
    return projectCache;
  }
}
