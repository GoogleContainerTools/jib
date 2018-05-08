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
import com.google.cloud.tools.jib.cache.CachedLayer;
import com.google.cloud.tools.jib.image.LayerPropertyNotFoundException;
import com.google.cloud.tools.jib.image.json.ImageToJsonTranslator;
import com.google.cloud.tools.jib.tar.TarStreamBuilder;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;

class BuildTarballAndLoadDockerStep implements Callable<Void> {

  private final BuildConfiguration buildConfiguration;
  private final ListeningExecutorService listeningExecutorService;
  private final ListenableFuture<List<ListenableFuture<CachedLayer>>>
      pullBaseImageLayerFuturesFuture;
  private final List<ListenableFuture<CachedLayer>> buildApplicationLayerFutures;
  private final ListenableFuture<ListenableFuture<ImageToJsonTranslator>>
      buildConfigurationFutureFuture;

  BuildTarballAndLoadDockerStep(
      BuildConfiguration buildConfiguration,
      ListeningExecutorService listeningExecutorService,
      ListenableFuture<List<ListenableFuture<CachedLayer>>> pullBaseImageLayerFuturesFuture,
      List<ListenableFuture<CachedLayer>> buildApplicationLayerFutures,
      ListenableFuture<ListenableFuture<ImageToJsonTranslator>> buildConfigurationFutureFuture) {
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
  public Void call() throws ExecutionException, InterruptedException {
    List<ListenableFuture<?>> dependencies = new ArrayList<>();
    dependencies.addAll(NonBlockingFutures.get(pullBaseImageLayerFuturesFuture));
    dependencies.addAll(buildApplicationLayerFutures);
    dependencies.add(NonBlockingFutures.get(buildConfigurationFutureFuture));
    return Futures.whenAllComplete(dependencies)
        .call(this::afterPushBaseImageLayerFuturesFuture, listeningExecutorService)
        .get();
  }

  /**
   * Depends on {@code pushAuthorizationFuture}, {@code pushBaseImageLayerFuturesFuture.get()},
   * {@code pushApplicationLayerFutures}, and (@code
   * containerConfigurationBlobDescriptorFutureFuture.get()}.
   */
  private Void afterPushBaseImageLayerFuturesFuture()
      throws ExecutionException, InterruptedException, LayerPropertyNotFoundException, IOException {
    // Add layers to image tarball
    TarStreamBuilder builder = new TarStreamBuilder();
    List<String> layerFiles = new ArrayList<>();
    for (Future<CachedLayer> cachedLayerFuture :
        NonBlockingFutures.get(pullBaseImageLayerFuturesFuture)) {
      Path layerFile = NonBlockingFutures.get(cachedLayerFuture).getContentFile();
      layerFiles.add(layerFile.getFileName().toString());
      builder.addEntry(new TarArchiveEntry(layerFile.toFile(), layerFile.getFileName().toString()));
    }
    for (Future<CachedLayer> cachedLayerFuture : buildApplicationLayerFutures) {
      Path layerFile = NonBlockingFutures.get(cachedLayerFuture).getContentFile();
      layerFiles.add(layerFile.getFileName().toString());
      builder.addEntry(new TarArchiveEntry(layerFile.toFile(), layerFile.getFileName().toString()));
    }

    // Add config to tarball
    ImageToJsonTranslator imageToJsonTranslator =
        NonBlockingFutures.get(NonBlockingFutures.get(buildConfigurationFutureFuture));
    Blob containerConfigurationBlob = imageToJsonTranslator.getContainerConfigurationBlob();
    Path tempConfig = Files.createTempFile(null, null);
    tempConfig.toFile().deleteOnExit();
    containerConfigurationBlob.writeTo(new BufferedOutputStream(Files.newOutputStream(tempConfig)));
    builder.addEntry(new TarArchiveEntry(tempConfig.toFile(), "config.json"));

    // Add manifest to tarball
    Blob manifestBlob =
        imageToJsonTranslator.getDockerLoadManifestBlob(
            buildConfiguration.getTargetImageRepository(),
            buildConfiguration.getTargetImageTag(),
            layerFiles);
    Path tempManifest = Files.createTempFile(null, null);
    tempManifest.toFile().deleteOnExit();
    manifestBlob.writeTo(new BufferedOutputStream(Files.newOutputStream(tempManifest)));
    builder.addEntry(new TarArchiveEntry(tempManifest.toFile(), "manifest.json"));

    // Load the image to docker daemon
    File tarFile = File.createTempFile(buildConfiguration.getTargetImageRepository(), null);
    builder.toBlob().writeTo(new BufferedOutputStream(Files.newOutputStream(tarFile.toPath())));
    new Command("docker", "load", "--input", tarFile.getAbsolutePath()).run();

    return null;
  }
}
