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
import com.google.cloud.tools.jib.async.NonBlockingSteps;
import com.google.cloud.tools.jib.blob.Blob;
import com.google.cloud.tools.jib.blob.BlobDescriptor;
import com.google.cloud.tools.jib.builder.TimerEventDispatcher;
import com.google.cloud.tools.jib.configuration.BuildConfiguration;
import com.google.cloud.tools.jib.configuration.ContainerConfiguration;
import com.google.cloud.tools.jib.image.DescriptorDigest;
import com.google.cloud.tools.jib.image.Image;
import com.google.cloud.tools.jib.image.Layer;
import com.google.cloud.tools.jib.image.LayerPropertyNotFoundException;
import com.google.cloud.tools.jib.image.json.HistoryEntry;
import com.google.cloud.tools.jib.ncache.CacheEntry;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

/** Builds a model {@link Image}. */
class BuildImageStep
    implements AsyncStep<AsyncStep<Image<Layer>>>, Callable<AsyncStep<Image<Layer>>> {

  private static final String DESCRIPTION = "Building container configuration";

  @VisibleForTesting
  static Layer cacheEntryToLayer(CacheEntry cacheEntry) {
    return new Layer() {

      @Override
      public Blob getBlob() throws LayerPropertyNotFoundException {
        return cacheEntry.getLayerBlob();
      }

      @Override
      public BlobDescriptor getBlobDescriptor() throws LayerPropertyNotFoundException {
        return new BlobDescriptor(cacheEntry.getLayerSize(), cacheEntry.getLayerDigest());
      }

      @Override
      public DescriptorDigest getDiffId() throws LayerPropertyNotFoundException {
        return cacheEntry.getLayerDiffId();
      }
    };
  }

  private final BuildConfiguration buildConfiguration;
  private final PullBaseImageStep pullBaseImageStep;
  private final PullAndCacheBaseImageLayersStep pullAndCacheBaseImageLayersStep;
  private final ImmutableList<BuildAndCacheApplicationLayerStep> buildAndCacheApplicationLayerSteps;

  private final ListeningExecutorService listeningExecutorService;
  private final ListenableFuture<AsyncStep<Image<Layer>>> listenableFuture;

  BuildImageStep(
      ListeningExecutorService listeningExecutorService,
      BuildConfiguration buildConfiguration,
      PullBaseImageStep pullBaseImageStep,
      PullAndCacheBaseImageLayersStep pullAndCacheBaseImageLayersStep,
      ImmutableList<BuildAndCacheApplicationLayerStep> buildAndCacheApplicationLayerSteps) {
    this.listeningExecutorService = listeningExecutorService;
    this.buildConfiguration = buildConfiguration;
    this.pullBaseImageStep = pullBaseImageStep;
    this.pullAndCacheBaseImageLayersStep = pullAndCacheBaseImageLayersStep;
    this.buildAndCacheApplicationLayerSteps = buildAndCacheApplicationLayerSteps;

    listenableFuture =
        Futures.whenAllSucceed(
                pullBaseImageStep.getFuture(), pullAndCacheBaseImageLayersStep.getFuture())
            .call(this, listeningExecutorService);
  }

  @Override
  public ListenableFuture<AsyncStep<Image<Layer>>> getFuture() {
    return listenableFuture;
  }

  @Override
  public AsyncStep<Image<Layer>> call() throws ExecutionException {
    List<ListenableFuture<?>> dependencies = new ArrayList<>();

    for (PullAndCacheBaseImageLayerStep pullAndCacheBaseImageLayerStep :
        NonBlockingSteps.get(pullAndCacheBaseImageLayersStep)) {
      dependencies.add(pullAndCacheBaseImageLayerStep.getFuture());
    }
    for (BuildAndCacheApplicationLayerStep buildAndCacheApplicationLayerStep :
        buildAndCacheApplicationLayerSteps) {
      dependencies.add(buildAndCacheApplicationLayerStep.getFuture());
    }
    ListenableFuture<Image<Layer>> future =
        Futures.whenAllSucceed(dependencies)
            .call(this::afterCacheEntrySteps, listeningExecutorService);
    return () -> future;
  }

  private Image<Layer> afterCacheEntrySteps()
      throws ExecutionException, LayerPropertyNotFoundException {
    try (TimerEventDispatcher ignored =
        new TimerEventDispatcher(buildConfiguration.getEventDispatcher(), DESCRIPTION)) {
      // Constructs the image.
      Image.Builder<Layer> imageBuilder = Image.builder();
      Image<Layer> baseImage = NonBlockingSteps.get(pullBaseImageStep).getBaseImage();
      ContainerConfiguration containerConfiguration =
          buildConfiguration.getContainerConfiguration();

      // Base image layers
      List<PullAndCacheBaseImageLayerStep> baseImageLayers =
          NonBlockingSteps.get(pullAndCacheBaseImageLayersStep);
      for (PullAndCacheBaseImageLayerStep pullAndCacheBaseImageLayerStep : baseImageLayers) {
        imageBuilder.addLayer(
            cacheEntryToLayer(NonBlockingSteps.get(pullAndCacheBaseImageLayerStep)));
      }

      // Passthrough config and count non-empty history entries
      int nonEmptyLayerCount = 0;
      for (HistoryEntry historyObject : baseImage.getHistory()) {
        imageBuilder.addHistory(historyObject);
        if (!historyObject.hasCorrespondingLayer()) {
          nonEmptyLayerCount++;
        }
      }
      imageBuilder.addEnvironment(baseImage.getEnvironment());
      imageBuilder.addLabels(baseImage.getLabels());
      imageBuilder.setWorkingDirectory(baseImage.getWorkingDirectory());

      // Add history elements for non-empty layers that don't have one yet
      Instant layerCreationTime =
          containerConfiguration == null
              ? ContainerConfiguration.DEFAULT_CREATION_TIME
              : containerConfiguration.getCreationTime();
      for (int count = 0; count < baseImageLayers.size() - nonEmptyLayerCount; count++) {
        imageBuilder.addHistory(
            HistoryEntry.builder()
                .setCreationTimestamp(layerCreationTime)
                .setComment("auto-generated by Jib")
                .build());
      }

      // Add built layers/configuration
      for (BuildAndCacheApplicationLayerStep buildAndCacheApplicationLayerStep :
          buildAndCacheApplicationLayerSteps) {
        imageBuilder.addLayer(
            cacheEntryToLayer(NonBlockingSteps.get(buildAndCacheApplicationLayerStep)));
        imageBuilder.addHistory(
            HistoryEntry.builder()
                .setCreationTimestamp(layerCreationTime)
                .setAuthor("Jib")
                .setCreatedBy(buildConfiguration.getToolName())
                .build());
      }
      if (containerConfiguration != null) {
        imageBuilder.addEnvironment(containerConfiguration.getEnvironmentMap());
        imageBuilder.setCreated(containerConfiguration.getCreationTime());
        imageBuilder.setEntrypoint(containerConfiguration.getEntrypoint());
        imageBuilder.setJavaArguments(containerConfiguration.getProgramArguments());
        imageBuilder.setExposedPorts(containerConfiguration.getExposedPorts());
        imageBuilder.addLabels(containerConfiguration.getLabels());
      }

      // Gets the container configuration content descriptor.
      return imageBuilder.build();
    }
  }
}
