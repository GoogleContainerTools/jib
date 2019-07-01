/*
 * Copyright 2019 Google LLC.
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

package com.google.cloud.tools.jib.plugins.common;

import com.google.cloud.tools.jib.api.AbsoluteUnixPath;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.function.BiFunction;

/** Modification time provider which returns original file modification time. */
class KeepOriginalModificationTimeProvider implements BiFunction<Path, AbsoluteUnixPath, Instant> {

  /**
   * Returns the original file modification time.
   *
   * @param file path to file
   * @param pathInContainer path to file in container
   * @return the original file modification time
   */
  @Override
  public Instant apply(Path file, AbsoluteUnixPath pathInContainer) {
    try {
      return Files.getLastModifiedTime(file).toInstant();
    } catch (IOException ex) {
      throw new IllegalStateException("Unable to define the modification time of " + file, ex);
    }
  }
}
