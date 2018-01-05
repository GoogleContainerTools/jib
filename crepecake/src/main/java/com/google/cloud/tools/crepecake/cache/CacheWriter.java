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

package com.google.cloud.tools.crepecake.cache;

import com.google.cloud.tools.crepecake.blob.BlobDescriptor;
import com.google.cloud.tools.crepecake.hash.CountingDigestOutputStream;
import com.google.cloud.tools.crepecake.image.DescriptorDigest;
import com.google.cloud.tools.crepecake.image.UnwrittenLayer;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.zip.GZIPOutputStream;

/** Writes {@link UnwrittenLayer}s to the cache. */
public class CacheWriter {

  private final Cache cache;

  public CacheWriter(Cache cache) {
    this.cache = cache;
  }

  public CachedLayer writeLayer(UnwrittenLayer layer) throws IOException {
    // Writes to a temporary file first because the UnwrittenLayer needs to be written first to
    // obtain its digest.
    File tempLayerFile = Files.createTempFile(cache.getCacheDirectory(), null, null).toFile();
    tempLayerFile.deleteOnExit();

    // Writes the UnwrittenLayer layer BLOB to a file to convert into a CachedLayer.
    try (CountingDigestOutputStream compressedDigestOutputStream =
        new CountingDigestOutputStream(
            new BufferedOutputStream(new FileOutputStream(tempLayerFile)))) {
      // Writes the layer with GZIP compression. The original bytes are captured as the layer's
      // diff ID and the bytes outputted from the GZIP compression are captured as the layer's
      // content descriptor.
      GZIPOutputStream compressorStream = new GZIPOutputStream(compressedDigestOutputStream);
      DescriptorDigest diffId = layer.getBlob().writeTo(compressorStream).getDigest();

      // The GZIPOutputStream must be closed in order to write out the remaining compressed data.
      compressorStream.close();
      BlobDescriptor compressedBlobDescriptor = compressedDigestOutputStream.toBlobDescriptor();

      // Renames the temporary layer file to the correct filename.
      File layerFile =
          CacheFiles.getLayerFile(cache.getCacheDirectory(), compressedBlobDescriptor.getDigest());
      if (!tempLayerFile.renameTo(layerFile)) {
        throw new IOException(
            "Could not rename layer "
                + compressedBlobDescriptor.getDigest().getHash()
                + " to "
                + layerFile);
      }

      return new CachedLayer(layerFile, compressedBlobDescriptor, diffId);
    }
  }
}
