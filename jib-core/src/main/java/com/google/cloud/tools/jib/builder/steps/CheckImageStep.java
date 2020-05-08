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

import com.google.cloud.tools.jib.api.DescriptorDigest;
import com.google.cloud.tools.jib.api.LogEvent;
import com.google.cloud.tools.jib.api.RegistryException;
import com.google.cloud.tools.jib.configuration.BuildContext;
import com.google.cloud.tools.jib.event.EventHandlers;
import com.google.cloud.tools.jib.global.JibSystemProperties;
import com.google.cloud.tools.jib.image.ManifestDescriptor;
import com.google.cloud.tools.jib.image.json.BuildableManifestTemplate;
import com.google.cloud.tools.jib.registry.RegistryClient;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.Callable;

/**
 * Checks the existence of a manifest.
 */
class CheckImageStep implements Callable<Optional<ManifestDescriptor<BuildableManifestTemplate>>> {

  BuildContext buildContext;
  RegistryClient registryClient;
  DescriptorDigest manifestDigest;

  CheckImageStep(
      BuildContext buildContext,
      RegistryClient registryClient,
      DescriptorDigest manifestDigest) {
    this.buildContext = buildContext;
    this.registryClient = registryClient;
    this.manifestDigest = manifestDigest;
  }

  @Override
  public Optional<ManifestDescriptor<BuildableManifestTemplate>> call()
      throws IOException, RegistryException {

    EventHandlers eventHandlers = buildContext.getEventHandlers();

    if (!JibSystemProperties.skipExistingImages()) {
      eventHandlers.dispatch(
          LogEvent.info(
              "Skipping manifest existence check; system property set to false"));

      return Optional.empty();
    }

    return registryClient.checkImage(manifestDigest);
  }
}