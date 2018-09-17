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

import com.google.cloud.tools.jib.blob.Blob;
import com.google.cloud.tools.jib.blob.BlobDescriptor;
import com.google.cloud.tools.jib.blob.Blobs;
import com.google.cloud.tools.jib.filesystem.TemporaryDirectory;
import com.google.cloud.tools.jib.hash.CountingDigestOutputStream;
import com.google.cloud.tools.jib.image.DescriptorDigest;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.GZIPOutputStream;

/** Writes to the default cache storage engine. */
class DefaultCacheStorageWriter {

  /** Holds information about a layer that was written. */
  private static class WrittenLayer {

    private final DescriptorDigest layerDigest;
    private final DescriptorDigest layerDiffId;
    private final long layerSize;

    private WrittenLayer(
        DescriptorDigest layerDigest, DescriptorDigest layerDiffId, long layerSize) {
      this.layerDigest = layerDigest;
      this.layerDiffId = layerDiffId;
      this.layerSize = layerSize;
    }
  }

  /**
   * Attempts to move {@code source} to {@code destination}. If {@code destination} already exists,
   * this does nothing. Attempts an atomic move first, and falls back to non-atomic if the
   * filesystem does not support atomic moves.
   *
   * @param source the source path
   * @param destination the destination path
   * @throws IOException if an I/O exception occurs
   */
  private static void moveIfDoesNotExist(Path source, Path destination) throws IOException {
    try {
      Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);

    } catch (FileAlreadyExistsException ignored) {
      // If the file already exists, we skip renaming and use the existing file. This happens if a
      // new layer happens to have the same content as a previously-cached layer.
      //
      // Do not attempt to remove the try-catch block with the idea of checking file existence
      // before moving; there can be concurrent file moves.

    } catch (AtomicMoveNotSupportedException ignored) {
      try {
        Files.move(source, destination);

      } catch (FileAlreadyExistsException alsoIgnored) {
        // Same reasoning
      }
    }
  }

  private final DefaultCacheStorageFiles defaultCacheStorageFiles;

  DefaultCacheStorageWriter(DefaultCacheStorageFiles defaultCacheStorageFiles) {
    this.defaultCacheStorageFiles = defaultCacheStorageFiles;
  }

  /**
   * Writes the {@link CacheWrite}.
   *
   * <p>The {@link CacheWrite} is written out to the cache directory in the form:
   *
   * <ul>
   *   <li>The {@link CacheWrite#getLayerBlob} and {@link CacheWrite#getMetadataBlob} are written to
   *       the layer directory under the layers directory corresponding to the layer blob.
   *   <li>The {@link CacheWrite#getSelector} is written to the selector file under the selectors
   *       directory.
   * </ul>
   *
   * Note that writes that fail to clean up unfinished temporary directories could result in stray
   * directories in the layers directory. Cache reads should ignore these stray directories.
   *
   * @param cacheWrite the {@link CacheWrite} to write out
   * @return the {@link CacheEntry} representing the written entry
   * @throws IOException if an I/O exception occurs
   */
  CacheEntry write(CacheWrite cacheWrite) throws IOException {
    // Creates the layers directory if it doesn't exist.
    Files.createDirectories(defaultCacheStorageFiles.getLayersDirectory());

    // Creates the temporary directory.
    try (TemporaryDirectory temporaryDirectory = new TemporaryDirectory()) {
      Path temporaryLayerDirectory = temporaryDirectory.getDirectory();

      // Writes the layer file to the temporary directory.
      WrittenLayer writtenLayer =
          writeLayerBlobToDirectory(cacheWrite.getLayerBlob(), temporaryLayerDirectory);

      // Writes the metadata to the temporary directory.
      if (cacheWrite.getMetadataBlob().isPresent()) {
        writeMetadataBlobToDirectory(cacheWrite.getMetadataBlob().get(), temporaryLayerDirectory);
      }

      // Moves the temporary directory to the final location.
      moveIfDoesNotExist(
          temporaryLayerDirectory,
          defaultCacheStorageFiles.getLayerDirectory(writtenLayer.layerDigest));

      // Updates cacheEntry with the blob information.
      Path layerFile =
          defaultCacheStorageFiles.getLayerFile(writtenLayer.layerDigest, writtenLayer.layerDiffId);
      DefaultCacheEntry.Builder cacheEntryBuilder =
          DefaultCacheEntry.builder()
              .setLayerDigest(writtenLayer.layerDigest)
              .setLayerDiffId(writtenLayer.layerDiffId)
              .setLayerSize(writtenLayer.layerSize)
              .setLayerBlob(Blobs.from(layerFile));
      if (cacheWrite.getMetadataBlob().isPresent()) {
        Path metadataFile = defaultCacheStorageFiles.getMetadataFile(writtenLayer.layerDigest);
        cacheEntryBuilder.setMetadataBlob(Blobs.from(metadataFile)).build();
      }

      // Write the selector file.
      if (cacheWrite.getSelector().isPresent()) {
        writeSelector(cacheWrite.getSelector().get(), writtenLayer.layerDigest);
      }

      return cacheEntryBuilder.build();
    }
  }

  /**
   * Writes the {@code layerBlob} to the {@code layerDirectory}.
   *
   * @param layerBlob the layer {@link Blob}
   * @param layerDirectory the directory for the layer
   * @return a {@link WrittenLayer} with the written layer information
   * @throws IOException if an I/O exception occurs
   */
  private WrittenLayer writeLayerBlobToDirectory(Blob layerBlob, Path layerDirectory)
      throws IOException {
    Path temporaryLayerFile = Files.createTempFile(layerDirectory, null, null);
    temporaryLayerFile.toFile().deleteOnExit();

    try (CountingDigestOutputStream compressedDigestOutputStream =
        new CountingDigestOutputStream(
            new BufferedOutputStream(Files.newOutputStream(temporaryLayerFile)))) {
      // Writes the layer with GZIP compression. The original bytes are captured as the layer's
      // diff ID and the bytes outputted from the GZIP compression are captured as the layer's
      // content descriptor.
      GZIPOutputStream compressorStream = new GZIPOutputStream(compressedDigestOutputStream);
      DescriptorDigest layerDiffId = layerBlob.writeTo(compressorStream).getDigest();

      // The GZIPOutputStream must be closed in order to write out the remaining compressed data.
      compressorStream.close();
      BlobDescriptor compressedBlobDescriptor = compressedDigestOutputStream.toBlobDescriptor();
      DescriptorDigest layerDigest = compressedBlobDescriptor.getDigest();
      long layerSize = compressedBlobDescriptor.getSize();

      // Renames the temporary layer file to the correct filename.
      Path layerFile =
          layerDirectory.resolve(defaultCacheStorageFiles.getLayerFilename(layerDiffId));
      moveIfDoesNotExist(temporaryLayerFile, layerFile);

      return new WrittenLayer(layerDigest, layerDiffId, layerSize);
    }
  }

  /**
   * Writes the {@code metadataBlob} to the {@code layerDirectory}.
   *
   * @param metadataBlob the metadata {@link Blob}
   * @param layerDirectory the directory for the layer the metadata is for
   * @throws IOException if an I/O exception occurs
   */
  private void writeMetadataBlobToDirectory(Blob metadataBlob, Path layerDirectory)
      throws IOException {
    Path metadataFile = layerDirectory.resolve(defaultCacheStorageFiles.getMetadataFilename());
    Blobs.writeToFileWithLock(metadataBlob, metadataFile);
  }

  /**
   * Writes the {@code selector} to a file in the selectors directory, with contents {@code
   * layerDigest}.
   *
   * @param selector the selector
   * @param layerDigest the layer digest it selects
   * @throws IOException if an I/O exception occurs
   */
  private void writeSelector(DescriptorDigest selector, DescriptorDigest layerDigest)
      throws IOException {
    Path selectorFile = defaultCacheStorageFiles.getSelectorFile(selector);

    // Creates the selectors directory if it doesn't exist.
    Files.createDirectories(selectorFile.getParent());

    // Writes the selector to a temporary file and then moves the file to the intended location.
    Path temporarySelectorFile = Files.createTempFile(null, null);
    temporarySelectorFile.toFile().deleteOnExit();
    Blobs.writeToFileWithLock(Blobs.from(layerDigest.getHash()), temporarySelectorFile);

    // Attempts an atomic move first, and falls back to non-atomic if the file system does not
    // support atomic moves.
    try {
      Files.move(
          temporarySelectorFile,
          selectorFile,
          StandardCopyOption.ATOMIC_MOVE,
          StandardCopyOption.REPLACE_EXISTING);

    } catch (AtomicMoveNotSupportedException ignored) {
      Files.move(temporarySelectorFile, selectorFile, StandardCopyOption.REPLACE_EXISTING);
    }
  }
}
