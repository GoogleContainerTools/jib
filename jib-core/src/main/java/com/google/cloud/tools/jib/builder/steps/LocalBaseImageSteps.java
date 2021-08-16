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
import com.google.cloud.tools.jib.builder.steps.PullBaseImageStep.ImagesAndRegistryClient;
import com.google.cloud.tools.jib.cache.Cache;
import com.google.cloud.tools.jib.cache.CacheCorruptedException;
import com.google.cloud.tools.jib.cache.CachedLayer;
import com.google.cloud.tools.jib.configuration.BuildContext;
import com.google.cloud.tools.jib.docker.DockerClient;
import com.google.cloud.tools.jib.docker.DockerClient.DockerImageDetails;
import com.google.cloud.tools.jib.docker.json.DockerManifestEntryTemplate;
import com.google.cloud.tools.jib.event.progress.ThrottledAccumulatingConsumer;
import com.google.cloud.tools.jib.filesystem.TempDirectoryProvider;
import com.google.cloud.tools.jib.hash.Digests;
import com.google.cloud.tools.jib.http.NotifyingOutputStream;
import com.google.cloud.tools.jib.image.Image;
import com.google.cloud.tools.jib.image.LayerCountMismatchException;
import com.google.cloud.tools.jib.image.json.ContainerConfigurationTemplate;
import com.google.cloud.tools.jib.image.json.JsonToImageTranslator;
import com.google.cloud.tools.jib.image.json.V22ManifestTemplate;
import com.google.cloud.tools.jib.json.JsonTemplateMapper;
import com.google.cloud.tools.jib.tar.TarExtractor;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.io.ByteStreams;
import com.google.common.util.concurrent.Futures;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** Extracts a tar file base image. */
public class LocalBaseImageSteps {

  /** Contains an {@link Image} and its layers. * */
  static class LocalImage {
    final List<Future<PreparedLayer>> layers;
    final ContainerConfigurationTemplate configurationTemplate;

