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

import com.google.cloud.tools.jib.api.CacheDirectoryCreationException;
import com.google.cloud.tools.jib.api.DescriptorDigest;
import com.google.cloud.tools.jib.api.ImageReference;
import com.google.cloud.tools.jib.api.buildplan.FileEntry;
import com.google.cloud.tools.jib.blob.Blob;
import com.google.cloud.tools.jib.image.json.BuildableManifestTemplate;
import com.google.cloud.tools.jib.image.json.ContainerConfigurationTemplate;
import com.google.cloud.tools.jib.image.json.ManifestAndConfigTemplate;
import com.google.cloud.tools.jib.image.json.V21ManifestTemplate;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import javax.annotation.concurrent.Immutable;

/**
 * Cache for storing data to be shared between Jib executions.
 *
 * <p>This class is immutable and safe to use across threads.
 */
@Immutable
public class Cache {

  /**
   * Initializes the cache using {@code cacheDirectory} for storage.
   *
   * @param cacheDirectory the directory for the cache. Creates the directory if it does not exist.
   * @return a new {@link Cache}
   * @throws CacheDirectoryCreationException if an I/O exception occurs when creating cache
   *     directory
   */
  public static Cache withDirectory(Path cacheDirectory) throws CacheDirectoryCreationException {
    try {
      Files.createDirectories(cacheDirectory);
    } catch (IOException ex) {
      throw new CacheDirectoryCreationException(ex);
    }
    return new Cache(new CacheStorageFiles(cacheDirectory));
  }

  private final CacheStorageWriter cacheStorageWriter;
  private final CacheStorageReader cacheStorageReader;

  private Cache(CacheStorageFiles cacheStorageFiles) {
    cacheStorageWriter = new CacheStorageWriter(cacheStorageFiles);
    cacheStorageReader = new CacheStorageReader(cacheStorageFiles);
  }

  /**
   * Saves a manifest and container configuration for a V2.2 or OCI image.
   *
   * @param imageReference the image reference to save the manifest and container configuration for
   * @param manifestTemplate the V2.2 or OCI manifest
   * @param containerConfigurationTemplate the container configuration
   * @throws IOException if an I/O exception occurs
   */
  public void writeMetadata(
      ImageReference imageReference,
      BuildableManifestTemplate manifestTemplate,
      ContainerConfigurationTemplate containerConfigurationTemplate)
      throws IOException {
    cacheStorageWriter.writeMetadata(
        imageReference, manifestTemplate, containerConfigurationTemplate);
  }

  /**
   * Saves a V2.1 image manifest.
   *
   * @param imageReference the image reference to save the manifest and container configuration for
   * @param manifestTemplate the V2.1 manifest
   * @throws IOException if an I/O exception occurs
   */
  public void writeMetadata(ImageReference imageReference, V21ManifestTemplate manifestTemplate)
      throws IOException {
    cacheStorageWriter.writeMetadata(imageReference, manifestTemplate);
  }

  /**
   * Saves a cache entry with a compressed layer {@link Blob}. Use {@link
   * #writeUncompressedLayer(Blob, ImmutableList)} to save a cache entry with an uncompressed layer
   * {@link Blob} and include a selector.
   *
   * @param compressedLayerBlob the compressed layer {@link Blob}
   * @return the {@link CachedLayer} for the written layer
   * @throws IOException if an I/O exception occurs
   */
  public CachedLayer writeCompressedLayer(Blob compressedLayerBlob) throws IOException {
    return cacheStorageWriter.writeCompressed(compressedLayerBlob);
  }

  /**
   * Saves a cache entry with an uncompressed layer {@link Blob} and an additional selector digest.
   * Use {@link #writeCompressedLayer(Blob)} to save a compressed layer {@link Blob}.
   *
   * @param uncompressedLayerBlob the layer {@link Blob}
   * @param layerEntries the layer entries that make up the layer
   * @return the {@link CachedLayer} for the written layer
   * @throws IOException if an I/O exception occurs
   */
  public CachedLayer writeUncompressedLayer(
      Blob uncompressedLayerBlob, ImmutableList<FileEntry> layerEntries) throws IOException {
    return cacheStorageWriter.writeUncompressed(
        uncompressedLayerBlob, LayerEntriesSelector.generateSelector(layerEntries));
  }

