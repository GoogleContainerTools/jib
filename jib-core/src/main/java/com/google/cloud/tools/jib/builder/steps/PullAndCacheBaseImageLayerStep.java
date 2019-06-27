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
import com.google.cloud.tools.jib.image.Image;
import com.google.cloud.tools.jib.image.Layer;
import com.google.cloud.tools.jib.image.LayerPropertyNotFoundException;
import com.google.cloud.tools.jib.registry.RegistryClient;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/** Pulls and caches a single base image layer. */
class PullAndCacheBaseImageLayerStep implements Callable<CachedLayerAndName> {

  private static final String DESCRIPTION = "Pulling base image layer %s";

  static boolean canSkipCachingBaseImageLayers(
      BuildConfiguration buildConfiguration,
      ImageAndAuthorization baseImageAndAuth,
      @Nullable Authorization pushAuthorization) {
    RegistryClient targetRegistryClient =
        buildConfiguration
            .newTargetImageRegistryClientFactory()
            .setAuthorization(pushAuthorization)
            .newRegistryClient();

    try {
      // TODO: parallelize
      for (Layer layer : baseImageAndAuth.getImage().getLayers()) {
        System.out.println(">>> Checking BLOb existence.");
        System.out.println(">>>     Size=" + layer.getBlobDescriptor().getSize());
        System.out.println(">>>     Digest=" + layer.getBlobDescriptor().getDigest());
        System.out.println(">>>     DiffId=" + layer.getDiffId());
        if (targetRegistryClient.checkBlob(layer.getBlobDescriptor().getDigest()) == null) {
          System.out.println(">>> BLOb does not exist. Halt.");
          return false;
        }
      }
      System.out.println(">>> All BLObs exist. Can skip downloading base image layers.");
      return true;
    } catch (IOException | RegistryException ex) {
      ex.printStackTrace();
      // fall through
    }
    System.out.println(">>> Exception occured.");
    return false;
  }

  static List<CachedLayerAndName> createNoBlobCachedLayers(
      BuildConfiguration buildConfiguration, Image baseImage)
      throws IOException, CacheCorruptedException {
    // The image manifest is already saved, so we should delete it if not all of the layers are
    // actually cached. (--offline shouldn't see an incomplete caching state.)
    Cache cache = buildConfiguration.getBaseImageLayersCache();
    for (Layer layer : baseImage.getLayers()) {
      if (!cache.retrieve(layer.getBlobDescriptor().getDigest()).isPresent()) {
        // TODO: delete the manifest.
      }
    }

    return baseImage
        .getLayers()
        .stream()
        .map(
            layer ->
                CachedLayer.builder()
                    .setLayerDigest(layer.getBlobDescriptor().getDigest())
                    .setLayerSize(layer.getBlobDescriptor().getSize())
                    .setLayerDiffId(layer.getDiffId())
                    .setLayerBlob(
                        ignored -> {
                          throw new LayerPropertyNotFoundException("No actual BLOb attached");
                        })
                    .build())
        .map(cachedLayer -> new CachedLayerAndName(cachedLayer, null))
        .collect(Collectors.toList());
  }

  static ImmutableList<PullAndCacheBaseImageLayerStep> makeList(
      BuildConfiguration buildConfiguration,
      ProgressEventDispatcher.Factory progressEventDispatcherFactory,
      ImageAndAuthorization baseImageAndAuth,
      boolean registryPush,
      Authorization pushAuthorization) {
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
                pushAuthorization,
                registryPush));
      }
      return ImmutableList.copyOf(layerPullers);
    }
  }

  private final BuildConfiguration buildConfiguration;
  private final ProgressEventDispatcher.Factory progressEventDispatcherFactory;

  private final Layer layer;
  private final @Nullable Authorization pullAuthorization;
  private final @Nullable Authorization pushAuthorization;
  private final boolean registryPush;

  PullAndCacheBaseImageLayerStep(
      BuildConfiguration buildConfiguration,
      ProgressEventDispatcher.Factory progressEventDispatcherFactory,
      Layer layer,
      @Nullable Authorization pullAuthorization,
      @Nullable Authorization pushAuthorization,
      boolean registryPush) {
    this.buildConfiguration = buildConfiguration;
    this.progressEventDispatcherFactory = progressEventDispatcherFactory;
    this.layer = layer;
    this.pullAuthorization = pullAuthorization;
    this.pushAuthorization = pushAuthorization;
    this.registryPush = registryPush;
  }

  @Override
  public CachedLayerAndName call() throws IOException, CacheCorruptedException, RegistryException {
    DescriptorDigest layerDigest = layer.getBlobDescriptor().getDigest();
    try (ProgressEventDispatcher progressEventDispatcher =
            progressEventDispatcherFactory.create("checking base image layer " + layerDigest, 1);
        TimerEventDispatcher ignored =
            new TimerEventDispatcher(
                buildConfiguration.getEventHandlers(), String.format(DESCRIPTION, layerDigest))) {
      Cache cache = buildConfiguration.getBaseImageLayersCache();

      // Checks if the layer already exists in the cache.
      Optional<CachedLayer> optionalCachedLayer = cache.retrieve(layerDigest);
      if (optionalCachedLayer.isPresent()) {
        return new CachedLayerAndName(optionalCachedLayer.get(), null);
      } else if (buildConfiguration.isOffline()) {
        throw new IOException(
            "Cannot run Jib in offline mode; local Jib cache for base image is missing image layer "
                + layerDigest
                + ". Rerun Jib in online mode to re-download the base image layers.");
      }

      if (registryPush) {
        RegistryClient targetRegistryClient =
            buildConfiguration
                .newTargetImageRegistryClientFactory()
                .setAuthorization(pullAuthorization)
                .newRegistryClient();
        if (targetRegistryClient.checkBlob(layerDigest) != null) {
          return new CachedLayerAndName(layer, null);
        }
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
        return new CachedLayerAndName(cachedLayer, null);
      }
    }
  }
}
