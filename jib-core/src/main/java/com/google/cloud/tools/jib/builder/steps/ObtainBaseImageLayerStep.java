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
import com.google.cloud.tools.jib.api.LogEvent;
import com.google.cloud.tools.jib.api.RegistryException;
import com.google.cloud.tools.jib.builder.ProgressEventDispatcher;
import com.google.cloud.tools.jib.builder.TimerEventDispatcher;
import com.google.cloud.tools.jib.builder.steps.PreparedLayer.StateInTarget;
import com.google.cloud.tools.jib.cache.Cache;
import com.google.cloud.tools.jib.cache.CacheCorruptedException;
import com.google.cloud.tools.jib.cache.CachedLayer;
import com.google.cloud.tools.jib.configuration.BuildContext;
import com.google.cloud.tools.jib.event.EventHandlers;
import com.google.cloud.tools.jib.image.Layer;
import com.google.cloud.tools.jib.registry.RegistryClient;
import com.google.common.base.Verify;
import java.io.IOException;
import java.nio.file.Path;
import java.util.AbstractMap.SimpleEntry;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nullable;

/** Pulls and caches a single base image layer. */
class ObtainBaseImageLayerStep implements Callable<PreparedLayer> {

  @SuppressWarnings("InlineFormatString")
  private static final String DESCRIPTION = "Pulling base image layer %s";

  /**
   * Layers currently being pulled in this JVM, keyed by cache directory and layer digest. Lets
   * parallel builds (e.g., "mvn -T") that share a base image and a cache pull each layer once.
   */
  private static final Map<Map.Entry<Path, DescriptorDigest>, CompletableFuture<CachedLayer>>
      inFlightPulls = new ConcurrentHashMap<>();

  @FunctionalInterface
  private interface BlobExistenceChecker {

    StateInTarget check(DescriptorDigest digest) throws IOException, RegistryException;
  }

  static ObtainBaseImageLayerStep forForcedDownload(
      BuildContext buildContext,
      ProgressEventDispatcher.Factory progressEventDispatcherFactory,
      Layer layer,
      @Nullable RegistryClient sourceRegistryClient) {
    BlobExistenceChecker noOpChecker = ignored -> StateInTarget.UNKNOWN;
    return new ObtainBaseImageLayerStep(
        buildContext, progressEventDispatcherFactory, layer, sourceRegistryClient, noOpChecker);
  }

  static ObtainBaseImageLayerStep forSelectiveDownload(
      BuildContext buildContext,
      ProgressEventDispatcher.Factory progressEventDispatcherFactory,
      Layer layer,
      @Nullable RegistryClient sourceRegistryClient,
      RegistryClient targetRegistryClient) {
    Verify.verify(!buildContext.isOffline());

    // TODO: also check if cross-repo blob mount is possible.
    BlobExistenceChecker blobExistenceChecker =
        digest ->
            targetRegistryClient.checkBlob(digest).isPresent()
                ? StateInTarget.EXISTING
                : StateInTarget.MISSING;

    return new ObtainBaseImageLayerStep(
        buildContext,
        progressEventDispatcherFactory,
        layer,
        sourceRegistryClient,
        blobExistenceChecker);
  }

  private final BuildContext buildContext;
  private final ProgressEventDispatcher.Factory progressEventDispatcherFactory;

  private final Layer layer;
  private final @Nullable RegistryClient registryClient;
  private final BlobExistenceChecker blobExistenceChecker;

  private ObtainBaseImageLayerStep(
      BuildContext buildContext,
      ProgressEventDispatcher.Factory progressEventDispatcherFactory,
      Layer layer,
      @Nullable RegistryClient registryClient,
      BlobExistenceChecker blobExistenceChecker) {
    this.buildContext = buildContext;
    this.progressEventDispatcherFactory = progressEventDispatcherFactory;
    this.layer = layer;
    this.registryClient = registryClient;
    this.blobExistenceChecker = blobExistenceChecker;
  }

