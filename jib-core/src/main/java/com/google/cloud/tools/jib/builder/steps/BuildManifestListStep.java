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
import com.google.cloud.tools.jib.builder.ProgressEventDispatcher;
import com.google.cloud.tools.jib.builder.TimerEventDispatcher;
import com.google.cloud.tools.jib.configuration.BuildContext;
import com.google.cloud.tools.jib.event.EventHandlers;
import com.google.cloud.tools.jib.image.Image;
import com.google.cloud.tools.jib.image.json.ManifestListGenerator;
import com.google.cloud.tools.jib.image.json.V22ManifestListTemplate;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Callable;

/** Creates a manifest list */
class BuildManifestListStep implements Callable<V22ManifestListTemplate> {

  private static final String DESCRIPTION = "Creating a manifest list";

  private final BuildContext buildContext;
  private final ProgressEventDispatcher.Factory progressEventDispatcherFactory;
  private final List<Image> builtImages;
  private final List<BlobDescriptor> containerConfigPushResults;

  BuildManifestListStep(
      BuildContext buildContext,
      ProgressEventDispatcher.Factory progressEventDispatcherFactory,
      List<Image> builtImages,
      List<BlobDescriptor> containerConfigPushResults) {
    this.buildContext = buildContext;
    this.progressEventDispatcherFactory = progressEventDispatcherFactory;
    this.builtImages = builtImages;
    this.containerConfigPushResults = containerConfigPushResults;
  }

  @Override
  public V22ManifestListTemplate call() throws IOException {

    EventHandlers eventHandlers = buildContext.getEventHandlers();
    try (TimerEventDispatcher ignored = new TimerEventDispatcher(eventHandlers, DESCRIPTION);
        ProgressEventDispatcher ignored2 =
            progressEventDispatcherFactory.create("creating manifest list for", 1)) {

      eventHandlers.dispatch(LogEvent.info("Creating manifest list for"));
    }
    return new ManifestListGenerator(
            this.buildContext, this.builtImages, this.containerConfigPushResults)
        .getManifestListTemplate();
  }
}
