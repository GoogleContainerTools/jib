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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.cloud.tools.jib.api.DescriptorDigest;
import com.google.cloud.tools.jib.api.ImageReference;
import com.google.cloud.tools.jib.blob.Blobs;
import com.google.cloud.tools.jib.filesystem.LockFile;
import com.google.cloud.tools.jib.image.json.ContainerConfigurationTemplate;
import com.google.cloud.tools.jib.image.json.ManifestAndConfig;
import com.google.cloud.tools.jib.image.json.ManifestTemplate;
import com.google.cloud.tools.jib.image.json.OciManifestTemplate;
import com.google.cloud.tools.jib.image.json.V21ManifestTemplate;
import com.google.cloud.tools.jib.image.json.V22ManifestTemplate;
import com.google.cloud.tools.jib.json.JsonTemplateMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Reads from the default cache storage engine. */
class CacheStorageReader {

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
   * Retrieves the cached manifest and container configuration for an image reference.
   *
   * @param imageReference the image reference
   * @return the manifest and container configuration for the image reference, if found
   * @throws IOException if an I/O exception occurs
   * @throws CacheCorruptedException if the cache is corrupted
   */
  List<ManifestAndConfig> retrieveMetadata(ImageReference imageReference)
      throws IOException, CacheCorruptedException {
    Path imageDirectory = cacheStorageFiles.getImageDirectory(imageReference);
    Path metadataPath = imageDirectory.resolve("manifests_configs.json");
    if (!Files.exists(metadataPath)) {
      return Collections.emptyList();
    }

    List<ManifestAndConfig> manifestsAndConfigs = new ArrayList<>();
    try (LockFile ignored = LockFile.lock(imageDirectory.resolve("lock"));
        InputStream jsonStream = Files.newInputStream(metadataPath)) {
      for (MetadataEntryTemplate metadataEntry :
          JsonTemplateMapper.readListOfJson(jsonStream, MetadataEntryTemplate.class)) {
        manifestsAndConfigs.add(parseManifestAndConfig(metadataEntry));
      }
    }
    return manifestsAndConfigs;
  }

  private ManifestAndConfig parseManifestAndConfig(MetadataEntryTemplate metadataEntry)
      throws IOException, CacheCorruptedException {
    // TODO: Consolidate with AbstractManifestPuller. However, doing so shouldn't destroy package
    // hierarchy. (RegistryClient sits lower in the hierarchy and isolated.)
    ObjectNode manifestNode =
        new ObjectMapper().readValue(metadataEntry.getManfiest(), ObjectNode.class);
    if (!manifestNode.has("schemaVersion")) {
      throw new CacheCorruptedException(
          cacheStorageFiles.getCacheDirectory(), "Cannot find field 'schemaVersion' in manifest");
    }

    int schemaVersion = manifestNode.get("schemaVersion").asInt(-1);
    if (schemaVersion == -1) {
      throw new CacheCorruptedException(
          cacheStorageFiles.getCacheDirectory(),
          "'schemaVersion' field is not an integer in manifest");
    }

    if (schemaVersion == 1) {
      return new ManifestAndConfig(
          JsonTemplateMapper.readJson(metadataEntry.getManfiest(), V21ManifestTemplate.class),
          null);
    }
    if (schemaVersion == 2) {
      // 'schemaVersion' of 2 can be either Docker V2.2 or OCI.
      String mediaType = manifestNode.get("mediaType").asText();

      ManifestTemplate manifestTemplate;
      if (V22ManifestTemplate.MANIFEST_MEDIA_TYPE.equals(mediaType)) {
        manifestTemplate =
            JsonTemplateMapper.readJson(metadataEntry.getManfiest(), V22ManifestTemplate.class);
      } else if (OciManifestTemplate.MANIFEST_MEDIA_TYPE.equals(mediaType)) {
        manifestTemplate =
            JsonTemplateMapper.readJson(metadataEntry.getManfiest(), OciManifestTemplate.class);
      } else {
        throw new CacheCorruptedException(
            cacheStorageFiles.getCacheDirectory(), "Unknown manifest mediaType: " + mediaType);
      }

      ContainerConfigurationTemplate config =
          JsonTemplateMapper.readJson(
              metadataEntry.getContainerConfig(), ContainerConfigurationTemplate.class);

      return new ManifestAndConfig(manifestTemplate, config);
    }
    throw new CacheCorruptedException(
        cacheStorageFiles.getCacheDirectory(),
        "Unknown schemaVersion in manifest: " + schemaVersion + " - only 1 and 2 are supported");
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
   * @param diffId the diff ID
   * @return the {@link CachedLayer} referenced by the diff ID, if found
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
