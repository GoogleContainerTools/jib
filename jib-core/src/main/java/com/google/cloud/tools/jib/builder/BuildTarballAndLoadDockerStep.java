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

import com.google.cloud.tools.jib.Command;
import com.google.cloud.tools.jib.blob.Blob;
import com.google.cloud.tools.jib.blob.Blobs;
import com.google.cloud.tools.jib.cache.CachedLayer;
import com.google.cloud.tools.jib.docker.json.DockerLoadManifestTemplate;
import com.google.cloud.tools.jib.image.ImageReference;
import com.google.cloud.tools.jib.json.JsonTemplateMapper;
import com.google.cloud.tools.jib.tar.TarStreamBuilder;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;

/** Adds image layers to a tarball and loads into Docker daemon. */
class BuildTarballAndLoadDockerStep implements Callable<ListenableFuture<Void>> {

  private final BuildConfiguration buildConfiguration;
  private final ListeningExecutorService listeningExecutorService;
  private final ListenableFuture<List<ListenableFuture<CachedLayer>>>
      pullBaseImageLayerFuturesFuture;
  private final List<ListenableFuture<CachedLayer>> buildApplicationLayerFutures;
  private final ListenableFuture<ListenableFuture<Blob>> buildConfigurationFutureFuture;

  BuildTarballAndLoadDockerStep(
      BuildConfiguration buildConfiguration,
      ListeningExecutorService listeningExecutorService,
      ListenableFuture<List<ListenableFuture<CachedLayer>>> pullBaseImageLayerFuturesFuture,
      List<ListenableFuture<CachedLayer>> buildApplicationLayerFutures,
      ListenableFuture<ListenableFuture<Blob>> buildConfigurationFutureFuture) {
    this.buildConfiguration = buildConfiguration;
    this.listeningExecutorService = listeningExecutorService;
    this.pullBaseImageLayerFuturesFuture = pullBaseImageLayerFuturesFuture;
    this.buildApplicationLayerFutures = buildApplicationLayerFutures;
    this.buildConfigurationFutureFuture = buildConfigurationFutureFuture;
  }

  /**
   * Depends on {@code pullBaseImageLayerFuturesFuture} and {@code buildConfigurationFutureFuture}.
   */
  @Override
  public ListenableFuture<Void> call() throws ExecutionException, InterruptedException {
    List<ListenableFuture<?>> dependencies = new ArrayList<>();
    dependencies.addAll(NonBlockingFutures.get(pullBaseImageLayerFuturesFuture));
    dependencies.addAll(buildApplicationLayerFutures);
    dependencies.add(NonBlockingFutures.get(buildConfigurationFutureFuture));
    return Futures.whenAllComplete(dependencies)
        .call(this::afterPushBaseImageLayerFuturesFuture, listeningExecutorService);
  }

  /**
   * Depends on {@code pullBaseImageLayerFuturesFuture.get()} and (@code
   * buildConfigurationFutureFuture.get()}.
   *
   * <p>TODO: Refactor into testable components
   */
  private Void afterPushBaseImageLayerFuturesFuture()
      throws ExecutionException, InterruptedException, IOException {
    // Add layers to image tarball
    TarStreamBuilder tarStreamBuilder = new TarStreamBuilder();
    List<String> layerFiles = new ArrayList<>();
    for (Future<CachedLayer> cachedLayerFuture :
        NonBlockingFutures.get(pullBaseImageLayerFuturesFuture)) {
      Path layerFile = NonBlockingFutures.get(cachedLayerFuture).getContentFile();
      layerFiles.add(layerFile.getFileName().toString());
      tarStreamBuilder.addEntry(
          new TarArchiveEntry(layerFile.toFile(), layerFile.getFileName().toString()));
    }
    for (Future<CachedLayer> cachedLayerFuture : buildApplicationLayerFutures) {
      Path layerFile = NonBlockingFutures.get(cachedLayerFuture).getContentFile();
      layerFiles.add(layerFile.getFileName().toString());
      tarStreamBuilder.addEntry(
          new TarArchiveEntry(layerFile.toFile(), layerFile.getFileName().toString()));
    }

    // Add config to tarball
    // TODO: Add ability to add blobs as entries to TarStreamBuilder without using temp files
    Blob containerConfigurationBlob =
        NonBlockingFutures.get(NonBlockingFutures.get(buildConfigurationFutureFuture));
    Path tempConfig = Files.createTempFile(null, null);
    tempConfig.toFile().deleteOnExit();
    try (OutputStream bufferedOutputStream =
        new BufferedOutputStream(Files.newOutputStream(tempConfig))) {
      containerConfigurationBlob.writeTo(bufferedOutputStream);
    }
    tarStreamBuilder.addEntry(new TarArchiveEntry(tempConfig.toFile(), "config.json"));

    // Add manifest to tarball
    Blob manifestBlob = getManifestBlob(buildConfiguration.getTargetImageReference(), layerFiles);
    Path tempManifest = Files.createTempFile(null, null);
    tempManifest.toFile().deleteOnExit();
    try (OutputStream bufferedOutputStream =
        new BufferedOutputStream(Files.newOutputStream(tempManifest))) {
      manifestBlob.writeTo(bufferedOutputStream);
    }
    tarStreamBuilder.addEntry(new TarArchiveEntry(tempManifest.toFile(), "manifest.json"));

    // Load the image to docker daemon
    // TODO: Command is untested/not very robust
    new Command("docker", "load").run(Blobs.writeToByteArray(tarStreamBuilder.toBlob()));

    return null;
  }

  /**
   * Builds a {@link DockerLoadManifestTemplate} from image parameters and returns the result as a
   * blob.
   */
  @VisibleForTesting
  static Blob getManifestBlob(ImageReference imageReference, List<String> layerFiles) {
    // Set up the JSON template.
    DockerLoadManifestTemplate template = new DockerLoadManifestTemplate();
    template.setRepoTags(
        imageReference.toString() + (imageReference.usesDefaultTag() ? ":latest" : ""));
    template.addLayerFiles(layerFiles);

    // Serializes into JSON.
    return JsonTemplateMapper.toBlob(Collections.singletonList(template));
  }
}
