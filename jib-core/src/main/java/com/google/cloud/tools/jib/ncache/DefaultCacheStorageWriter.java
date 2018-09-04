/*
 * Copyright 2018 Google LLC. All rights reserved.
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

import com.google.cloud.tools.jib.blob.BlobDescriptor;
import com.google.cloud.tools.jib.blob.Blobs;
import com.google.cloud.tools.jib.filesystem.FileOperations;
import com.google.cloud.tools.jib.hash.CountingDigestOutputStream;
import com.google.cloud.tools.jib.image.DescriptorDigest;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;

/** Writes to the default cache storage engine. */
class DefaultCacheStorageWriter {

  private final DefaultCacheStorageFiles defaultCacheStorageFiles;

  DefaultCacheStorageWriter(DefaultCacheStorageFiles defaultCacheStorageFiles) {
    this.defaultCacheStorageFiles = defaultCacheStorageFiles;
  }

  /**
   * Writes the {@link CacheWrite}.
   *
   * @param cacheWrite the {@link CacheWrite} to write out
   * @return the {@link CacheEntry} representing the written entry
   * @throws IOException if an I/O exception occurs
   */
  CacheEntry write(CacheWrite cacheWrite) throws IOException {
    Path temporaryLayerFile = defaultCacheStorageFiles.createTemporaryFile();

    // Writes the UnwrittenLayer layer BLOB to a file to convert into a CachedLayer.
    try (CountingDigestOutputStream compressedDigestOutputStream =
        new CountingDigestOutputStream(
            new BufferedOutputStream(FileOperations.newLockingOutputStream(temporaryLayerFile)))) {
      // Writes the layer with GZIP compression. The original bytes are captured as the layer's
      // diff ID and the bytes outputted from the GZIP compression are captured as the layer's
      // content descriptor.
      GZIPOutputStream compressorStream = new GZIPOutputStream(compressedDigestOutputStream);
      DescriptorDigest layerDiffId =
          cacheWrite.getLayerBlob().writeTo(compressorStream).getDigest();

      // The GZIPOutputStream must be closed in order to write out the remaining compressed data.
      compressorStream.close();
      BlobDescriptor compressedBlobDescriptor = compressedDigestOutputStream.toBlobDescriptor();
      DescriptorDigest layerDigest = compressedBlobDescriptor.getDigest();

      // Renames the temporary layer file to the correct filename.
      Path layerFile = defaultCacheStorageFiles.getLayerFile(layerDigest, layerDiffId);
      Files.createDirectories(layerFile.getParent());
      try {
        Files.move(temporaryLayerFile, layerFile);

      } catch (FileAlreadyExistsException ignored) {
        // If the file already exists, we skip renaming and use the existing file. This happens if a
        // new layer happens to have the same content as a previously-cached layer.
        //
        // Do not attempt to remove the try-catch block with the idea of checking file existence
        // before moving; there can be concurrent file moves.
      }

      // Writes the selector file.
      if (cacheWrite.getSelector().isPresent()) {
        Path selectorFile =
            defaultCacheStorageFiles.getSelectorFile(cacheWrite.getSelector().get());
        Files.createDirectories(selectorFile.getParent());
        Blobs.writeToFileWithLock(Blobs.from(layerDigest.getHash()), selectorFile);
      }

      DefaultCacheEntry.Builder cacheEntryBuilder =
          DefaultCacheEntry.builder()
              .setLayerDigest(layerDigest)
              .setLayerDiffId(layerDiffId)
              .setLayerSize(compressedBlobDescriptor.getSize())
              .setLayerBlob(Blobs.from(layerFile));

      if (!cacheWrite.getMetadataBlob().isPresent()) {
        return cacheEntryBuilder.build();
      }

      // Writes metadata blob to the layer directory.
      Path metadataFile = defaultCacheStorageFiles.getMetadataFile(layerDigest);
      Blobs.writeToFileWithLock(cacheWrite.getMetadataBlob().get(), metadataFile);

      return cacheEntryBuilder.setMetadataBlob(Blobs.from(metadataFile)).build();
    }
  }
}
