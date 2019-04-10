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

package com.google.cloud.tools.jib.frontend;

import java.nio.file.Path;
import java.time.Instant;

/** Provides a timestamp given the source path of a file to be added to the container. */
@FunctionalInterface
public interface FileTimestampProvider {

  /** A provider that returns Epoch + 1 second for all files. */
  FileTimestampProvider DEFAULT = ignored -> Instant.ofEpochSecond(1);

  /**
   * Returns the modification timestamp to apply to a file on the container.
   *
   * @param sourcePath the source path of the file to be added to the container.
   * @return the timestamp
   */
  Instant generateTimestamp(Path sourcePath);
}
