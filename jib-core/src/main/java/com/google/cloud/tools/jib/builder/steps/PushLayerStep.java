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

import com.google.cloud.tools.jib.api.RegistryException;
import com.google.cloud.tools.jib.blob.BlobDescriptor;
import com.google.cloud.tools.jib.builder.ProgressEventDispatcher;
import com.google.cloud.tools.jib.builder.TimerEventDispatcher;
import com.google.cloud.tools.jib.configuration.BuildConfiguration;
import com.google.cloud.tools.jib.http.Authorization;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import javax.annotation.Nullable;

class PushLayerStep implements Callable<BlobDescriptor> {

  static ImmutableList<PushLayerStep> makeList(
      BuildConfiguration buildConfiguration,
      ProgressEventDispatcher.Factory progressEventDispatcherFactory,
      @Nullable Authorization pushAuthorization,
      List<Future<PreparedLayer>> cachedLayers) {
    try (TimerEventDispatcher ignored =
            new TimerEventDispatcher(
                buildConfiguration.getEventHandlers(), "Preparing application layer pushers");
        ProgressEventDispatcher progressEventDispatcher =
            progressEventDispatcherFactory.create(
                "preparing application layer pushers", cachedLayers.size())) {

      // Constructs a PushBlobStep for each layer.
      return cachedLayers
          .stream()
          .map(
              layer ->
                  new PushLayerStep(
                      buildConfiguration,
                      progressEventDispatcher.newChildProducer(),
                      pushAuthorization,
                      layer))
          .collect(ImmutableList.toImmutableList());
    }
  }

  private final BuildConfiguration buildConfiguration;
  private final ProgressEventDispatcher.Factory progressEventDispatcherFactory;

  @Nullable private final Authorization pushAuthorization;
  private final Future<PreparedLayer> preparedLayer;

  PushLayerStep(
      BuildConfiguration buildConfiguration,
      ProgressEventDispatcher.Factory progressEventDispatcherFactory,
      @Nullable Authorization pushAuthorization,
      Future<PreparedLayer> preparedLayer) {
    this.buildConfiguration = buildConfiguration;
    this.progressEventDispatcherFactory = progressEventDispatcherFactory;
    this.pushAuthorization = pushAuthorization;
    this.preparedLayer = preparedLayer;
  }

  @Override
  public BlobDescriptor call()
      throws IOException, RegistryException, ExecutionException, InterruptedException {
    PreparedLayer layer = preparedLayer.get();
    if (layer.existsInTarget().orElse(false)) { // skip pushing if known to exist in registry
      return layer.getBlobDescriptor();
    }

    boolean doneBlobCheck = layer.existsInTarget().isPresent();
    boolean doBlobCheck = !doneBlobCheck;
    return new PushBlobStep(
            buildConfiguration,
            progressEventDispatcherFactory,
            pushAuthorization,
            layer.getBlobDescriptor(),
            layer.getBlob(),
            doBlobCheck)
        .call();
  }
}
