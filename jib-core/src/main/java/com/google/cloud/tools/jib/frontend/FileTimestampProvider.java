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

import com.google.cloud.tools.jib.filesystem.AbsoluteUnixPath;
import java.time.Instant;

/** Provides a timestamp given a file's extraction path. */
@FunctionalInterface
public interface FileTimestampProvider {

  /** A provider that returns Epoch + 1 second for all files. */
  FileTimestampProvider DEFAULT = ignored -> Instant.ofEpochSecond(1);

  /**
   * Returns the modification timestamp to apply to a file on the container.
   *
   * @param pathOnContainer the file's extraction path
   * @return the timestamp
   */
  Instant generateTimestamp(AbsoluteUnixPath pathOnContainer);
}
