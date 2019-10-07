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
import com.google.cloud.tools.jib.api.ImageReference;
import com.google.cloud.tools.jib.blob.Blob;
import com.google.cloud.tools.jib.blob.BlobDescriptor;
import com.google.cloud.tools.jib.blob.Blobs;
import com.google.cloud.tools.jib.builder.ProgressEventDispatcher;
import com.google.cloud.tools.jib.builder.TimerEventDispatcher;
import com.google.cloud.tools.jib.cache.Cache;
import com.google.cloud.tools.jib.cache.CacheCorruptedException;
import com.google.cloud.tools.jib.cache.CachedLayer;
import com.google.cloud.tools.jib.configuration.BuildConfiguration;
import com.google.cloud.tools.jib.docker.DockerClient;
import com.google.cloud.tools.jib.docker.json.DockerManifestEntryTemplate;
import com.google.cloud.tools.jib.event.progress.ThrottledAccumulatingConsumer;
import com.google.cloud.tools.jib.filesystem.TempDirectoryProvider;
import com.google.cloud.tools.jib.http.NotifyingOutputStream;
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** Extracts a tar file base image. */
public class LocalBaseImageSteps {

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

  private final ExecutorService executorService;
  private final BuildConfiguration buildConfiguration;
  private final ProgressEventDispatcher.Factory progressEventDispatcherFactory;

  LocalBaseImageSteps(
      ExecutorService executorService,
      BuildConfiguration buildConfiguration,
      ProgressEventDispatcher.Factory progressEventDispatcherFactory) {
    this.executorService = executorService;
    this.buildConfiguration = buildConfiguration;
    this.progressEventDispatcherFactory = progressEventDispatcherFactory;
  }

  LocalImage dockerDaemonImageStep(DockerClient dockerClient)
      throws InterruptedException, ExecutionException, BadContainerConfigurationFormatException,
          LayerCountMismatchException, IOException {
    ImageReference imageReference = buildConfiguration.getBaseImageConfiguration().getImage();
    try (TempDirectoryProvider tempDirectoryProvider = new TempDirectoryProvider();
        ProgressEventDispatcher progressEventDispatcher =
            progressEventDispatcherFactory.create("processing base image " + imageReference, 2)) {
      Path outputDir = tempDirectoryProvider.newDirectory();
      Path tarPath = outputDir.resolve("out.tar");
      try (TimerEventDispatcher ignored =
          new TimerEventDispatcher(
              buildConfiguration.getEventHandlers(),
              "Saving " + imageReference + " from Docker daemon")) {
        long size = dockerClient.sizeOf(imageReference);
        try (ProgressEventDispatcher dockerProgress =
                progressEventDispatcher
                    .newChildProducer()
                    .create("saving base image " + imageReference, size);
            ThrottledAccumulatingConsumer throttledProgressReporter =
                new ThrottledAccumulatingConsumer(dockerProgress::dispatchProgress)) {
          dockerClient.save(imageReference, tarPath, throttledProgressReporter);
        }
      }

      return extractTar(tarPath, tempDirectoryProvider, progressEventDispatcher.newChildProducer());
    }
  }

  LocalImage tarImageStep(Path tarPath)
      throws InterruptedException, ExecutionException, BadContainerConfigurationFormatException,
          LayerCountMismatchException, IOException {
    try (TempDirectoryProvider tempDirectoryProvider = new TempDirectoryProvider()) {
      return extractTar(tarPath, tempDirectoryProvider, progressEventDispatcherFactory);
    }
  }

  private LocalImage extractTar(
      Path tarPath,
      TempDirectoryProvider tempDirectoryProvider,
      ProgressEventDispatcher.Factory progressEventDispatcherFactory)
      throws IOException, LayerCountMismatchException, BadContainerConfigurationFormatException,
          ExecutionException, InterruptedException {
    Path destination = tempDirectoryProvider.newDirectory();
    try (TimerEventDispatcher ignored =
        new TimerEventDispatcher(
            buildConfiguration.getEventHandlers(),
            "Extracting tar " + tarPath + " into " + destination)) {
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
      try (ProgressEventDispatcher progressEventDispatcher =
          progressEventDispatcherFactory.create(
              "processing base image layers", layerFiles.size())) {
        List<PreparedLayer> layers = new ArrayList<>(layerFiles.size());
        V22ManifestTemplate v22Manifest = new V22ManifestTemplate();

        // Start compressing layers in parallel
        List<Future<CachedLayer>> cachedLayers = new ArrayList<>();
        for (int index = 0; index < layerFiles.size(); index++) {
          Path layerFile = destination.resolve(layerFiles.get(index));
          DescriptorDigest diffId = configurationTemplate.getLayerDiffId(index);
          ProgressEventDispatcher.Factory layerProgressFactory =
              progressEventDispatcher.newChildProducer();
          cachedLayers.add(
              executorService.submit(
                  () ->
                      getCachedTarLayer(
                          diffId, layerFile, layersAreCompressed, layerProgressFactory)));
        }

        // Collect compressed layers and add to manifest
        for (Future<CachedLayer> layerFuture : cachedLayers) {
          CachedLayer layer = layerFuture.get();
          layers.add(new PreparedLayer.Builder(layer).build());
          v22Manifest.addLayer(layer.getSize(), layer.getDigest());
        }

        BlobDescriptor configDescriptor =
            Blobs.from(configurationTemplate).writeTo(ByteStreams.nullOutputStream());
        v22Manifest.setContainerConfiguration(
            configDescriptor.getSize(), configDescriptor.getDigest());
        Image image = JsonToImageTranslator.toImage(v22Manifest, configurationTemplate);
        return new LocalImage(image, layers);
      }
    }
  }

  private CachedLayer getCachedTarLayer(
      DescriptorDigest diffId,
      Path layerFile,
      boolean layersAreCompressed,
      ProgressEventDispatcher.Factory progressEventDispatcherFactory)
      throws IOException, CacheCorruptedException {
    try (ProgressEventDispatcher childDispatcher =
            progressEventDispatcherFactory.create(
                "compressing layer " + diffId, Files.size(layerFile));
        ThrottledAccumulatingConsumer throttledProgressReporter =
            new ThrottledAccumulatingConsumer(childDispatcher::dispatchProgress)) {
      Cache cache = buildConfiguration.getBaseImageLayersCache();

      // Retrieve pre-compressed layer from cache
      Optional<CachedLayer> optionalLayer = cache.retrieveTarLayer(diffId);
      if (optionalLayer.isPresent()) {
        return optionalLayer.get();
      }

      // Just write layers that are already compressed
      if (layersAreCompressed) {
        return cache.writeTarLayer(diffId, Blobs.from(layerFile));
      }

      // Compress uncompressed layers while writing
      Blob compressedBlob =
          Blobs.from(
              outputStream -> {
                try (GZIPOutputStream compressorStream = new GZIPOutputStream(outputStream);
                    NotifyingOutputStream notifyingOutputStream =
                        new NotifyingOutputStream(compressorStream, throttledProgressReporter)) {
                  Blobs.from(layerFile).writeTo(notifyingOutputStream);
                }
              });
      return cache.writeTarLayer(diffId, compressedBlob);
    }
  }
}
