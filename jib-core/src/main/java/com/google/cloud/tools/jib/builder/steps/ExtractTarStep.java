/*
 * Copyright 2019 Google LLC.
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

package com.google.cloud.tools.jib.builder.steps;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.tools.jib.api.DescriptorDigest;
import com.google.cloud.tools.jib.blob.Blob;
import com.google.cloud.tools.jib.blob.BlobDescriptor;
import com.google.cloud.tools.jib.blob.Blobs;
import com.google.cloud.tools.jib.builder.steps.ExtractTarStep.LocalImage;
import com.google.cloud.tools.jib.cache.Cache;
import com.google.cloud.tools.jib.cache.CacheCorruptedException;
import com.google.cloud.tools.jib.cache.CachedLayer;
import com.google.cloud.tools.jib.configuration.BuildConfiguration;
import com.google.cloud.tools.jib.docker.json.DockerManifestEntryTemplate;
import com.google.cloud.tools.jib.filesystem.FileOperations;
import com.google.cloud.tools.jib.image.Image;
import com.google.cloud.tools.jib.image.LayerCountMismatchException;
import com.google.cloud.tools.jib.image.json.BadContainerConfigurationFormatException;
import com.google.cloud.tools.jib.image.json.ContainerConfigurationTemplate;
import com.google.cloud.tools.jib.image.json.JsonToImageTranslator;
import com.google.cloud.tools.jib.image.json.V22ManifestTemplate;
import com.google.cloud.tools.jib.json.JsonTemplateMapper;
import com.google.cloud.tools.jib.tar.TarExtractor;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** Extracts a tar file base image. */
public class ExtractTarStep implements Callable<LocalImage> {

  /** Contains an {@link Image} and its layers. * */
  static class LocalImage {
    final Image baseImage;
    final List<PreparedLayer> layers;

    LocalImage(Image baseImage, List<PreparedLayer> layers) {
      this.baseImage = baseImage;
      this.layers = layers;
    }
  }

  /**
   * Checks the first two bytes of a file to see if it has been gzipped.
   *
   * @param path the file to check
   * @return {@code true} if the file is gzipped, {@code false} if not
   * @throws IOException if reading the file fails
   * @see <a href="http://www.zlib.org/rfc-gzip.html#file-format">GZIP file format</a>
   */
  @VisibleForTesting
  static boolean isGzipped(Path path) throws IOException {
    try (InputStream inputStream = Files.newInputStream(path)) {
      inputStream.mark(2);
      int magic = (inputStream.read() & 0xff) | ((inputStream.read() << 8) & 0xff00);
      return magic == GZIPInputStream.GZIP_MAGIC;
    }
  }

  private final Path tarPath;
  private final Path destination;
  private final BuildConfiguration buildConfiguration;

  ExtractTarStep(Path tarPath, Path destination, BuildConfiguration buildConfiguration) {
    this.tarPath = tarPath;
    this.destination = destination;
    this.buildConfiguration = buildConfiguration;
  }

  @Override
  public LocalImage call()
      throws IOException, LayerCountMismatchException, BadContainerConfigurationFormatException,
          CacheCorruptedException {
    Cache cache = buildConfiguration.getBaseImageLayersCache();

    Files.createDirectories(destination);
    FileOperations.deleteRecursiveOnExit(destination);
    TarExtractor.extract(tarPath, destination);

    InputStream manifestStream = Files.newInputStream(destination.resolve("manifest.json"));
    DockerManifestEntryTemplate loadManifest =
        new ObjectMapper()
            .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true)
            .readValue(manifestStream, DockerManifestEntryTemplate[].class)[0];
    manifestStream.close();
    ContainerConfigurationTemplate configurationTemplate =
        JsonTemplateMapper.readJsonFromFile(
            destination.resolve(loadManifest.getConfig()), ContainerConfigurationTemplate.class);

    List<String> layerFiles = loadManifest.getLayerFiles();
    if (configurationTemplate.getLayerCount() != layerFiles.size()) {
      throw new LayerCountMismatchException(
          "Invalid base image format: manifest contains "
              + layerFiles.size()
              + " layers, but container configuration contains "
              + configurationTemplate.getLayerCount()
              + " layers");
    }

    // Check the first layer to see if the layers are compressed already. 'docker save' output is
    // uncompressed, but a jib-built tar has compressed layers.
    boolean layersAreCompressed =
        layerFiles.size() > 0 && isGzipped(destination.resolve(layerFiles.get(0)));

    // Process layer blobs
    // TODO: Optimize; compressing/calculating layer digests is slow
    List<PreparedLayer> layers = new ArrayList<>();
    V22ManifestTemplate v22Manifest = new V22ManifestTemplate();
    for (int index = 0; index < layerFiles.size(); index++) {
      Path file = destination.resolve(layerFiles.get(index));
      DescriptorDigest diffId = configurationTemplate.getLayerDiffId(index);

      // Compress layers if necessary and calculate the digest/size
      Blob blob = Blobs.from(file);
      if (!layersAreCompressed) {
        Path compressedFile = destination.resolve(layerFiles.get(index) + ".compressed");
        try (GZIPOutputStream compressorStream =
            new GZIPOutputStream(Files.newOutputStream(compressedFile))) {
          blob.writeTo(compressorStream);
        }
        blob = Blobs.from(compressedFile);
      }

      CachedLayer layer = cache.retrieveTarLayer(diffId).orElse(cache.writeTarLayer(diffId, blob));
      layers.add(new PreparedLayer.Builder(layer).build());
      v22Manifest.addLayer(layer.getSize(), layer.getDigest());
    }

    BlobDescriptor configDescriptor =
        Blobs.from(configurationTemplate).writeTo(ByteStreams.nullOutputStream());
    v22Manifest.setContainerConfiguration(configDescriptor.getSize(), configDescriptor.getDigest());
    Image image = JsonToImageTranslator.toImage(v22Manifest, configurationTemplate);
    return new LocalImage(image, layers);
  }
}