    LocalImage(
        List<Future<PreparedLayer>> layers, ContainerConfigurationTemplate configurationTemplate) {
      this.layers = layers;
      this.configurationTemplate = configurationTemplate;
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

  static Callable<LocalImage> retrieveDockerDaemonLayersStep(
      BuildContext buildContext,
      ProgressEventDispatcher.Factory progressEventDispatcherFactory,
      DockerClient dockerClient,
      TempDirectoryProvider tempDirectoryProvider) {
    return () -> {
      ImageReference imageReference = buildContext.getBaseImageConfiguration().getImage();
      try (ProgressEventDispatcher progressEventDispatcher =
              progressEventDispatcherFactory.create("processing base image " + imageReference, 2);
          TimerEventDispatcher ignored =
              new TimerEventDispatcher(
                  buildContext.getEventHandlers(),
                  "Saving " + imageReference + " from Docker daemon")) {
        DockerClient.DockerImageDetails dockerImageDetails = dockerClient.inspect(imageReference);
        Optional<LocalImage> cachedImage =
            getCachedDockerImage(buildContext.getBaseImageLayersCache(), dockerImageDetails);
        if (cachedImage.isPresent()) {
          PlatformChecker.checkManifestPlatform(
              buildContext, cachedImage.get().configurationTemplate);
          return cachedImage.get();
        }

        Path tarPath = tempDirectoryProvider.newDirectory().resolve("out.tar");
        long size = dockerClient.inspect(imageReference).getSize();
        try (ProgressEventDispatcher dockerProgress =
                progressEventDispatcher
                    .newChildProducer()
                    .create("saving base image " + imageReference, size);
            ThrottledAccumulatingConsumer throttledProgressReporter =
                new ThrottledAccumulatingConsumer(dockerProgress::dispatchProgress)) {
          dockerClient.save(imageReference, tarPath, throttledProgressReporter);
        }

        LocalImage localImage =
            cacheDockerImageTar(
                buildContext,
                tarPath,
                progressEventDispatcher.newChildProducer(),
                tempDirectoryProvider);
        PlatformChecker.checkManifestPlatform(buildContext, localImage.configurationTemplate);
        return localImage;
      }
    };
  }

  static Callable<LocalImage> retrieveTarLayersStep(
      BuildContext buildContext,
      ProgressEventDispatcher.Factory progressEventDispatcherFactory,
      Path tarPath,
      TempDirectoryProvider tempDirectoryProvider) {
    return () -> {
      LocalImage localImage =
          cacheDockerImageTar(
              buildContext, tarPath, progressEventDispatcherFactory, tempDirectoryProvider);
      PlatformChecker.checkManifestPlatform(buildContext, localImage.configurationTemplate);
      return localImage;
    };
  }

  static Callable<ImagesAndRegistryClient> returnImageAndRegistryClientStep(
      List<PreparedLayer> layers, ContainerConfigurationTemplate configurationTemplate) {
    return () -> {
      // Collect compressed layers and add to manifest
      V22ManifestTemplate v22Manifest = new V22ManifestTemplate();
      for (PreparedLayer layer : layers) {
        BlobDescriptor descriptor = layer.getBlobDescriptor();
        v22Manifest.addLayer(descriptor.getSize(), descriptor.getDigest());
      }

      BlobDescriptor configDescriptor = Digests.computeDigest(configurationTemplate);
      v22Manifest.setContainerConfiguration(
          configDescriptor.getSize(), configDescriptor.getDigest());
      return new ImagesAndRegistryClient(
          Collections.singletonList(
              JsonToImageTranslator.toImage(v22Manifest, configurationTemplate)),
          null);
    };
  }

  @VisibleForTesting
  static Optional<LocalImage> getCachedDockerImage(
      Cache cache, DockerImageDetails dockerImageDetails)
      throws DigestException, IOException, CacheCorruptedException {
    // Get config
    Optional<ContainerConfigurationTemplate> cachedConfig =
        cache.retrieveLocalConfig(dockerImageDetails.getImageId());
    if (!cachedConfig.isPresent()) {
      return Optional.empty();
    }

    // Get layers
    List<Future<PreparedLayer>> cachedLayers = new ArrayList<>();
    for (DescriptorDigest diffId : dockerImageDetails.getDiffIds()) {
      Optional<CachedLayer> cachedLayer = cache.retrieveTarLayer(diffId);
      if (!cachedLayer.isPresent()) {
        return Optional.empty();
      }
      CachedLayer layer = cachedLayer.get();
      cachedLayers.add(Futures.immediateFuture(new PreparedLayer.Builder(layer).build()));
    }

    return Optional.of(new LocalImage(cachedLayers, cachedConfig.get()));
  }

  @VisibleForTesting
  static LocalImage cacheDockerImageTar(
      BuildContext buildContext,
      Path tarPath,
      ProgressEventDispatcher.Factory progressEventDispatcherFactory,
      TempDirectoryProvider tempDirectoryProvider)
      throws IOException, LayerCountMismatchException {
    ExecutorService executorService = buildContext.getExecutorService();
    Path destination = tempDirectoryProvider.newDirectory();

    try (TimerEventDispatcher ignored =
        new TimerEventDispatcher(
            buildContext.getEventHandlers(),
            "Extracting tar " + tarPath + " into " + destination)) {
      TarExtractor.extract(tarPath, destination);

      InputStream manifestStream = Files.newInputStream(destination.resolve("manifest.json"));
      DockerManifestEntryTemplate loadManifest =
          new ObjectMapper()
              .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true)
              .readValue(manifestStream, DockerManifestEntryTemplate[].class)[0];
      manifestStream.close();

      Path configPath = destination.resolve(loadManifest.getConfig());
      ContainerConfigurationTemplate configurationTemplate =
          JsonTemplateMapper.readJsonFromFile(configPath, ContainerConfigurationTemplate.class);
      // Don't compute the digest of the loaded Java JSON instance.
      BlobDescriptor originalConfigDescriptor =
          Blobs.from(configPath).writeTo(ByteStreams.nullOutputStream());

      List<String> layerFiles = loadManifest.getLayerFiles();
      if (configurationTemplate.getLayerCount() != layerFiles.size()) {
        throw new LayerCountMismatchException(
            "Invalid base image format: manifest contains "
                + layerFiles.size()
                + " layers, but container configuration contains "
                + configurationTemplate.getLayerCount()
                + " layers");
      }
      buildContext
          .getBaseImageLayersCache()
          .writeLocalConfig(originalConfigDescriptor.getDigest(), configurationTemplate);

      // Check the first layer to see if the layers are compressed already. 'docker save' output
      // is uncompressed, but a jib-built tar has compressed layers.
      boolean layersAreCompressed =
          !layerFiles.isEmpty() && isGzipped(destination.resolve(layerFiles.get(0)));

      // Process layer blobs
      try (ProgressEventDispatcher progressEventDispatcher =
          progressEventDispatcherFactory.create(
              "processing base image layers", layerFiles.size())) {
        // Start compressing layers in parallel
        List<Future<PreparedLayer>> preparedLayers = new ArrayList<>();
        for (int index = 0; index < layerFiles.size(); index++) {
          Path layerFile = destination.resolve(layerFiles.get(index));
          DescriptorDigest diffId = configurationTemplate.getLayerDiffId(index);
          ProgressEventDispatcher.Factory layerProgressDispatcherFactory =
              progressEventDispatcher.newChildProducer();
          preparedLayers.add(
              executorService.submit(
                  () ->
                      compressAndCacheTarLayer(
                          buildContext.getBaseImageLayersCache(),
                          diffId,
                          layerFile,
                          layersAreCompressed,
                          layerProgressDispatcherFactory)));
        }
        return new LocalImage(preparedLayers, configurationTemplate);
      }
    }
  }

  private static PreparedLayer compressAndCacheTarLayer(
      Cache cache,
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
      // Retrieve pre-compressed layer from cache
      Optional<CachedLayer> optionalLayer = cache.retrieveTarLayer(diffId);
      if (optionalLayer.isPresent()) {
        return new PreparedLayer.Builder(optionalLayer.get()).build();
      }

      // Just write layers that are already compressed
      if (layersAreCompressed) {
        return new PreparedLayer.Builder(cache.writeTarLayer(diffId, Blobs.from(layerFile)))
            .build();
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
              },
              true);
      return new PreparedLayer.Builder(cache.writeTarLayer(diffId, compressedBlob)).build();
    }
  }
}
