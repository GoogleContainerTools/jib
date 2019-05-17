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

package com.google.cloud.tools.jib.cache;

import com.google.cloud.tools.jib.api.DescriptorDigest;
import com.google.cloud.tools.jib.api.ImageReference;
import com.google.cloud.tools.jib.blob.Blob;
import com.google.cloud.tools.jib.blob.BlobDescriptor;
import com.google.cloud.tools.jib.blob.Blobs;
import com.google.cloud.tools.jib.filesystem.FileOperations;
import com.google.cloud.tools.jib.filesystem.LockFile;
import com.google.cloud.tools.jib.filesystem.TemporaryDirectory;
import com.google.cloud.tools.jib.hash.CountingDigestOutputStream;
import com.google.cloud.tools.jib.image.json.BuildableManifestTemplate;
import com.google.cloud.tools.jib.image.json.ContainerConfigurationTemplate;
import com.google.cloud.tools.jib.image.json.V21ManifestTemplate;
import com.google.cloud.tools.jib.json.JsonTemplate;
import com.google.cloud.tools.jib.json.JsonTemplateMapper;
import com.google.common.base.Preconditions;
import com.google.common.io.ByteStreams;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import javax.annotation.Nullable;

/** Writes to the default cache storage engine. */
class CacheStorageWriter {

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
    // If the file already exists, we skip renaming and use the existing file. This happens if a
    // new layer happens to have the same content as a previously-cached layer.
    if (Files.exists(destination)) {
      return;
    }

