/*
 * Copyright 2018 Google LLC.
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

import com.google.cloud.tools.jib.api.DescriptorDigest;
import com.google.cloud.tools.jib.api.RegistryException;
import com.google.cloud.tools.jib.builder.ProgressEventDispatcher;
import com.google.cloud.tools.jib.builder.TimerEventDispatcher;
import com.google.cloud.tools.jib.builder.steps.PullBaseImageStep.ImageAndAuthorization;
import com.google.cloud.tools.jib.cache.Cache;
import com.google.cloud.tools.jib.cache.CacheCorruptedException;
import com.google.cloud.tools.jib.cache.CachedLayer;
import com.google.cloud.tools.jib.configuration.BuildConfiguration;
import com.google.cloud.tools.jib.http.Authorization;
import com.google.cloud.tools.jib.image.Layer;
import com.google.cloud.tools.jib.registry.RegistryClient;
import com.google.common.base.Verify;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import javax.annotation.Nullable;

/** Pulls and caches a single base image layer. */
class PullAndCacheBaseImageLayerStep implements Callable<PreparedLayer> {

  private static final String DESCRIPTION = "Pulling base image layer %s";

  private interface BlobChecker {

    Optional<Boolean> exists(DescriptorDigest digest) throws IOException, RegistryException;
  }

  static ImmutableList<PullAndCacheBaseImageLayerStep> makeListForForcedDownload(
      BuildConfiguration buildConfiguration,
      ProgressEventDispatcher.Factory progressEventDispatcherFactory,
      ImageAndAuthorization baseImageAndAuth) {
    BlobChecker noOpBlobChecker = ignored -> Optional.empty();
    return makeList(
        buildConfiguration, progressEventDispatcherFactory, baseImageAndAuth, noOpBlobChecker);
  }

  static ImmutableList<PullAndCacheBaseImageLayerStep> makeListForSelectiveDownload(
      BuildConfiguration buildConfiguration,
      ProgressEventDispatcher.Factory progressEventDispatcherFactory,
      ImageAndAuthorization baseImageAndAuth,
      Authorization pushAuthorization) {
    Verify.verify(!buildConfiguration.isOffline());

    RegistryClient targetRegistryClient =
        buildConfiguration
            .newTargetImageRegistryClientFactory()
            .setAuthorization(pushAuthorization)
            .newRegistryClient();
    BlobChecker blobChecker =
        digest -> Optional.of(targetRegistryClient.checkBlob(digest).isPresent());

    return makeList(
        buildConfiguration, progressEventDispatcherFactory, baseImageAndAuth, blobChecker);
  }

  private static ImmutableList<PullAndCacheBaseImageLayerStep> makeList(
      BuildConfiguration buildConfiguration,
      ProgressEventDispatcher.Factory progressEventDispatcherFactory,
      ImageAndAuthorization baseImageAndAuth,
      BlobChecker blobChecker) {
    ImmutableList<Layer> baseImageLayers = baseImageAndAuth.getImage().getLayers();

    try (ProgressEventDispatcher progressEventDispatcher =
            progressEventDispatcherFactory.create(
                "preparing base image layer pullers", baseImageLayers.size());
        TimerEventDispatcher ignored =
            new TimerEventDispatcher(
                buildConfiguration.getEventHandlers(), "Preparing base image layer pullers")) {

      List<PullAndCacheBaseImageLayerStep> layerPullers = new ArrayList<>();
      for (Layer layer : baseImageLayers) {
        layerPullers.add(
            new PullAndCacheBaseImageLayerStep(
                buildConfiguration,
                progressEventDispatcher.newChildProducer(),
                layer,
                baseImageAndAuth.getAuthorization(),
                blobChecker));
      }
      return ImmutableList.copyOf(layerPullers);
    }
  }

  private final BuildConfiguration buildConfiguration;
  private final ProgressEventDispatcher.Factory progressEventDispatcherFactory;

  private final Layer layer;
  private final @Nullable Authorization pullAuthorization;
  private final BlobChecker blobChecker;

  PullAndCacheBaseImageLayerStep(
      BuildConfiguration buildConfiguration,
      ProgressEventDispatcher.Factory progressEventDispatcherFactory,
      Layer layer,
      @Nullable Authorization pullAuthorization,
      BlobChecker blobChecker) {
    this.buildConfiguration = buildConfiguration;
    this.progressEventDispatcherFactory = progressEventDispatcherFactory;
    this.layer = layer;
    this.pullAuthorization = pullAuthorization;
    this.blobChecker = blobChecker;
  }

  @Override
  public PreparedLayer call() throws IOException, CacheCorruptedException, RegistryException {
    DescriptorDigest layerDigest = layer.getBlobDescriptor().getDigest();
    try (ProgressEventDispatcher progressEventDispatcher =
            progressEventDispatcherFactory.create("checking base image layer " + layerDigest, 1);
        TimerEventDispatcher ignored =
            new TimerEventDispatcher(
                buildConfiguration.getEventHandlers(), String.format(DESCRIPTION, layerDigest))) {

      Optional<Boolean> layerExists = blobChecker.exists(layerDigest);
      if (layerExists.orElse(false)) {
        return new PreparedLayer.Builder(layer).setStateInTarget(layerExists).build();
      }

      Cache cache = buildConfiguration.getBaseImageLayersCache();

      // Checks if the layer already exists in the cache.
      Optional<CachedLayer> optionalCachedLayer = cache.retrieve(layerDigest);
      if (optionalCachedLayer.isPresent()) {
        CachedLayer cachedLayer = optionalCachedLayer.get();
        return new PreparedLayer.Builder(cachedLayer).setStateInTarget(layerExists).build();
      } else if (buildConfiguration.isOffline()) {
        throw new IOException(
            "Cannot run Jib in offline mode; local Jib cache for base image is missing image layer "
                + layerDigest
                + ". Rerun Jib in online mode with \"-Djib.forceDownload=true\" to re-download the "
                + "base image layers.");
      }

      RegistryClient registryClient =
          buildConfiguration
              .newBaseImageRegistryClientFactory()
              .setAuthorization(pullAuthorization)
              .newRegistryClient();

      try (ThrottledProgressEventDispatcherWrapper progressEventDispatcherWrapper =
          new ThrottledProgressEventDispatcherWrapper(
              progressEventDispatcher.newChildProducer(),
              "pulling base image layer " + layerDigest)) {
        CachedLayer cachedLayer =
            cache.writeCompressedLayer(
                registryClient.pullBlob(
                    layerDigest,
                    progressEventDispatcherWrapper::setProgressTarget,
                    progressEventDispatcherWrapper::dispatchProgress));
        return new PreparedLayer.Builder(cachedLayer).setStateInTarget(layerExists).build();
      }
    }
  }
}