  @Override
  public PreparedLayer call() throws IOException, CacheCorruptedException, RegistryException {
    EventHandlers eventHandlers = buildContext.getEventHandlers();
    DescriptorDigest layerDigest = layer.getBlobDescriptor().getDigest();
    try (ProgressEventDispatcher progressEventDispatcher =
            progressEventDispatcherFactory.create("checking base image layer " + layerDigest, 1);
        TimerEventDispatcher ignored =
            new TimerEventDispatcher(eventHandlers, String.format(DESCRIPTION, layerDigest))) {

      StateInTarget stateInTarget = blobExistenceChecker.check(layerDigest);
      if (stateInTarget == StateInTarget.EXISTING) {
        eventHandlers.dispatch(
            LogEvent.info(
                "Skipping pull; BLOB already exists on target registry : "
                    + layer.getBlobDescriptor()));
        return new PreparedLayer.Builder(layer).setStateInTarget(stateInTarget).build();
      }

      Cache cache = buildContext.getBaseImageLayersCache();

      // Checks if the layer already exists in the cache.
      Optional<CachedLayer> optionalCachedLayer = cache.retrieve(layerDigest);
      if (optionalCachedLayer.isPresent()) {
        CachedLayer cachedLayer = optionalCachedLayer.get();
        return new PreparedLayer.Builder(cachedLayer).setStateInTarget(stateInTarget).build();
      } else if (buildContext.isOffline()) {
        throw new IOException(
            "Cannot run Jib in offline mode; local Jib cache for base image is missing image layer "
                + layerDigest
                + ". Rerun Jib in online mode with \"-Djib.alwaysCacheBaseImage=true\" to "
                + "re-download the base image layers.");
      }

      // If another build in this JVM is already pulling the same layer, reuse its result.
      Map.Entry<Path, DescriptorDigest> pullKey =
          new SimpleEntry<>(cache.getDirectory(), layerDigest);
      CompletableFuture<CachedLayer> thisPull = new CompletableFuture<>();
      CompletableFuture<CachedLayer> otherPull = inFlightPulls.putIfAbsent(pullKey, thisPull);
      if (otherPull != null) {
        CachedLayer sharedLayer = waitForOtherPull(otherPull);
        if (sharedLayer != null) {
          return new PreparedLayer.Builder(sharedLayer).setStateInTarget(stateInTarget).build();
        }
        // The other pull failed; fall through and pull the layer here.
      }

      try (ThrottledProgressEventDispatcherWrapper progressEventDispatcherWrapper =
          new ThrottledProgressEventDispatcherWrapper(
              progressEventDispatcher.newChildProducer(),
              "pulling base image layer " + layerDigest)) {
        CachedLayer cachedLayer =
            cache.writeCompressedLayer(
                Verify.verifyNotNull(registryClient)
                    .pullBlob(
                        layerDigest,
                        progressEventDispatcherWrapper::setProgressTarget,
                        progressEventDispatcherWrapper::dispatchProgress));
        thisPull.complete(cachedLayer);
        return new PreparedLayer.Builder(cachedLayer).setStateInTarget(stateInTarget).build();

      } finally {
        // No-op if the pull completed; otherwise lets waiting builds fall back to pulling.
        thisPull.completeExceptionally(new IOException("Pull of " + layerDigest + " failed"));
        inFlightPulls.remove(pullKey, thisPull);
      }
    }
  }

  /**
   * Waits for a pull of the same layer started by another build.
   *
   * @return the pulled layer, or {@code null} if that pull failed
   */
  @Nullable
  private static CachedLayer waitForOtherPull(CompletableFuture<CachedLayer> otherPull)
      throws IOException {
    try {
      return otherPull.get();

    } catch (ExecutionException ex) {
      return null;

    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IOException("Interrupted while waiting for the layer to be pulled", ex);
    }
  }
}
