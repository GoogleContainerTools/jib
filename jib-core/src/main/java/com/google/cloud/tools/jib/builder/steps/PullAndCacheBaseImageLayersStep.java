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

import com.google.cloud.tools.jib.builder.ProgressEventDispatcher;
import com.google.cloud.tools.jib.builder.TimerEventDispatcher;
import com.google.cloud.tools.jib.builder.steps.PullBaseImageStep.ImageAndAuthorization;
import com.google.cloud.tools.jib.configuration.BuildConfiguration;
import com.google.cloud.tools.jib.image.Layer;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;

/** Pulls and caches the base image layers. */
// TODO: following the same pattern as "BuildAndCacheApplicationLayerStep", move the sole "makeList"
// static into "PullAndCacheBaseImageLayerStep" and remove this class.
class PullAndCacheBaseImageLayersStep {

  private static final String DESCRIPTION = "Preparing base image layer pullers";

  static ImmutableList<PullAndCacheBaseImageLayerStep> makeList(
      BuildConfiguration buildConfiguration,
      ProgressEventDispatcher.Factory progressEventDispatcherFactory,
      ImageAndAuthorization baseImageAndAuth) {
    ImmutableList<Layer> baseImageLayers = baseImageAndAuth.getImage().getLayers();

    try (ProgressEventDispatcher progressEventDispatcher =
            progressEventDispatcherFactory.create(
                "preparing base image layer pullers", baseImageLayers.size());
        TimerEventDispatcher ignored =
            new TimerEventDispatcher(buildConfiguration.getEventHandlers(), DESCRIPTION)) {

      List<PullAndCacheBaseImageLayerStep> layerPullers = new ArrayList<>();
      for (Layer layer : baseImageLayers) {
        layerPullers.add(
            new PullAndCacheBaseImageLayerStep(
                buildConfiguration,
                progressEventDispatcher.newChildProducer(),
                layer.getBlobDescriptor().getDigest(),
                baseImageAndAuth.getAuthorization()));
      }
      return ImmutableList.copyOf(layerPullers);
    }
  }
}