  /**
   * Caches a layer that was extracted from a local base image, and names the file using the
   * provided diff id.
   *
   * @param diffId the diff id
   * @param compressedBlob the compressed layer blob
   * @return the {@link CachedLayer} for the written layer
   * @throws IOException if an I/O exception occurs
   */
  public CachedLayer writeTarLayer(DescriptorDigest diffId, Blob compressedBlob)
      throws IOException {
    return cacheStorageWriter.writeTarLayer(diffId, compressedBlob);
  }

  /**
   * Writes a container configuration to {@code (cache directory)/local/config/(image id)}. An image
   * ID is a SHA hash of a container configuration JSON. The value is also shown as IMAGE ID in
   * {@code docker images}.
   *
   * <p>Note: the {@code imageID} to the {@code containerConfiguration} is a one-way relationship;
   * there is no guarantee that {@code containerConfiguration}'s SHA will be {@code imageID}, since
   * the original container configuration is being rewritten here rather than being moved.
   *
   * @param imageId the ID of the image to store the container configuration for
   * @param containerConfiguration the container configuration
   * @throws IOException if an I/O exception occurs
   */
  public void writeLocalConfig(
      DescriptorDigest imageId, ContainerConfigurationTemplate containerConfiguration)
      throws IOException {
    cacheStorageWriter.writeLocalConfig(imageId, containerConfiguration);
  }

  /**
   * Retrieves the cached manifest and container configuration for an image reference.
   *
   * @param imageReference the image reference
   * @return the manifest and container configuration for the image reference, if found
   * @throws IOException if an I/O exception occurs
   * @throws CacheCorruptedException if the cache is corrupted
   */
  public Optional<ManifestAndConfigTemplate> retrieveMetadata(ImageReference imageReference)
      throws IOException, CacheCorruptedException {
    return cacheStorageReader.retrieveMetadata(imageReference);
  }

  /**
   * Retrieves the {@link CachedLayer} that was built from the {@code layerEntries}.
   *
   * @param layerEntries the layer entries to match against
   * @return a {@link CachedLayer} that was built from {@code layerEntries}, if found
   * @throws IOException if an I/O exception occurs
   * @throws CacheCorruptedException if the cache is corrupted
   */
  public Optional<CachedLayer> retrieve(ImmutableList<FileEntry> layerEntries)
      throws IOException, CacheCorruptedException {
    Optional<DescriptorDigest> optionalSelectedLayerDigest =
        cacheStorageReader.select(LayerEntriesSelector.generateSelector(layerEntries));
    if (!optionalSelectedLayerDigest.isPresent()) {
      return Optional.empty();
    }

    return cacheStorageReader.retrieve(optionalSelectedLayerDigest.get());
  }

  /**
   * Retrieves the {@link CachedLayer} for the layer with digest {@code layerDigest}.
   *
   * @param layerDigest the layer digest
   * @return the {@link CachedLayer} referenced by the layer digest, if found
   * @throws CacheCorruptedException if the cache was found to be corrupted
   * @throws IOException if an I/O exception occurs
   */
  public Optional<CachedLayer> retrieve(DescriptorDigest layerDigest)
      throws IOException, CacheCorruptedException {
    return cacheStorageReader.retrieve(layerDigest);
  }

  /**
   * Retrieves a {@link CachedLayer} for a local base image layer with the given diff id.
   *
   * @param diffId the diff id
   * @return the {@link CachedLayer} with the given diff id
   * @throws CacheCorruptedException if the cache was found to be corrupted
   * @throws IOException if an I/O exception occurs
   */
  public Optional<CachedLayer> retrieveTarLayer(DescriptorDigest diffId)
      throws IOException, CacheCorruptedException {
    return cacheStorageReader.retrieveTarLayer(diffId);
  }

  /**
   * Retrieves the {@link ContainerConfigurationTemplate} for the image saved from the given image
   * ID. An image ID is a SHA hash of a container configuration JSON. The value is also shown as
   * IMAGE ID in {@code docker images}.
   *
   * <p>Note: the {@code imageID} is only used to find the {@code containerConfiguration}, and is
   * not necessarily the actual SHA of {@code containerConfiguration}. There is no guarantee that
   * {@code containerConfiguration}'s SHA will be {@code imageID}, since the saved container
   * configuration is not a direct copy of the base image's original configuration.
   *
   * @param imageId the image ID
   * @return the {@link ContainerConfigurationTemplate} referenced by the image ID, if found
   * @throws IOException if an I/O exception occurs
   */
  public Optional<ContainerConfigurationTemplate> retrieveLocalConfig(DescriptorDigest imageId)
      throws IOException {
    return cacheStorageReader.retrieveLocalConfig(imageId);
  }
}
