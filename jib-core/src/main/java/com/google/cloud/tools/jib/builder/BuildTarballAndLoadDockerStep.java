/*
 * Copyright 2018 Google LLC. All Rights Reserved.
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

package com.google.cloud.tools.jib.builder;

import com.google.cloud.tools.jib.blob.Blob;
import com.google.cloud.tools.jib.blob.Blobs;
import com.google.cloud.tools.jib.cache.CachedLayer;
import com.google.cloud.tools.jib.docker.json.DockerLoadManifestTemplate;
import com.google.cloud.tools.jib.image.Image;
import com.google.cloud.tools.jib.image.Layer;
import com.google.cloud.tools.jib.image.LayerPropertyNotFoundException;
import com.google.cloud.tools.jib.image.json.ImageToJsonTranslator;
import com.google.cloud.tools.jib.json.JsonTemplateMapper;
import com.google.cloud.tools.jib.tar.TarStreamBuilder;
import com.google.common.io.CharStreams;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;

/** Adds image layers to a tarball and loads into Docker daemon. */
class BuildTarballAndLoadDockerStep implements Callable<Void> {

  private final BuildConfiguration buildConfiguration;
  private final ListeningExecutorService listeningExecutorService;
  private final ListenableFuture<List<ListenableFuture<CachedLayer>>>
      pullBaseImageLayerFuturesFuture;
  private final List<ListenableFuture<CachedLayer>> buildApplicationLayerFutures;
  private final ListenableFuture<ListenableFuture<Image>> buildImageFutureFuture;

  BuildTarballAndLoadDockerStep(
      BuildConfiguration buildConfiguration,
      ListeningExecutorService listeningExecutorService,
      ListenableFuture<List<ListenableFuture<CachedLayer>>> pullBaseImageLayerFuturesFuture,
      List<ListenableFuture<CachedLayer>> buildApplicationLayerFutures,
      ListenableFuture<ListenableFuture<Image>> buildImageFutureFuture) {
    this.buildConfiguration = buildConfiguration;
    this.listeningExecutorService = listeningExecutorService;
    this.pullBaseImageLayerFuturesFuture = pullBaseImageLayerFuturesFuture;
    this.buildApplicationLayerFutures = buildApplicationLayerFutures;
    this.buildImageFutureFuture = buildImageFutureFuture;
  }

  /** Depends on {@code pullBaseImageLayerFuturesFuture} and {@code buildImageFutureFuture}. */
  @Override
  public Void call() throws ExecutionException, InterruptedException {
    List<ListenableFuture<?>> dependencies = new ArrayList<>();
    dependencies.addAll(NonBlockingFutures.get(pullBaseImageLayerFuturesFuture));
    dependencies.addAll(buildApplicationLayerFutures);
    dependencies.add(NonBlockingFutures.get(buildImageFutureFuture));
    return Futures.whenAllComplete(dependencies)
        .call(this::afterPushBaseImageLayerFuturesFuture, listeningExecutorService)
        .get();
  }

  /**
   * Depends on {@code pullBaseImageLayerFuturesFuture.get()} and (@code
   * buildImageFutureFuture.get()}.
   *
   * <p>TODO: Refactor into testable components
   */
  private Void afterPushBaseImageLayerFuturesFuture()
      throws ExecutionException, InterruptedException, IOException, LayerPropertyNotFoundException {
    // Add layers to image tarball
    Image image = NonBlockingFutures.get(NonBlockingFutures.get(buildImageFutureFuture));
    TarStreamBuilder tarStreamBuilder = new TarStreamBuilder();
    DockerLoadManifestTemplate manifestTemplate = new DockerLoadManifestTemplate();

    for (Layer layer : image.getLayers()) {
      // TODO: Refactor Image to only contain CachedLayers
      Path cachedFile = ((CachedLayer) layer).getContentFile();
      String layerName = cachedFile.getFileName().toString();
      tarStreamBuilder.addEntry(new TarArchiveEntry(cachedFile.toFile(), layerName));
      manifestTemplate.addLayerFile(layerName);
    }

    // Add config to tarball
    Blob containerConfigurationBlob =
        new ImageToJsonTranslator(image).getContainerConfigurationBlob();
    tarStreamBuilder.addEntry(Blobs.writeToString(containerConfigurationBlob), "config.json");

    // Add manifest to tarball
    manifestTemplate.setRepoTags(buildConfiguration.getTargetImageReference().toStringWithTag());
    tarStreamBuilder.addEntry(
        Blobs.writeToString(JsonTemplateMapper.toBlob(manifestTemplate)), "manifest.json");

    // Load the image to docker daemon
    ProcessBuilder processBuilder = new ProcessBuilder("docker", "load");
    Process process = processBuilder.start();
    try (OutputStream stdin = process.getOutputStream()) {
      tarStreamBuilder.toBlob().writeTo(stdin);
    }
    try (InputStreamReader stdout =
        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)) {
      String output = CharStreams.toString(stdout);
      if (process.waitFor() != 0) {
        throw new IOException("'docker load' command failed with output: " + output);
      }
    }

    return null;
  }
}
