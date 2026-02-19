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

import com.google.cloud.tools.jib.api.LogEvent;
import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer;
import com.google.cloud.tools.jib.api.buildplan.FileEntry;
import com.google.cloud.tools.jib.api.buildplan.LayerObject;
import com.google.cloud.tools.jib.api.buildplan.Platform;
import com.google.cloud.tools.jib.api.buildplan.PlatformDependentLayer;
import com.google.cloud.tools.jib.blob.Blob;
import com.google.cloud.tools.jib.builder.ProgressEventDispatcher;
import com.google.cloud.tools.jib.builder.TimerEventDispatcher;
import com.google.cloud.tools.jib.cache.Cache;
import com.google.cloud.tools.jib.cache.CacheCorruptedException;
import com.google.cloud.tools.jib.cache.CachedLayer;
import com.google.cloud.tools.jib.configuration.BuildContext;
import com.google.cloud.tools.jib.event.EventHandlers;
import com.google.cloud.tools.jib.image.ReproducibleLayerBuilder;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

/** Builds and caches application layers. */
class BuildAndCacheApplicationLayerStep implements Callable<PreparedLayer> {

  @SuppressWarnings("InlineFormatString")
  private static final String DESCRIPTION = "Building %s layer";

  /**
   * Makes a list of {@link BuildAndCacheApplicationLayerStep} for dependencies, resources, and
   * classes layers. Optionally adds an extra layer if configured to do so.
   */
  static ImmutableList<BuildAndCacheApplicationLayerStep> makeList(
      BuildContext buildContext, ProgressEventDispatcher.Factory progressEventDispatcherFactory) {
    List<? extends LayerObject> layerConfigurations = buildContext.getLayerConfigurations();
    int fileLayerCount = 0;
    for (LayerObject layerConfiguration : layerConfigurations) {
      if (layerConfiguration instanceof FileEntriesLayer) {
        fileLayerCount++;
      } else if (layerConfiguration instanceof PlatformDependentLayer) {
        fileLayerCount += ((PlatformDependentLayer) layerConfiguration).getEntries().size();
      }
    }

    try (ProgressEventDispatcher progressEventDispatcher =
            progressEventDispatcherFactory.create(
                "launching application layer builders", fileLayerCount);
        TimerEventDispatcher ignored =
            new TimerEventDispatcher(
                buildContext.getEventHandlers(), "Preparing application layer builders")) {
      return layerConfigurations.stream()
          // Skips the layer if empty.
          .filter(
              layerConfiguration -> {
                if (layerConfiguration instanceof FileEntriesLayer) {
                  return !((FileEntriesLayer) layerConfiguration).getEntries().isEmpty();
                } else if (layerConfiguration instanceof PlatformDependentLayer) {
                  return !((PlatformDependentLayer) layerConfiguration).getEntries().isEmpty();
                }
                return true;
              })
          .flatMap(
              layerConfiguration -> {
                if (layerConfiguration instanceof FileEntriesLayer) {
                  return Stream.of(
                      new BuildAndCacheApplicationLayerStep(
                          buildContext,
                          progressEventDispatcher.newChildProducer(),
                          layerConfiguration.getName(),
                          (FileEntriesLayer) layerConfiguration,
                          new Platform("amd64", "linux")));
                } else if (layerConfiguration instanceof PlatformDependentLayer) {
                  return ((PlatformDependentLayer) layerConfiguration)
                      .getEntries().entrySet().stream()
                          .map(
                              entry -> {
                                FileEntriesLayer fileLayer = entry.getValue();
                                return new BuildAndCacheApplicationLayerStep(
                                    buildContext,
                                    progressEventDispatcher.newChildProducer(),
                                    fileLayer.getName(),
                                    fileLayer,
                                    entry.getKey());
                              });
                } else {
                  throw new UnsupportedOperationException(
                      "Unsupported LayerObject type " + layerConfiguration.getType());
                }
              })
          .collect(ImmutableList.toImmutableList());
    }
  }

  private final BuildContext buildContext;
  private final ProgressEventDispatcher.Factory progressEventDispatcherFactory;

  private final String layerName;
  private final FileEntriesLayer layerConfiguration;
  private final Platform layerPlatform;

  private BuildAndCacheApplicationLayerStep(
      BuildContext buildContext,
      ProgressEventDispatcher.Factory progressEventDispatcherFactory,
      String layerName,
      FileEntriesLayer layerConfiguration,
      Platform layerPlatform) {
    this.buildContext = buildContext;
    this.progressEventDispatcherFactory = progressEventDispatcherFactory;
    this.layerName = layerName;
    this.layerConfiguration = layerConfiguration;
    this.layerPlatform = layerPlatform;
  }

  @Override
  public PreparedLayer call() throws IOException, CacheCorruptedException {
    String description = String.format(DESCRIPTION, layerName);

    EventHandlers eventHandlers = buildContext.getEventHandlers();
    eventHandlers.dispatch(LogEvent.progress(description + "..."));

    try (ProgressEventDispatcher ignored =
            progressEventDispatcherFactory.create("building " + layerName + " layer", 1);
        TimerEventDispatcher ignored2 = new TimerEventDispatcher(eventHandlers, description)) {
      Cache cache = buildContext.getApplicationLayersCache();

      ImmutableList<FileEntry> layerEntries = ImmutableList.copyOf(layerConfiguration.getEntries());
      // Don't build the layer if it exists already.
      Optional<CachedLayer> optionalCachedLayer = cache.retrieve(layerEntries);
      if (optionalCachedLayer.isPresent()) {
        return new PreparedLayer.Builder(optionalCachedLayer.get())
            .setName(layerName)
            .setPlatform(layerPlatform)
            .build();
      }

      Blob layerBlob = new ReproducibleLayerBuilder(layerEntries).build();
      CachedLayer cachedLayer = cache.writeUncompressedLayer(layerBlob, layerEntries);

      eventHandlers.dispatch(LogEvent.debug(description + " built " + cachedLayer.getDigest()));

      return new PreparedLayer.Builder(cachedLayer)
          .setName(layerName)
          .setPlatform(layerPlatform)
          .build();
    }
  }
}
