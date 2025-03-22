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

package com.google.cloud.tools.jib.api.buildplan;

import javax.annotation.Nullable;

/** Indicates the format of the tar archive. */
public enum CompressionAlgorithm {

  /**
   * No compression.
   *
   * <p>This is only specified in combination with {@link ImageFormat#OCI}, see <a
   * href="https://github.com/opencontainers/image-spec/blob/main/layer.md#zstd-media-types">oci
   * media types</a>
   */
  NONE,

  /*
   * gzip is the historical compression format of container layers.
   */
  GZIP,

  /**
   * zstd is more efficient at compression and much faster at decompression than gzip.
   *
   * <p>zstd is only specified in combination with {@link ImageFormat#OCI}, see <a
   * href="https://github.com/opencontainers/image-spec/blob/main/layer.md#zstd-media-types">oci
   * media types</a>
   */
  ZSTD;

  /**
   * Deduce the compression from the layer media type suffix (.gzip with docker or +gzip/+zstd with
   * oci).
   *
   * @param mediaType The layer media type
   * @return the compression algorithm
   */
  public static CompressionAlgorithm fromMediaType(@Nullable String mediaType) {
    if (mediaType == null) {
      throw new IllegalStateException("Can't deduce compression without mediaType");
    } else if (mediaType.endsWith("gzip")) {
      return GZIP;
    } else if (mediaType.endsWith("zstd")) {
      return ZSTD;
    } else {
      // TODO Detect +xxxx or .xxxx where xxxx is unknown ?
      return NONE;
    }
  }
}
