/*
 * Copyright 2020 Google LLC.
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

import com.google.api.client.util.Preconditions;
import com.google.cloud.tools.jib.api.LogEvent;
import com.google.cloud.tools.jib.blob.BlobDescriptor;
import com.google.cloud.tools.jib.builder.ProgressEventDispatcher;
import com.google.cloud.tools.jib.builder.TimerEventDispatcher;
import com.google.cloud.tools.jib.configuration.BuildContext;
import com.google.cloud.tools.jib.event.EventHandlers;
import com.google.cloud.tools.jib.hash.Digests;
import com.google.cloud.tools.jib.image.Image;
import com.google.cloud.tools.jib.image.json.ImageToJsonTranslator;
import com.google.cloud.tools.jib.image.json.ManifestListGenerator;
import com.google.cloud.tools.jib.image.json.ManifestTemplate;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Callable;

/** Builds a manifest list or a single manifest. */
class BuildManifestListOrSingleManifestStep implements Callable<ManifestTemplate> {

  private static final String DESCRIPTION = "Building a manifest list or a single manifest";

  private final BuildContext buildContext;
  private final ProgressEventDispatcher.Factory progressEventDispatcherFactory;
  private final List<Image> builtImages;

  BuildManifestListOrSingleManifestStep(
      BuildContext buildContext,
      ProgressEventDispatcher.Factory progressEventDispatcherFactory,
      List<Image> builtImages) {
    this.buildContext = buildContext;
    this.progressEventDispatcherFactory = progressEventDispatcherFactory;
    this.builtImages = builtImages;
  }

  @Override
  public ManifestTemplate call() throws IOException {
    Preconditions.checkState(!builtImages.isEmpty(), "no images given");
    EventHandlers eventHandlers = buildContext.getEventHandlers();
    try (TimerEventDispatcher ignored = new TimerEventDispatcher(eventHandlers, DESCRIPTION);
        ProgressEventDispatcher ignored2 =
            progressEventDispatcherFactory.create(
                "building a manifest list or a single manifest", 1)) {

      if (builtImages.size() == 1) {
        eventHandlers.dispatch(LogEvent.info("Building a single manifest"));
        ImageToJsonTranslator imageTranslator = new ImageToJsonTranslator(builtImages.get(0));
        BlobDescriptor configDescriptor =
            Digests.computeDigest(imageTranslator.getContainerConfiguration());
        return imageTranslator.getManifestTemplate(
            buildContext.getTargetFormat(), configDescriptor);
      }

      eventHandlers.dispatch(LogEvent.info("Building a manifest list"));
      return new ManifestListGenerator(builtImages)
          .getManifestListTemplate(buildContext.getTargetFormat());
    }
  }
}
