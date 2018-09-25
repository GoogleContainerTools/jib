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

package com.google.cloud.tools.jib.ncache;

import com.google.cloud.tools.jib.blob.Blob;
import com.google.cloud.tools.jib.blob.Blobs;
import com.google.cloud.tools.jib.image.LayerEntry;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import java.time.Instant;

/**
 * Metadata that is the latest last modified time of all the source files in a list of {@link
 * LayerEntry}s.
 */
class LastModifiedMetadata {

  /**
   * Generates the metadata {@link Blob} for the list of {@link LayerEntry}s. The metadata is the
   * latest last modified time of all the source files in the list of {@link LayerEntry}s serialized
   * using {@link Instant#toString}.
   *
   * @param layerEntries the list of {@link LayerEntry}s
   * @return the generated metadata
   */
  static Blob generateMetadata(ImmutableList<LayerEntry> layerEntries) throws IOException {
    return Blobs.from(getLastModifiedTime(layerEntries).toInstant().toString());
  }

  /**
   * Gets the latest last modified time of all the source files in the list of {@link LayerEntry}s.
   *
   * @param layerEntries the list of {@link LayerEntry}s
   * @return the last modified time
   */
  static FileTime getLastModifiedTime(ImmutableList<LayerEntry> layerEntries) throws IOException {
    FileTime maxLastModifiedTime = FileTime.from(Instant.MIN);

    for (LayerEntry layerEntry : layerEntries) {
      FileTime lastModifiedTime = Files.getLastModifiedTime(layerEntry.getSourceFile());
      if (lastModifiedTime.compareTo(maxLastModifiedTime) > 0) {
        maxLastModifiedTime = lastModifiedTime;
      }
    }

    return maxLastModifiedTime;
  }

  private LastModifiedMetadata() {}
}
