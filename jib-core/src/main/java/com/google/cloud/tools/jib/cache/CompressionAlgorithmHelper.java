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

package com.google.cloud.tools.jib.cache;

import com.google.cloud.tools.jib.api.buildplan.CompressionAlgorithm;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorStreamFactory;

/**
 * Utilities that cannot be in {@link CompressionAlgorithm} to avoid jib-build-plan to depend on
 * commons-compress.
 */
public class CompressionAlgorithmHelper {

  /**
   * Detect the compression algorithm by looking at the magic bytes in the layerFile.
   *
   * @param layerFile The layer file in the cache
   * @return the compression algorithm of the layer
   * @throws IOException if an I/O exception occurs
   */
  public static CompressionAlgorithm detectCompressionAlgorithm(Path layerFile) throws IOException {
    // The compression algorithm is not part of the file metadata, thus look at the magic bytes
    CompressionAlgorithm compressionAlgorithm;
    InputStream in = new BufferedInputStream(Files.newInputStream(layerFile));
    String signatureType;
    try {
      signatureType = CompressorStreamFactory.detect(in);
      in.close();
      switch (signatureType) {
        case CompressorStreamFactory.GZIP:
          compressionAlgorithm = CompressionAlgorithm.GZIP;
          break;
        case CompressorStreamFactory.ZSTANDARD:
          compressionAlgorithm = CompressionAlgorithm.ZSTD;
          break;
        default:
          throw new IllegalStateException("Unsupported compression type " + signatureType);
      }
    } catch (CompressorException e) {
      compressionAlgorithm = CompressionAlgorithm.NONE;
    }
    return compressionAlgorithm;
  }
}
