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

import com.google.cloud.tools.jib.api.DescriptorDigest;
import com.google.cloud.tools.jib.api.ImageReference;
import com.google.cloud.tools.jib.blob.Blobs;
import com.google.cloud.tools.jib.filesystem.LockFile;
import com.google.cloud.tools.jib.image.json.ContainerConfigurationTemplate;
import com.google.cloud.tools.jib.image.json.ImageMetadataTemplate;
import com.google.cloud.tools.jib.image.json.ManifestAndConfigTemplate;
import com.google.cloud.tools.jib.json.JsonTemplateMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Verify;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Reads from the default cache storage engine. */
class CacheStorageReader {

  @VisibleForTesting
  static void verifyImageMetadata(ImageMetadataTemplate metadata, Path metadataCacheDirectory)
      throws CacheCorruptedException {
    if (metadata.getManifestsAndConfigs().isEmpty()) {
      throw new CacheCorruptedException(metadataCacheDirectory, "Manifest cache empty");
    }

    List<ManifestAndConfigTemplate> manifestsAndConfigs = metadata.getManifestsAndConfigs();
    if (metadata.getManifestList() == null && manifestsAndConfigs.size() != 1) {
      throw new CacheCorruptedException(metadataCacheDirectory, "Manifest list missing");
    }
    if (manifestsAndConfigs.stream().anyMatch(entry -> entry.getManifest() == null)) {
      throw new CacheCorruptedException(metadataCacheDirectory, "Manifest(s) missing");
    }

    int schemaVersion =
        Verify.verifyNotNull(manifestsAndConfigs.get(0).getManifest()).getSchemaVersion();
    if (schemaVersion == 1) {
      if (metadata.getManifestList() != null
          || manifestsAndConfigs.stream().anyMatch(entry -> entry.getConfig() != null)) {
        throw new CacheCorruptedException(metadataCacheDirectory, "Schema 1 manifests corrupted");
      }
    } else if (schemaVersion == 2) {
      if (manifestsAndConfigs.stream().anyMatch(entry -> entry.getConfig() == null)) {
        throw new CacheCorruptedException(metadataCacheDirectory, "Schema 2 manifests corrupted");
      }
    } else {
      throw new CacheCorruptedException(
          metadataCacheDirectory,
          "Unknown schemaVersion in manifest: " + schemaVersion + " - only 1 and 2 are supported");
    }
  }

  private final CacheStorageFiles cacheStorageFiles;

  CacheStorageReader(CacheStorageFiles cacheStorageFiles) {
    this.cacheStorageFiles = cacheStorageFiles;
  }

  /**
   * Lists all the layer digests stored.
   *
   * @return the list of layer digests
   * @throws CacheCorruptedException if the cache was found to be corrupted
   * @throws IOException if an I/O exception occurs
   */
  Set<DescriptorDigest> fetchDigests() throws IOException, CacheCorruptedException {
    try (Stream<Path> layerDirectories = Files.list(cacheStorageFiles.getLayersDirectory())) {
      List<Path> layerDirectoriesList = layerDirectories.collect(Collectors.toList());
      Set<DescriptorDigest> layerDigests = new HashSet<>(layerDirectoriesList.size());
      for (Path layerDirectory : layerDirectoriesList) {
        try {
          layerDigests.add(DescriptorDigest.fromHash(layerDirectory.getFileName().toString()));

        } catch (DigestException ex) {
          throw new CacheCorruptedException(
              cacheStorageFiles.getCacheDirectory(),
              "Found non-digest file in layers directory",
              ex);
        }
      }
      return layerDigests;
    }
  }

  /**
   * Retrieves the cached manifest and container configuration for an image reference. aaa
   *
   * @param imageReference the image reference
   * @return the manifest and container configuration for the image reference, if found
   * @throws IOException if an I/O exception occurs
   * @throws CacheCorruptedException if the cache is corrupted
   */
  Optional<ImageMetadataTemplate> retrieveMetadata(ImageReference imageReference)
      throws IOException, CacheCorruptedException {
    Path imageDirectory = cacheStorageFiles.getImageDirectory(imageReference);
    Path metadataPath = imageDirectory.resolve("manifests_configs.json");
    if (!Files.exists(metadataPath)) {
      return Optional.empty();
    }

    ImageMetadataTemplate metadata;
    try (LockFile ignored = LockFile.lock(imageDirectory.resolve("lock"))) {
      metadata = JsonTemplateMapper.readJsonFromFile(metadataPath, ImageMetadataTemplate.class);
    }
    verifyImageMetadata(metadata, imageDirectory);
    return Optional.of(metadata);
  }

