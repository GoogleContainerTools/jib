/*
 * Copyright 2018 Google Inc.
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

import java.nio.file.Path;

/**
 * Thrown when trying to use a directory as {@link Cache}, but the directory might be used by other
 * applications.
 */
public class CacheDirectoryNotOwnedException extends Exception {

  private final Path cacheDirectory;

  /** Initializes with the cache directory that was unsafe to use. */
  CacheDirectoryNotOwnedException(Path cacheDirectory) {
    super();

    this.cacheDirectory = cacheDirectory;
  }

  public Path getCacheDirectory() {
    return cacheDirectory;
  }
}
