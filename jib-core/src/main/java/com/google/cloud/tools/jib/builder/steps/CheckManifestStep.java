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

import com.google.cloud.tools.jib.api.DescriptorDigest;
import com.google.cloud.tools.jib.api.LogEvent;
import com.google.cloud.tools.jib.api.RegistryException;
import com.google.cloud.tools.jib.builder.ProgressEventDispatcher;
import com.google.cloud.tools.jib.builder.TimerEventDispatcher;
import com.google.cloud.tools.jib.configuration.BuildContext;
import com.google.cloud.tools.jib.event.EventHandlers;
import com.google.cloud.tools.jib.global.JibSystemProperties;
import com.google.cloud.tools.jib.hash.Digests;
import com.google.cloud.tools.jib.image.json.ManifestTemplate;
import com.google.cloud.tools.jib.registry.ManifestAndDigest;
import com.google.cloud.tools.jib.registry.RegistryClient;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.Callable;

/** Checks the existence of a manifest. */
class CheckManifestStep implements Callable<Optional<ManifestAndDigest<ManifestTemplate>>> {

  private static final String DESCRIPTION = "Checking existence of manifest";

  private final BuildContext buildContext;
  private final ProgressEventDispatcher.Factory progressEventDispatcherFactory;
  private final RegistryClient registryClient;
  private final ManifestTemplate manifestTemplate;

  CheckManifestStep(
      BuildContext buildContext,
      ProgressEventDispatcher.Factory progressEventDispatcherFactory,
      RegistryClient registryClient,
      ManifestTemplate manifestTemplate) {
    this.buildContext = buildContext;
    this.progressEventDispatcherFactory = progressEventDispatcherFactory;
    this.registryClient = registryClient;
    this.manifestTemplate = manifestTemplate;
  }

  @Override
  public Optional<ManifestAndDigest<ManifestTemplate>> call()
      throws IOException, RegistryException {
    DescriptorDigest manifestDigest = Digests.computeJsonDigest(manifestTemplate);
    EventHandlers eventHandlers = buildContext.getEventHandlers();
    try (TimerEventDispatcher ignored = new TimerEventDispatcher(eventHandlers, DESCRIPTION);
        ProgressEventDispatcher ignored2 =
            progressEventDispatcherFactory.create(
                "checking existence of manifest for " + manifestDigest, 1)) {
      eventHandlers.dispatch(
          LogEvent.info("Checking existence of manifest for " + manifestDigest + "..."));

      if (!JibSystemProperties.skipExistingImages()) {
        eventHandlers.dispatch(
            LogEvent.info("Skipping manifest existence check; system property set to false"));
        return Optional.empty();
      }

      return registryClient.checkManifest(manifestDigest.toString());
    }
  }
}