  /**
   * Retrieves the {@link CachedLayer} for the layer with digest {@code layerDigest}.
   *
   * @param layerDigest the layer digest
   * @return the {@link CachedLayer} referenced by the layer digest, if found
   * @throws CacheCorruptedException if the cache was found to be corrupted
   * @throws IOException if an I/O exception occurs
   */
  Optional<CachedLayer> retrieve(DescriptorDigest layerDigest)
      throws IOException, CacheCorruptedException {
    Path layerDirectory = cacheStorageFiles.getLayerDirectory(layerDigest);
    if (!Files.exists(layerDirectory)) {
      return Optional.empty();
    }

    try (Stream<Path> files = Files.list(layerDirectory)) {
      List<Path> layerFiles =
          files.filter(CacheStorageFiles::isLayerFile).collect(Collectors.toList());
      if (layerFiles.size() != 1) {
        throw new CacheCorruptedException(
            cacheStorageFiles.getCacheDirectory(),
            "No or multiple layer files found for layer hash "
                + layerDigest.getHash()
                + " in directory: "
                + layerDirectory);
      }

      Path layerFile = layerFiles.get(0);
      return Optional.of(
          CachedLayer.builder()
              .setLayerDigest(layerDigest)
              .setLayerSize(Files.size(layerFile))
              .setLayerBlob(Blobs.from(layerFile))
              .setLayerDiffId(cacheStorageFiles.getDigestFromFilename(layerFile))
              .build());
    }
  }

  /**
   * Retrieves the {@link CachedLayer} for the local base image layer with the given diff ID.
   *
   * @param diffId the diff ID @)return the {@link CachedLayer} referenced by the diff ID, if found
   * @throws CacheCorruptedException if the cache was found to be corrupted
   * @throws IOException if an I/O exception occurs
   */
  Optional<CachedLayer> retrieveTarLayer(DescriptorDigest diffId)
      throws IOException, CacheCorruptedException {
    Path layerDirectory = cacheStorageFiles.getLocalDirectory().resolve(diffId.getHash());
    if (!Files.exists(layerDirectory)) {
      return Optional.empty();
    }

    try (Stream<Path> files = Files.list(layerDirectory)) {
      List<Path> layerFiles =
          files.filter(CacheStorageFiles::isLayerFile).collect(Collectors.toList());
      if (layerFiles.size() != 1) {
        throw new CacheCorruptedException(
            cacheStorageFiles.getCacheDirectory(),
            "No or multiple layer files found for layer hash "
                + diffId.getHash()
                + " in directory: "
                + layerDirectory);
      }

      Path layerFile = layerFiles.get(0);
      return Optional.of(
          CachedLayer.builder()
              .setLayerDigest(cacheStorageFiles.getDigestFromFilename(layerFile))
              .setLayerSize(Files.size(layerFile))
              .setLayerBlob(Blobs.from(layerFile))
              .setLayerDiffId(diffId)
              .build());
    }
  }

  /**
   * Retrieves the {@link ContainerConfigurationTemplate} for the image with the given image ID.
   *
   * @param imageId the image ID
   * @return the {@link ContainerConfigurationTemplate} referenced by the image ID, if found
   * @throws IOException if an I/O exception occurs
   */
  Optional<ContainerConfigurationTemplate> retrieveLocalConfig(DescriptorDigest imageId)
      throws IOException {
    Path configPath =
        cacheStorageFiles.getLocalDirectory().resolve("config").resolve(imageId.getHash());
    if (!Files.exists(configPath)) {
      return Optional.empty();
    }

    ContainerConfigurationTemplate config =
        JsonTemplateMapper.readJsonFromFile(configPath, ContainerConfigurationTemplate.class);
    return Optional.of(config);
  }

  /**
   * Retrieves the layer digest selected by the {@code selector}.
   *
   * @param selector the selector
   * @return the layer digest {@code selector} selects, if found
   * @throws CacheCorruptedException if the selector file contents was not a valid layer digest
   * @throws IOException if an I/O exception occurs
   */
  Optional<DescriptorDigest> select(DescriptorDigest selector)
      throws CacheCorruptedException, IOException {
    Path selectorFile = cacheStorageFiles.getSelectorFile(selector);
    if (!Files.exists(selectorFile)) {
      return Optional.empty();
    }

    String selectorFileContents =
        new String(Files.readAllBytes(selectorFile), StandardCharsets.UTF_8);
    try {
      return Optional.of(DescriptorDigest.fromHash(selectorFileContents));

    } catch (DigestException ex) {
      throw new CacheCorruptedException(
          cacheStorageFiles.getCacheDirectory(),
          "Expected valid layer digest as contents of selector file `"
              + selectorFile
              + "` for selector `"
              + selector.getHash()
              + "`, but got: "
              + selectorFileContents);
    }
  }
}
