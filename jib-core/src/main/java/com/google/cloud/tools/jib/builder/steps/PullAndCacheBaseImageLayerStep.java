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

import com.google.cloud.tools.jib.async.AsyncStep;
import com.google.cloud.tools.jib.builder.ProgressEventDispatcher;
import com.google.cloud.tools.jib.builder.TimerEventDispatcher;
import com.google.cloud.tools.jib.cache.Cache;
import com.google.cloud.tools.jib.cache.CacheCorruptedException;
import com.google.cloud.tools.jib.cache.CachedLayer;
import com.google.cloud.tools.jib.configuration.BuildConfiguration;
import com.google.cloud.tools.jib.http.Authorization;
import com.google.cloud.tools.jib.image.DescriptorDigest;
import com.google.cloud.tools.jib.registry.RegistryClient;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.io.Closeable;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.Callable;
import javax.annotation.Nullable;

/** Pulls and caches a single base image layer. */
class PullAndCacheBaseImageLayerStep implements AsyncStep<CachedLayer>, Callable<CachedLayer> {

  /**
   * Contains a {@link ProgressEventDispatcher}. This class is mutable and should only be used
   * within a local context.
   *
   * <p>This class is necessary because the total BLOb size (allocation units) is not known until
   * the response headers are received, only after which can the {@link ProgressEventDispatcher} be
   * created.
   */
  private static class ProgressEventDispatcherContainer implements Closeable {

    private final ProgressEventDispatcher.Factory progressEventDispatcherFactory;
    @Nullable private ProgressEventDispatcher progressEventDispatcher;

    private ProgressEventDispatcherContainer(
        ProgressEventDispatcher.Factory progressEventDispatcherFactory) {
      this.progressEventDispatcherFactory = progressEventDispatcherFactory;
    }

    private void initializeWithAllocationUnits(String description, long allocationUnits) {
      Preconditions.checkState(progressEventDispatcher == null);
      progressEventDispatcher = progressEventDispatcherFactory.create(description, allocationUnits);
    }

    private void dispatchProgress(long progressUnits) {
      Preconditions.checkNotNull(progressEventDispatcher);
      progressEventDispatcher.dispatchProgress(progressUnits);
    }

    @Override
    public void close() {
      Preconditions.checkNotNull(progressEventDispatcher);
      progressEventDispatcher.close();
    }
  }

  private static final String DESCRIPTION = "Pulling base image layer %s";

  private final BuildConfiguration buildConfiguration;
  private final ProgressEventDispatcher.Factory progressEventDispatcherFactory;

  private final DescriptorDigest layerDigest;
  private final @Nullable Authorization pullAuthorization;

  private final ListenableFuture<CachedLayer> listenableFuture;

  PullAndCacheBaseImageLayerStep(
      ListeningExecutorService listeningExecutorService,
      BuildConfiguration buildConfiguration,
      ProgressEventDispatcher.Factory progressEventDispatcherFactory,
      DescriptorDigest layerDigest,
      @Nullable Authorization pullAuthorization) {
    this.buildConfiguration = buildConfiguration;
    this.progressEventDispatcherFactory = progressEventDispatcherFactory;
    this.layerDigest = layerDigest;
    this.pullAuthorization = pullAuthorization;

    listenableFuture = listeningExecutorService.submit(this);
  }

  @Override
  public ListenableFuture<CachedLayer> getFuture() {
    return listenableFuture;
  }

  @Override
  public CachedLayer call() throws IOException, CacheCorruptedException {
    try (ProgressEventDispatcher progressEventDispatcher =
            progressEventDispatcherFactory.create("pull base image layer " + layerDigest, 1);
        TimerEventDispatcher ignored =
            new TimerEventDispatcher(
                buildConfiguration.getEventDispatcher(), String.format(DESCRIPTION, layerDigest))) {
      Cache cache = buildConfiguration.getBaseImageLayersCache();

      // Checks if the layer already exists in the cache.
      Optional<CachedLayer> optionalCachedLayer = cache.retrieve(layerDigest);
      if (optionalCachedLayer.isPresent()) {
        return optionalCachedLayer.get();
      }

      RegistryClient registryClient =
          buildConfiguration
              .newBaseImageRegistryClientFactory()
              .setAuthorization(pullAuthorization)
              .newRegistryClient();

      try (ProgressEventDispatcherContainer progressEventDispatcherContainer =
          new ProgressEventDispatcherContainer(progressEventDispatcher.newChildProducer())) {
        return cache.writeCompressedLayer(
            registryClient.pullBlob(
                layerDigest,
                blobSize ->
                    progressEventDispatcherContainer.initializeWithAllocationUnits(
                        "pull base image layer blob " + layerDigest, blobSize),
                progressEventDispatcherContainer::dispatchProgress));
      }
    }
  }
}
