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

import com.google.cloud.tools.jib.blob.BlobDescriptor;
import com.google.cloud.tools.jib.hash.CountingDigestOutputStream;
import com.google.cloud.tools.jib.image.DescriptorDigest;
import com.google.cloud.tools.jib.image.ReproducibleLayerBuilder;
import com.google.cloud.tools.jib.image.UnwrittenLayer;
import com.google.common.io.ByteStreams;
import com.google.common.io.CountingOutputStream;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** Writes {@link UnwrittenLayer}s to the cache. */
public class CacheWriter {

  private final Cache cache;

  public CacheWriter(Cache cache) {
    this.cache = cache;
  }

  /**
   * Builds an {@link UnwrittenLayer} from a {@link ReproducibleLayerBuilder} and compresses and
   * writes the {@link UnwrittenLayer}'s uncompressed layer content BLOB to cache.
   *
   * @param reproducibleLayerBuilder the layer builder
   * @return the cached layer with accompanying {@link LayerMetadata}
   * @throws IOException if writing the layer to file fails
   */
  public CachedLayerWithMetadata writeLayer(ReproducibleLayerBuilder reproducibleLayerBuilder)
      throws IOException {
    UnwrittenLayer unwrittenLayer = reproducibleLayerBuilder.build();

    // Writes to a temporary file first because the UnwrittenLayer needs to be written first to
    // obtain its digest.
    Path tempLayerFile = Files.createTempFile(cache.getCacheDirectory(), null, null);
    // TODO: Find a way to do this with java.nio.file
    tempLayerFile.toFile().deleteOnExit();

    // Writes the UnwrittenLayer layer BLOB to a file to convert into a CachedLayer.
    try (CountingDigestOutputStream compressedDigestOutputStream =
        new CountingDigestOutputStream(
            new BufferedOutputStream(Files.newOutputStream(tempLayerFile)))) {
      // Writes the layer with GZIP compression. The original bytes are captured as the layer's
      // diff ID and the bytes outputted from the GZIP compression are captured as the layer's
      // content descriptor.
      GZIPOutputStream compressorStream = new GZIPOutputStream(compressedDigestOutputStream);
      DescriptorDigest diffId = unwrittenLayer.getBlob().writeTo(compressorStream).getDigest();

      // The GZIPOutputStream must be closed in order to write out the remaining compressed data.
      compressorStream.close();
      BlobDescriptor compressedBlobDescriptor = compressedDigestOutputStream.toBlobDescriptor();

      // Renames the temporary layer file to the correct filename.
      Path layerFile = getLayerFile(compressedBlobDescriptor.getDigest());
      try {
        Files.move(tempLayerFile, layerFile);
      } catch (FileAlreadyExistsException ignored) {
        // If the file already exists, we skip renaming and use the existing file. This happens if a
        // new layer happens to have the same content as a previously-cached layer.
        //
        // Do not attempt to remove the try-catch block with the idea of checking file existence
        // before moving; there can be concurrent file moves.
      }

      CachedLayer cachedLayer = new CachedLayer(layerFile, compressedBlobDescriptor, diffId);
      LayerMetadata layerMetadata =
          LayerMetadata.from(
              reproducibleLayerBuilder.getLayerEntries(), FileTime.from(Instant.now()));
      return new CachedLayerWithMetadata(cachedLayer, layerMetadata);
    }
  }

  /**
   * @param layerDigest the written layer's digest.
   * @return the {@link CountingOutputStream} to write to to cache a layer with the specified
   *     compressed digest.
   * @throws IOException if writing to the layer file's output stream fails.
   */
  public CountingOutputStream getLayerOutputStream(DescriptorDigest layerDigest)
      throws IOException {
    Path layerFile = getLayerFile(layerDigest);
    return new CountingOutputStream(new BufferedOutputStream(Files.newOutputStream(layerFile)));
  }

  /**
   * Gets the layer that was cached by writing the contents to a file in the cache. The returned
   * {@link CachedLayer} should be added to the cache metadata.
   *
   * @param layerSize the size of the layer
   * @param layerDigest the digest of the layer to retrieve
   * @return a {@link CachedLayer} from a layer digest and the {@link CountingOutputStream} the
   *     layer BLOB was written to
   * @throws IOException if closing the output stream or getting the layer diff ID fails
   */
  public CachedLayer getCachedLayer(long layerSize, DescriptorDigest layerDigest)
      throws IOException {
    Path layerFile = getLayerFile(layerDigest);

    return new CachedLayer(
        layerFile, new BlobDescriptor(layerSize, layerDigest), getDiffId(layerFile));
  }

  /**
   * @param compressedDigest the compressed digest of the layer to find.
   * @return the path to the file for the layer with the specified compressed digest.
   */
  private Path getLayerFile(DescriptorDigest compressedDigest) {
    return CacheFiles.getLayerFile(cache.getCacheDirectory(), compressedDigest);
  }

  /**
   * @param layerFile the layer content file.
   * @return the layer diff ID by decompressing the layer content file.
   * @throws IOException if reading the layer file fails.
   */
  private DescriptorDigest getDiffId(Path layerFile) throws IOException {
    CountingDigestOutputStream diffIdCaptureOutputStream =
        new CountingDigestOutputStream(ByteStreams.nullOutputStream());
    try (InputStream fileInputStream = new BufferedInputStream(Files.newInputStream(layerFile));
        GZIPInputStream decompressorStream = new GZIPInputStream(fileInputStream)) {
      ByteStreams.copy(decompressorStream, diffIdCaptureOutputStream);
    }
    return diffIdCaptureOutputStream.toBlobDescriptor().getDigest();
  }
}