    try {
      Files.move(source, destination);

    } catch (FileSystemException ex) {
      if (!Files.exists(destination)) {
        // TODO to log that the destination exists
        throw ex;
      }
    }
  }

  /**
   * Decompresses the file to obtain the diff ID.
   *
   * @param compressedFile the file containing the compressed contents
   * @return the digest of the decompressed file
   * @throws IOException if an I/O exception occurs
   */
  private static DescriptorDigest getDiffIdByDecompressingFile(Path compressedFile)
      throws IOException {
    try (CountingDigestOutputStream diffIdCaptureOutputStream =
        new CountingDigestOutputStream(ByteStreams.nullOutputStream())) {
      try (InputStream fileInputStream =
              new BufferedInputStream(Files.newInputStream(compressedFile));
          GZIPInputStream decompressorStream = new GZIPInputStream(fileInputStream)) {
        ByteStreams.copy(decompressorStream, diffIdCaptureOutputStream);
      }
      return diffIdCaptureOutputStream.computeDigest().getDigest();
    }
  }

  /**
   * Writes a json template to the destination path by writing to a temporary file then moving the
   * file.
   *
   * @param jsonTemplate the json template
   * @param destination the destination path
   * @throws IOException if an I/O exception occurs
   */
  private static void writeMetadata(JsonTemplate jsonTemplate, Path destination)
      throws IOException {
    Path temporaryFile = Files.createTempFile(destination.getParent(), null, null);
    temporaryFile.toFile().deleteOnExit();
    try (OutputStream outputStream = Files.newOutputStream(temporaryFile)) {
      JsonTemplateMapper.writeTo(jsonTemplate, outputStream);
    }

    // Attempts an atomic move first, and falls back to non-atomic if the file system does not
    // support atomic moves.
    try {
      Files.move(
          temporaryFile,
          destination,
          StandardCopyOption.ATOMIC_MOVE,
          StandardCopyOption.REPLACE_EXISTING);

    } catch (AtomicMoveNotSupportedException ignored2) {
      Files.move(temporaryFile, destination, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private final CacheStorageFiles cacheStorageFiles;

  CacheStorageWriter(CacheStorageFiles cacheStorageFiles) {
    this.cacheStorageFiles = cacheStorageFiles;
  }

  /**
   * Writes a compressed layer {@link Blob}.
   *
   * <p>The {@code compressedLayerBlob} is written to the layer directory under the layers directory
   * corresponding to the layer blob.
   *
   * @param compressedLayerBlob the compressed layer {@link Blob} to write out
   * @return the {@link CachedLayer} representing the written entry
   * @throws IOException if an I/O exception occurs
   */
  CachedLayer writeCompressed(Blob compressedLayerBlob) throws IOException {
    // Creates the layers directory if it doesn't exist.
    Files.createDirectories(cacheStorageFiles.getLayersDirectory());

    // Creates the temporary directory.
    Files.createDirectories(cacheStorageFiles.getTemporaryDirectory());
    try (TemporaryDirectory temporaryDirectory =
        new TemporaryDirectory(cacheStorageFiles.getTemporaryDirectory())) {
      Path temporaryLayerDirectory = temporaryDirectory.getDirectory();

      // Writes the layer file to the temporary directory.
      WrittenLayer writtenLayer =
          writeCompressedLayerBlobToDirectory(compressedLayerBlob, temporaryLayerDirectory);

      // Moves the temporary directory to the final location.
      moveIfDoesNotExist(
          temporaryLayerDirectory, cacheStorageFiles.getLayerDirectory(writtenLayer.layerDigest));

      // Updates cachedLayer with the blob information.
      Path layerFile =
          cacheStorageFiles.getLayerFile(writtenLayer.layerDigest, writtenLayer.layerDiffId);
      return CachedLayer.builder()
          .setLayerDigest(writtenLayer.layerDigest)
          .setLayerDiffId(writtenLayer.layerDiffId)
          .setLayerSize(writtenLayer.layerSize)
          .setLayerBlob(Blobs.from(layerFile))
          .build();
    }
  }

  /**
   * Writes an uncompressed {@link Blob} out to the cache directory in the form:
   *
   * <ul>
   *   <li>The {@code uncompressedLayerBlob} is written to the layer directory under the layers
   *       directory corresponding to the layer blob.
   *   <li>The {@code selector} is written to the selector file under the selectors directory.
   * </ul>
   *
   * @param uncompressedLayerBlob the {@link Blob} containing the uncompressed layer contents to
   *     write out
   * @param selector the optional selector digest to also reference this layer data. A selector
   *     digest may be a secondary identifier for a layer that is distinct from the default layer
   *     digest.
   * @return the {@link CachedLayer} representing the written entry
   * @throws IOException if an I/O exception occurs
   */
  CachedLayer writeUncompressed(Blob uncompressedLayerBlob, @Nullable DescriptorDigest selector)
      throws IOException {
    // Creates the layers directory if it doesn't exist.
    Files.createDirectories(cacheStorageFiles.getLayersDirectory());

    // Creates the temporary directory. The temporary directory must be in the same FileStore as the
    // final location for Files.move to work.
    Files.createDirectories(cacheStorageFiles.getTemporaryDirectory());
    try (TemporaryDirectory temporaryDirectory =
        new TemporaryDirectory(cacheStorageFiles.getTemporaryDirectory())) {
      Path temporaryLayerDirectory = temporaryDirectory.getDirectory();

      // Writes the layer file to the temporary directory.
      WrittenLayer writtenLayer =
          writeUncompressedLayerBlobToDirectory(uncompressedLayerBlob, temporaryLayerDirectory);

      // Moves the temporary directory to the final location.
      moveIfDoesNotExist(
          temporaryLayerDirectory, cacheStorageFiles.getLayerDirectory(writtenLayer.layerDigest));

      // Updates cachedLayer with the blob information.
      Path layerFile =
          cacheStorageFiles.getLayerFile(writtenLayer.layerDigest, writtenLayer.layerDiffId);
      CachedLayer.Builder cachedLayerBuilder =
          CachedLayer.builder()
              .setLayerDigest(writtenLayer.layerDigest)
              .setLayerDiffId(writtenLayer.layerDiffId)
              .setLayerSize(writtenLayer.layerSize)
              .setLayerBlob(Blobs.from(layerFile));

      // Write the selector file.
      if (selector != null) {
        writeSelector(selector, writtenLayer.layerDigest);
      }

      return cachedLayerBuilder.build();
    }
  }

  /**
   * Saves the manifest and container configuration for a V2.2 or OCI image.
   *
   * @param imageReference the image reference to store the metadata for
   * @param manifestTemplate the manifest
   * @param containerConfiguration the container configuration
   */
  void writeMetadata(
      ImageReference imageReference,
      BuildableManifestTemplate manifestTemplate,
      ContainerConfigurationTemplate containerConfiguration)
      throws IOException {
    Preconditions.checkNotNull(manifestTemplate.getContainerConfiguration());
    Preconditions.checkNotNull(manifestTemplate.getContainerConfiguration().getDigest());

    Path imageDirectory = cacheStorageFiles.getImageDirectory(imageReference);
    Files.createDirectories(imageDirectory);

    try (LockFile ignored1 = LockFile.lock(imageDirectory.resolve("lock"))) {
      writeMetadata(manifestTemplate, imageDirectory.resolve("manifest.json"));
      writeMetadata(containerConfiguration, imageDirectory.resolve("config.json"));
    }
  }

  /**
   * Writes a V2.1 manifest for a given image reference.
   *
   * @param imageReference the image reference to store the metadata for
   * @param manifestTemplate the manifest
   */
  void writeMetadata(ImageReference imageReference, V21ManifestTemplate manifestTemplate)
      throws IOException {
    Path imageDirectory = cacheStorageFiles.getImageDirectory(imageReference);
    Files.createDirectories(imageDirectory);

    try (LockFile ignored1 = LockFile.lock(imageDirectory.resolve("lock"))) {
      writeMetadata(manifestTemplate, imageDirectory.resolve("manifest.json"));
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
    Path temporaryLayerFile = cacheStorageFiles.getTemporaryLayerFile(layerDirectory);

    BlobDescriptor layerBlobDescriptor;
    try (OutputStream fileOutputStream =
        new BufferedOutputStream(Files.newOutputStream(temporaryLayerFile))) {
      layerBlobDescriptor = compressedLayerBlob.writeTo(fileOutputStream);
    }

    // Gets the diff ID.
    DescriptorDigest layerDiffId = getDiffIdByDecompressingFile(temporaryLayerFile);

    // Renames the temporary layer file to the correct filename.
    Path layerFile = layerDirectory.resolve(cacheStorageFiles.getLayerFilename(layerDiffId));
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
    Path temporaryLayerFile = cacheStorageFiles.getTemporaryLayerFile(layerDirectory);

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
      BlobDescriptor blobDescriptor = compressedDigestOutputStream.computeDigest();
      DescriptorDigest layerDigest = blobDescriptor.getDigest();
      long layerSize = blobDescriptor.getSize();

      // Renames the temporary layer file to the correct filename.
      Path layerFile = layerDirectory.resolve(cacheStorageFiles.getLayerFilename(layerDiffId));
      moveIfDoesNotExist(temporaryLayerFile, layerFile);

      return new WrittenLayer(layerDigest, layerDiffId, layerSize);
    }
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
    Path selectorFile = cacheStorageFiles.getSelectorFile(selector);

    // Creates the selectors directory if it doesn't exist.
    Files.createDirectories(selectorFile.getParent());

    // Writes the selector to a temporary file and then moves the file to the intended location.
    Path temporarySelectorFile = Files.createTempFile(null, null);
    temporarySelectorFile.toFile().deleteOnExit();
    try (OutputStream fileOut = FileOperations.newLockingOutputStream(temporarySelectorFile)) {
      fileOut.write(layerDigest.getHash().getBytes(StandardCharsets.UTF_8));
    }

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
