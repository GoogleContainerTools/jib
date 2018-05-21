/*
 * Copyright 2018 Google LLC. All rights reserved.
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
import com.google.common.collect.ImmutableList;
import com.google.common.io.CharStreams;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nullable;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;

/** Adds image layers to a tarball and loads into Docker daemon. */
class BuildTarballAndLoadDockerStep implements AsyncStep<Void> {

  private final BuildConfiguration buildConfiguration;
  private final PullAndCacheBaseImageLayersStep pullAndCacheBaseImageLayersStep;
  private final BuildAndCacheApplicationLayersStep buildAndCacheApplicationLayersStep;
  private final BuildImageStep buildImageStep;

  private final ListeningExecutorService listeningExecutorService;
  @Nullable private ListenableFuture<Void> listenableFuture;

  BuildTarballAndLoadDockerStep(
      ListeningExecutorService listeningExecutorService,
      BuildConfiguration buildConfiguration,
      PullAndCacheBaseImageLayersStep pullAndCacheBaseImageLayersStep,
      BuildAndCacheApplicationLayersStep buildAndCacheApplicationLayersStep,
      BuildImageStep buildImageStep) {
    this.listeningExecutorService = listeningExecutorService;
    this.buildConfiguration = buildConfiguration;
    this.pullAndCacheBaseImageLayersStep = pullAndCacheBaseImageLayersStep;
    this.buildAndCacheApplicationLayersStep = buildAndCacheApplicationLayersStep;
    this.buildImageStep = buildImageStep;
  }

  @Override
  public ListenableFuture<Void> getFuture() {
    if (listenableFuture == null) {
      listenableFuture =
          Futures.whenAllSucceed(
                  pullAndCacheBaseImageLayersStep.getFuture(),
                  buildAndCacheApplicationLayersStep.getFuture(),
                  buildImageStep.getFuture())
              .call(this, listeningExecutorService);
    }
    return listenableFuture;
  }

  @Override
  public Void call() throws ExecutionException, InterruptedException {
    ImmutableList.Builder<ListenableFuture<?>> dependenciesBuilder = ImmutableList.builder();
    for (PullAndCacheBaseImageLayerStep pullAndCacheBaseImageLayerStep :
        NonBlockingSteps.get(pullAndCacheBaseImageLayersStep)) {
      dependenciesBuilder.add(pullAndCacheBaseImageLayerStep.getFuture());
    }
    for (AsyncStep<CachedLayer> buildAndCacheApplicationLayerStep :
        NonBlockingSteps.get(buildAndCacheApplicationLayersStep)) {
      dependenciesBuilder.add(buildAndCacheApplicationLayerStep.getFuture());
    }
    dependenciesBuilder.add(NonBlockingSteps.get(buildImageStep));
    return Futures.whenAllComplete(dependenciesBuilder.build())
        .call(this::afterPushBaseImageLayerFuturesFuture, listeningExecutorService)
        .get();
  }

  // TODO: Refactor into testable components
  private Void afterPushBaseImageLayerFuturesFuture()
      throws ExecutionException, InterruptedException, IOException, LayerPropertyNotFoundException {
    // Add layers to image tarball
    Image image = NonBlockingFutures.get(NonBlockingSteps.get(buildImageStep));
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
