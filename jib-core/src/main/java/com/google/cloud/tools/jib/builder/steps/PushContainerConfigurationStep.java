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
import com.google.cloud.tools.jib.blob.Blobs;
import com.google.cloud.tools.jib.builder.ProgressEventDispatcher;
import com.google.cloud.tools.jib.builder.TimerEventDispatcher;
import com.google.cloud.tools.jib.configuration.BuildContext;
import com.google.cloud.tools.jib.hash.Digests;
import com.google.cloud.tools.jib.image.Image;
import com.google.cloud.tools.jib.image.json.ImageToJsonTranslator;
import com.google.cloud.tools.jib.json.JsonTemplate;
import com.google.cloud.tools.jib.registry.RegistryClient;
import java.io.IOException;
import java.util.concurrent.Callable;

/** Pushes the container configuration. */
class PushContainerConfigurationStep implements Callable<BlobDescriptor> {

  private static final String DESCRIPTION = "Pushing container configuration";

  private final BuildContext buildContext;
  private final ProgressEventDispatcher.Factory progressEventDispatcherFactory;

  private final RegistryClient registryClient;
  private final Image builtImage;

  PushContainerConfigurationStep(
      BuildContext buildContext,
      ProgressEventDispatcher.Factory progressEventDispatcherFactory,
      RegistryClient registryClient,
      Image builtImage) {
    this.buildContext = buildContext;
    this.progressEventDispatcherFactory = progressEventDispatcherFactory;
    this.registryClient = registryClient;
    this.builtImage = builtImage;
  }

  @Override
  public BlobDescriptor call() throws IOException, RegistryException {
    try (ProgressEventDispatcher progressEventDispatcher =
            progressEventDispatcherFactory.create("pushing container configuration", 1);
        TimerEventDispatcher ignored =
            new TimerEventDispatcher(buildContext.getEventHandlers(), DESCRIPTION)) {
      JsonTemplate containerConfiguration =
          new ImageToJsonTranslator(builtImage).getContainerConfiguration();

      return new PushBlobStep(
              buildContext,
              progressEventDispatcher.newChildProducer(),
              registryClient,
              Digests.computeDigest(containerConfiguration),
              Blobs.from(containerConfiguration),
              false)
          .call();
    }
  }
}
