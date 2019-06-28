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

package com.google.cloud.tools.jib.api;

import java.nio.file.Path;
import java.time.Instant;

/** Modification time provider which returns fixed instant */
public class FixedModificationTimeProvider implements ModificationTimeProvider {

  /** EPOCH + 1 second */
  public static final Instant EPOCH_PLUS_ONE_SECOND = Instant.ofEpochSecond(1);

  /** Fixed modification time * */
  private final Instant modificationTime;

  /**
   * Initializes with a fixed modification time
   *
   * @param modificationTime fixed modification time
   */
  public FixedModificationTimeProvider(Instant modificationTime) {
    this.modificationTime = modificationTime;
  }

  /**
   * Returns preconfigured fixed modification time
   *
   * @param file path to file
   * @param pathInContainer path to file in container
   * @return preconfigured fixed modification time
   */
  @Override
  public Instant getModificationTime(Path file, AbsoluteUnixPath pathInContainer) {
    return modificationTime;
  }
}
