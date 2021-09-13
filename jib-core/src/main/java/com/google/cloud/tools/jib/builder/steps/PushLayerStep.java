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
import com.google.cloud.tools.jib.builder.steps.PreparedLayer.StateInTarget;
import com.google.cloud.tools.jib.configuration.BuildContext;
import com.google.cloud.tools.jib.registry.RegistryClient;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

class PushLayerStep implements Callable<BlobDescriptor> {

  static ImmutableList<PushLayerStep> makeList(
      BuildContext buildContext,
      ProgressEventDispatcher.Factory progressEventDispatcherFactory,
      RegistryClient registryClient,
      List<Future<PreparedLayer>> cachedLayers) {
    try (TimerEventDispatcher ignored =
            new TimerEventDispatcher(buildContext.getEventHandlers(), "Preparing layer pushers");
        ProgressEventDispatcher progressEventDispatcher =
            progressEventDispatcherFactory.create("launching layer pushers", cachedLayers.size())) {

      // Constructs a PushBlobStep for each layer.
      return cachedLayers.stream()
          .map(
              layer ->
                  new PushLayerStep(
                      buildContext,
                      progressEventDispatcher.newChildProducer(),
                      registryClient,
                      layer))
          .collect(ImmutableList.toImmutableList());
    }
  }

  private final BuildContext buildContext;
  private final ProgressEventDispatcher.Factory progressEventDispatcherFactory;

  private final RegistryClient registryClient;
  private final Future<PreparedLayer> preparedLayer;

  private PushLayerStep(
      BuildContext buildContext,
      ProgressEventDispatcher.Factory progressEventDispatcherFactory,
      RegistryClient registryClient,
      Future<PreparedLayer> preparedLayer) {
    this.buildContext = buildContext;
    this.progressEventDispatcherFactory = progressEventDispatcherFactory;
    this.registryClient = registryClient;
    this.preparedLayer = preparedLayer;
  }

  @Override
  public BlobDescriptor call()
      throws IOException, RegistryException, ExecutionException, InterruptedException {
    PreparedLayer layer = preparedLayer.get();

    if (layer.getStateInTarget() == StateInTarget.EXISTING) {
      return layer.getBlobDescriptor(); // skip pushing if known to exist in registry
    }

    boolean forcePush = layer.getStateInTarget() == StateInTarget.MISSING;
    return new PushBlobStep(
            buildContext,
            progressEventDispatcherFactory,
            registryClient,
            layer.getBlobDescriptor(),
            layer.getBlob(),
            forcePush)
        .call();
  }
}
