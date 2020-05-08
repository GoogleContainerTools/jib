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
import com.google.cloud.tools.jib.blob.BlobDescriptor;
import com.google.cloud.tools.jib.builder.ProgressEventDispatcher;
import com.google.cloud.tools.jib.builder.TimerEventDispatcher;
import com.google.cloud.tools.jib.configuration.BuildContext;
import com.google.cloud.tools.jib.event.EventHandlers;
import com.google.cloud.tools.jib.image.json.BuildableManifestTemplate;
import com.google.cloud.tools.jib.registry.RegistryClient;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * Pushes a manifest for a tag. Returns the manifest digest ("image digest") and the container
 * configuration digest ("image id") as {#link BuildResult}.
 */
class PushImageStep implements Callable<BuildResult> {

  private static final String DESCRIPTION = "Pushing manifest";

  static ImmutableList<PushImageStep> makeList(
      BuildContext buildContext,
      ProgressEventDispatcher.Factory progressEventDispatcherFactory,
      RegistryClient registryClient,
      BlobDescriptor containerConfigurationDigestAndSize,
      BuildableManifestTemplate manifestTemplate,
      DescriptorDigest imageDigest) {
    Set<String> tags = buildContext.getAllTargetImageTags();

    try (TimerEventDispatcher ignored =
            new TimerEventDispatcher(
                buildContext.getEventHandlers(), "Preparing manifest pushers");
        ProgressEventDispatcher progressEventDispatcher =
            progressEventDispatcherFactory.create("launching manifest pushers", tags.size())) {

      return tags.stream()
          .map(
              tag ->
                  new PushImageStep(
                      buildContext,
                      progressEventDispatcher.newChildProducer(),
                      registryClient,
                      manifestTemplate,
                      tag,
                      imageDigest,
                      containerConfigurationDigestAndSize.getDigest()))
          .collect(ImmutableList.toImmutableList());
    }
  }

  private final BuildContext buildContext;
  private final ProgressEventDispatcher.Factory progressEventDispatcherFactory;

  private final BuildableManifestTemplate manifestTemplate;
  private final RegistryClient registryClient;
  private final String tag;
  private final DescriptorDigest imageDigest;
  private final DescriptorDigest imageId;

  PushImageStep(
      BuildContext buildContext,
      ProgressEventDispatcher.Factory progressEventDispatcherFactory,
      RegistryClient registryClient,
      BuildableManifestTemplate manifestTemplate,
      String tag,
      DescriptorDigest imageDigest,
      DescriptorDigest imageId) {
    this.buildContext = buildContext;
    this.progressEventDispatcherFactory = progressEventDispatcherFactory;
    this.registryClient = registryClient;
    this.manifestTemplate = manifestTemplate;
    this.tag = tag;
    this.imageDigest = imageDigest;
    this.imageId = imageId;
  }

  @Override
  public BuildResult call() throws IOException, RegistryException {
    EventHandlers eventHandlers = buildContext.getEventHandlers();
    try (TimerEventDispatcher ignored = new TimerEventDispatcher(eventHandlers, DESCRIPTION);
        ProgressEventDispatcher ignored2 =
            progressEventDispatcherFactory.create("pushing manifest for " + tag, 1)) {
      eventHandlers.dispatch(LogEvent.info("Pushing manifest for " + tag + "..."));

      registryClient.pushManifest(manifestTemplate, tag);
      return new BuildResult(imageDigest, imageId);
    }
  }
}
