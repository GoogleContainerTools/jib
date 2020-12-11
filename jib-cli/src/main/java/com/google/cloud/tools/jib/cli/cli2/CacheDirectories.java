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
import java.io.IOException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import javax.annotation.Nullable;

/** A class to determine cache locations for any cli commands. */
public class CacheDirectories {

  @Nullable private final Path baseImageCache;
  private final Path projectCache;

  /**
   * Create a caches helper for cli cache locations.
   *
   * @param commonCliOptions cli options
   * @param contextRoot the context root, if a single file, use the parent directory, this context
   *     root must exist
   * @return an instance of CacheDirectories with cli specific cache locations
   */
  public static CacheDirectories from(CommonCliOptions commonCliOptions, Path contextRoot) {
    return new CacheDirectories(
        commonCliOptions.getBaseImageCache().orElse(null),
        commonCliOptions
            .getProjectCache()
            .orElse(
                XdgDirectories.getCacheHome()
                    .resolve("cli")
                    .resolve("projects")
                    .resolve(getProjectCacheDirectoryFromProject(contextRoot))));
  }

  @VisibleForTesting
  static String getProjectCacheDirectoryFromProject(Path path) {
    try {
      byte[] hashedBytes =
          MessageDigest.getInstance("SHA-256")
              .digest(path.toFile().getCanonicalPath().getBytes(Charsets.UTF_8));
      StringBuilder stringBuilder = new StringBuilder(2 * hashedBytes.length);
      for (byte b : hashedBytes) {
        stringBuilder.append(String.format("%02x", b));
      }
      return stringBuilder.toString();
    } catch (IOException | SecurityException ex) {
      throw new RuntimeException(
          "Unable to create cache directory for project path: "
              + path
              + " - you can try to configure --project-cache manually",
          ex);
    } catch (NoSuchAlgorithmException ex) {
      throw new RuntimeException(
          "SHA-256 algorithm implementation not found - might be a broken JVM");
    }
  }

  public CacheDirectories(@Nullable Path baseImageCache, Path projectCache) {
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
