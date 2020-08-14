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

import com.google.cloud.tools.jib.api.LogEvent;
import com.google.cloud.tools.jib.blob.BlobDescriptor;
import com.google.cloud.tools.jib.blob.Blobs;
import com.google.cloud.tools.jib.builder.ProgressEventDispatcher;
import com.google.cloud.tools.jib.builder.TimerEventDispatcher;
import com.google.cloud.tools.jib.configuration.BuildContext;
import com.google.cloud.tools.jib.event.EventHandlers;
import com.google.cloud.tools.jib.image.Image;
import com.google.cloud.tools.jib.image.json.ImageToJsonTranslator;
import com.google.cloud.tools.jib.image.json.ManifestListGenerator;
import com.google.cloud.tools.jib.image.json.ManifestTemplate;
import com.google.cloud.tools.jib.json.JsonTemplate;
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Callable;

/** Creates a manifest list. */
class BuildManifestListStep implements Callable<ManifestTemplate> {

  private static final String DESCRIPTION = "Creating a manifest list";

  private final BuildContext buildContext;
  private final ProgressEventDispatcher.Factory progressEventDispatcherFactory;
  private final List<Image> builtImages;

  BuildManifestListStep(
      BuildContext buildContext,
      ProgressEventDispatcher.Factory progressEventDispatcherFactory,
      List<Image> builtImages) {
    this.buildContext = buildContext;
    this.progressEventDispatcherFactory = progressEventDispatcherFactory;
    this.builtImages = builtImages;
  }

  @Override
  public ManifestTemplate call() throws IOException {

    EventHandlers eventHandlers = buildContext.getEventHandlers();
    try (TimerEventDispatcher ignored = new TimerEventDispatcher(eventHandlers, DESCRIPTION);
        ProgressEventDispatcher ignored2 =
            progressEventDispatcherFactory.create("creating a manifest list", 1)) {
      eventHandlers.dispatch(LogEvent.info("Creating a manifest list"));
    }
    if (builtImages.size() == 1) {
      JsonTemplate containerConfiguration =
          new ImageToJsonTranslator(builtImages.get(0)).getContainerConfiguration();
      BlobDescriptor configDescriptor =
          Blobs.from(containerConfiguration).writeTo(ByteStreams.nullOutputStream());

      return new ImageToJsonTranslator(builtImages.get(0))
          .getManifestTemplate(buildContext.getTargetFormat(), configDescriptor);
    }

    return new ManifestListGenerator(this.buildContext, this.builtImages).getManifestListTemplate();
  }
}
