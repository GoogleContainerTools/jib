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
import com.google.cloud.tools.jib.blob.Blob;
import com.google.cloud.tools.jib.blob.BlobDescriptor;
import com.google.cloud.tools.jib.blob.Blobs;
import com.google.cloud.tools.jib.builder.steps.ExtractTarStep.LocalImage;
import com.google.cloud.tools.jib.cache.CachedLayer;
import com.google.cloud.tools.jib.docker.json.DockerManifestEntryTemplate;
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
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.zip.GZIPInputStream;

/** Extracts a tar file base image. */
public class ExtractTarStep implements Callable<LocalImage> {

  static class LocalImage {
    Image baseImage;
    List<PreparedLayer> layers;

    LocalImage(Image baseImage, List<PreparedLayer> layers) {
      this.baseImage = baseImage;
      this.layers = layers;
    }
  }

  @VisibleForTesting
  static boolean isGzipped(Path path) throws IOException {
    try (InputStream inputStream = new BufferedInputStream(Files.newInputStream(path))) {
      inputStream.mark(2);
      int magic = (inputStream.read() & 0xff) | ((inputStream.read() << 8) & 0xff00);
      return magic == GZIPInputStream.GZIP_MAGIC;
    }
  }

  private final Path tarPath;
  private final Path destination;

  ExtractTarStep(Path tarPath, Path destination) {
    this.tarPath = tarPath;
    this.destination = destination;
  }

  @Override
  public LocalImage call()
      throws IOException, LayerCountMismatchException, BadContainerConfigurationFormatException {
    TarExtractor.extract(tarPath, destination);
    DockerManifestEntryTemplate loadManifest =
        new ObjectMapper()
            .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true)
            .readValue(
                Files.newInputStream(destination.resolve("manifest.json")),
                DockerManifestEntryTemplate[].class)[0];
    ContainerConfigurationTemplate configuration =
        JsonTemplateMapper.readJsonFromFile(
            destination.resolve(loadManifest.getConfig()), ContainerConfigurationTemplate.class);

    List<String> layerFiles = loadManifest.getLayerFiles();
    if (configuration.getLayerCount() != layerFiles.size()) {
      throw new LayerCountMismatchException(
          "Invalid base image format: manifest contains "
              + layerFiles.size()
              + " layers, but container configuration contains "
              + configuration.getLayerCount()
              + " layers");
    }

    // Check the first layer to see if the layers are compressed already. 'docker save' output is
    // uncompressed, but a jib-built tar has compressed layers.
    boolean layersAreCompressed =
        layerFiles.size() > 0 && isGzipped(destination.resolve(layerFiles.get(0)));

    // Process layer blobs
    // TODO: Optimize; compressing/calculating layer digests is slow
    List<PreparedLayer> layers = new ArrayList<>();
    V22ManifestTemplate newManifest = new V22ManifestTemplate();
    for (int index = 0; index < layerFiles.size(); index++) {
      Path file = destination.resolve(layerFiles.get(index));

      // Compress layers if necessary and calculate the digest/size
      Blob blob = layersAreCompressed ? Blobs.from(file) : Blobs.compress(Blobs.from(file));
      BlobDescriptor blobDescriptor = blob.writeTo(ByteStreams.nullOutputStream());

      // 'manifest' contains the layer files in the same order as the diff ids in 'configuration',
      // so we don't need to recalculate those.
      // https://containers.gitbook.io/build-containers-the-hard-way/#docker-load-format
      CachedLayer layer =
          CachedLayer.builder()
              .setLayerBlob(blob)
              .setLayerDigest(blobDescriptor.getDigest())
              .setLayerSize(blobDescriptor.getSize())
              .setLayerDiffId(configuration.getLayerDiffId(index))
              .build();

      // TODO: Check blob existence on target registry (online mode only)

      layers.add(new PreparedLayer.Builder(layer).build());
      newManifest.addLayer(blobDescriptor.getSize(), blobDescriptor.getDigest());
    }

    BlobDescriptor configDescriptor =
        Blobs.from(configuration).writeTo(ByteStreams.nullOutputStream());
    newManifest.setContainerConfiguration(configDescriptor.getSize(), configDescriptor.getDigest());
    Image image = JsonToImageTranslator.toImage(newManifest, configuration);
    return new LocalImage(image, layers);
  }
}
