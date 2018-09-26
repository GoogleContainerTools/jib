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
import com.google.common.io.ByteStreams;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.GZIPInputStream;
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

  // TODO: javadoc
  private static DescriptorDigest getDiffIdByDecompressingFile(Path compressedFile)
      throws IOException {
    try (CountingDigestOutputStream diffIdCaptureOutputStream =
        new CountingDigestOutputStream(ByteStreams.nullOutputStream())) {
      try (InputStream fileInputStream =
              new BufferedInputStream(Files.newInputStream(compressedFile));
          GZIPInputStream decompressorStream = new GZIPInputStream(fileInputStream)) {
        ByteStreams.copy(decompressorStream, diffIdCaptureOutputStream);
      }
      return diffIdCaptureOutputStream.toBlobDescriptor().getDigest();
    }
  }

  private final DefaultCacheStorageFiles defaultCacheStorageFiles;

  DefaultCacheStorageWriter(DefaultCacheStorageFiles defaultCacheStorageFiles) {
    this.defaultCacheStorageFiles = defaultCacheStorageFiles;
  }

  // TODO: Javadoc
  CacheEntry write(CompressedCacheWrite compressedCacheWrite) throws IOException {
    // Creates the layers directory if it doesn't exist.
    Files.createDirectories(defaultCacheStorageFiles.getLayersDirectory());

    // Creates the temporary directory.
    try (TemporaryDirectory temporaryDirectory = new TemporaryDirectory()) {
      Path temporaryLayerDirectory = temporaryDirectory.getDirectory();

      // Writes the layer file to the temporary directory.
      WrittenLayer writtenLayer =
          writeCompressedLayerBlobToDirectory(
              compressedCacheWrite.getCompressedLayerBlob(), temporaryLayerDirectory);

      // Moves the temporary directory to the final location.
      moveIfDoesNotExist(
          temporaryLayerDirectory,
          defaultCacheStorageFiles.getLayerDirectory(writtenLayer.layerDigest));

      // Updates cacheEntry with the blob information.
      Path layerFile =
          defaultCacheStorageFiles.getLayerFile(writtenLayer.layerDigest, writtenLayer.layerDiffId);
      return DefaultCacheEntry.builder()
          .setLayerDigest(writtenLayer.layerDigest)
          .setLayerDiffId(writtenLayer.layerDiffId)
          .setLayerSize(writtenLayer.layerSize)
          .setLayerBlob(Blobs.from(layerFile))
          .build();
    }
  }

  /**
   * Writes the {@link UncompressedCacheWrite}.
   *
   * <p>The {@link UncompressedCacheWrite} is written out to the cache directory in the form:
   *
   * <ul>
   *   <li>The {@link UncompressedCacheWrite#getUncompressedLayerBlob} and {@link
   *       UncompressedCacheWrite#getMetadataBlob} are written to the layer directory under the
   *       layers directory corresponding to the layer blob.
   *   <li>The {@link UncompressedCacheWrite#getSelector} is written to the selector file under the
   *       selectors directory.
   * </ul>
   *
   * Note that writes that fail to clean up unfinished temporary directories could result in stray
   * directories in the layers directory. Cache reads should ignore these stray directories.
   *
   * @param uncompressedCacheWrite the {@link UncompressedCacheWrite} to write out
   * @return the {@link CacheEntry} representing the written entry
   * @throws IOException if an I/O exception occurs
   */
  CacheEntry write(UncompressedCacheWrite uncompressedCacheWrite) throws IOException {
    // Creates the layers directory if it doesn't exist.
    Files.createDirectories(defaultCacheStorageFiles.getLayersDirectory());

    // Creates the temporary directory.
    try (TemporaryDirectory temporaryDirectory = new TemporaryDirectory()) {
      Path temporaryLayerDirectory = temporaryDirectory.getDirectory();

      // Writes the layer file to the temporary directory.
      WrittenLayer writtenLayer =
          writeUncompressedLayerBlobToDirectory(
              uncompressedCacheWrite.getUncompressedLayerBlob(), temporaryLayerDirectory);

      // Writes the metadata to the temporary directory.
      if (uncompressedCacheWrite.getMetadataBlob().isPresent()) {
        writeMetadataBlobToDirectory(
            uncompressedCacheWrite.getMetadataBlob().get(), temporaryLayerDirectory);
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
      if (uncompressedCacheWrite.getMetadataBlob().isPresent()) {
        Path metadataFile = defaultCacheStorageFiles.getMetadataFile(writtenLayer.layerDigest);
        cacheEntryBuilder.setMetadataBlob(Blobs.from(metadataFile)).build();
      }

      // Write the selector file.
      if (uncompressedCacheWrite.getSelector().isPresent()) {
        writeSelector(uncompressedCacheWrite.getSelector().get(), writtenLayer.layerDigest);
      }

      return cacheEntryBuilder.build();
    }
  }

  /**
   * Writes a compressed {@code layerBlob} to the {@code layerDirectory}.
   *
   * @param compressedLayerBlob the compressed layer {@link Blob}
   * @param layerDirectory the directory for the layer
   * @return a {@link WrittenLayer} with the written layer information
   * @throws IOException if an I/O exception occurs
   */
  private WrittenLayer writeCompressedLayerBlobToDirectory(
      Blob compressedLayerBlob, Path layerDirectory) throws IOException {
    // Writes the layer file to the temporary directory.
    Path temporaryLayerFile = Files.createTempFile(layerDirectory, null, null);
    temporaryLayerFile.toFile().deleteOnExit();

    BlobDescriptor layerBlobDescriptor;
    try (OutputStream fileOutputStream =
        new BufferedOutputStream(Files.newOutputStream(temporaryLayerFile))) {
      layerBlobDescriptor = compressedLayerBlob.writeTo(fileOutputStream);
    }

    // Gets the diff ID.
    DescriptorDigest layerDiffId = getDiffIdByDecompressingFile(temporaryLayerFile);

    Path layerFile = layerDirectory.resolve(defaultCacheStorageFiles.getLayerFilename(layerDiffId));
    moveIfDoesNotExist(temporaryLayerFile, layerFile);

    return new WrittenLayer(
        layerBlobDescriptor.getDigest(), layerDiffId, layerBlobDescriptor.getSize());
  }

  /**
   * Writes an uncompressed {@code layerBlob} to the {@code layerDirectory}.
   *
   * @param uncompressedLayerBlob the uncompressed layer {@link Blob}
   * @param layerDirectory the directory for the layer
   * @return a {@link WrittenLayer} with the written layer information
   * @throws IOException if an I/O exception occurs
   */
  private WrittenLayer writeUncompressedLayerBlobToDirectory(
      Blob uncompressedLayerBlob, Path layerDirectory) throws IOException {
    Path temporaryLayerFile = Files.createTempFile(layerDirectory, null, null);
    temporaryLayerFile.toFile().deleteOnExit();

    try (CountingDigestOutputStream compressedDigestOutputStream =
        new CountingDigestOutputStream(
            new BufferedOutputStream(Files.newOutputStream(temporaryLayerFile)))) {
      // Writes the layer with GZIP compression. The original bytes are captured as the layer's
      // diff ID and the bytes outputted from the GZIP compression are captured as the layer's
      // content descriptor.
      GZIPOutputStream compressorStream = new GZIPOutputStream(compressedDigestOutputStream);
      DescriptorDigest layerDiffId = uncompressedLayerBlob.writeTo(compressorStream).getDigest();

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
