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

import com.google.cloud.tools.jib.blob.Blob;
import com.google.cloud.tools.jib.blob.BlobDescriptor;
import com.google.cloud.tools.jib.blob.Blobs;
import com.google.cloud.tools.jib.builder.steps.ExtractTarStep.LocalImage;
import com.google.cloud.tools.jib.cache.CachedLayer;
import com.google.cloud.tools.jib.configuration.BuildConfiguration;
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
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
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

  private final Path tarPath;
  private final Path destination;
  private final BuildConfiguration buildConfiguration;

  ExtractTarStep(Path tarPath, Path destination, BuildConfiguration buildConfiguration) {
    this.tarPath = tarPath;
    this.destination = destination;
    this.buildConfiguration = buildConfiguration;
  }

  // TODO: Future<> stuff
  @Override
  public LocalImage call()
      throws IOException, LayerCountMismatchException, BadContainerConfigurationFormatException {
    TarExtractor.extract(tarPath, destination);
    DockerManifestEntryTemplate loadManifest =
        JsonTemplateMapper.readJsonFromFile(
            destination.resolve("manifest.json"), DockerManifestEntryTemplate.class);
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
    boolean layersAreCompressed = false;
    if (layerFiles.size() > 0) {
      layersAreCompressed = isGzipped(destination.resolve(layerFiles.get(0)));
    }

    // Convert v1.2 manifest to v2.2 manifest
    List<PreparedLayer> layers = new ArrayList<>();
    V22ManifestTemplate newManifest = new V22ManifestTemplate();
    for (int index = 0; index < layerFiles.size(); index++) {
      Path file = destination.resolve(layerFiles.get(index));

      Blob blob = layersAreCompressed ? Blobs.from(file) : Blobs.compress(Blobs.from(file));
      BlobDescriptor blobDescriptor = blob.writeTo(ByteStreams.nullOutputStream());

      // 'manifest' contains the layer files in the same order as the diff ids in 'configuration'
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

  @VisibleForTesting
  boolean isGzipped(Path path) throws IOException {
    ByteBuffer buffer = ByteBuffer.allocate(2);
    try (FileChannel channel = FileChannel.open(path)) {
      for (int bytesRead = 0; bytesRead != -1 && buffer.hasRemaining(); ) {
        bytesRead = channel.read(buffer);
      }
    }
    return buffer.getInt() == GZIPInputStream.GZIP_MAGIC;
  }
}
