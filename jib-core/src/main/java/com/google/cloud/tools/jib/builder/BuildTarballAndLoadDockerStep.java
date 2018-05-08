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
import com.google.cloud.tools.jib.blob.BlobDescriptor;
import com.google.cloud.tools.jib.cache.CachedLayer;
import com.google.cloud.tools.jib.docker.DockerLoader;
import com.google.cloud.tools.jib.hash.CountingDigestOutputStream;
import com.google.cloud.tools.jib.image.LayerPropertyNotFoundException;
import com.google.cloud.tools.jib.image.json.BuildableManifestTemplate;
import com.google.cloud.tools.jib.image.json.ImageToJsonTranslator;
import com.google.cloud.tools.jib.image.json.V22ManifestTemplate;
import com.google.cloud.tools.jib.json.JsonTemplateMapper;
import com.google.cloud.tools.jib.tar.TarStreamBuilder;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.io.BufferedOutputStream;
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
    TarStreamBuilder tarStreamBuilder = new TarStreamBuilder();
    for (Future<CachedLayer> cachedLayerFuture :
        NonBlockingFutures.get(pullBaseImageLayerFuturesFuture)) {
      Path blobFile = NonBlockingFutures.get(cachedLayerFuture).getContentFile();
      tarStreamBuilder.addEntry(
          new TarArchiveEntry(blobFile.toFile(), blobFile.getFileName().toString()));
    }
    for (Future<CachedLayer> cachedLayerFuture : buildApplicationLayerFutures) {
      Path blobFile = NonBlockingFutures.get(cachedLayerFuture).getContentFile();
      tarStreamBuilder.addEntry(
          new TarArchiveEntry(blobFile.toFile(), blobFile.getFileName().toString()));
    }

    // Add config to tarball
    ImageToJsonTranslator imageToJsonTranslator =
        NonBlockingFutures.get(NonBlockingFutures.get(buildConfigurationFutureFuture));
    Path tempConfig = Files.createTempFile(null, null);
    tempConfig.toFile().deleteOnExit();
    CountingDigestOutputStream digestOutputStream =
        new CountingDigestOutputStream(new BufferedOutputStream(Files.newOutputStream(tempConfig)));
    Blob containerConfigurationBlob = imageToJsonTranslator.getContainerConfigurationBlob();
    containerConfigurationBlob.writeTo(digestOutputStream);
    tarStreamBuilder.addEntry(new TarArchiveEntry(tempConfig.toFile(), "config.json"));

    // Add manifest to tarball
    Path tempManifest = Files.createTempFile(null, null);
    tempManifest.toFile().deleteOnExit();
    BlobDescriptor descriptor = digestOutputStream.toBlobDescriptor();
    BuildableManifestTemplate manifestTemplate =
        imageToJsonTranslator.getManifestTemplate(V22ManifestTemplate.class, descriptor);
    JsonTemplateMapper.toBlob(manifestTemplate)
        .writeTo(new BufferedOutputStream(Files.newOutputStream(tempManifest)));
    tarStreamBuilder.addEntry(new TarArchiveEntry(tempManifest.toFile(), "manifest.json"));

    // Load the image to docker daemon
    new DockerLoader()
        .load(tarStreamBuilder.toBlob(), buildConfiguration.getTargetImageRepository());

    return null;
  }
}
