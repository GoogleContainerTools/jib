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
import com.google.cloud.tools.jib.configuration.BuildConfiguration;
import com.google.cloud.tools.jib.hash.Digests;
import com.google.cloud.tools.jib.http.Authorization;
import com.google.cloud.tools.jib.image.Image;
import com.google.cloud.tools.jib.image.json.BuildableManifestTemplate;
import com.google.cloud.tools.jib.image.json.ImageToJsonTranslator;
import com.google.cloud.tools.jib.registry.RegistryClient;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * Pushes the final image. In fact, its manifest is pushed along with a tag. Returns the pushed
 * image digest (digest of manifest) and image ID (digest of container configuration) as {#link
 * BuildResult}.
 */
class PushImageStep implements Callable<BuildResult> {

  private static final String DESCRIPTION = "Pushing new image (tag)";

  private final BuildConfiguration buildConfiguration;
  private final ProgressEventDispatcher.Factory progressEventDispatcherFactory;

  private final BuildableManifestTemplate manifestTemplate;
  private final Authorization pushAuthorization;
  private final String tag;
  private final DescriptorDigest imageDigest;
  private final DescriptorDigest imageId;

  PushImageStep(
      BuildConfiguration buildConfiguration,
      ProgressEventDispatcher.Factory progressEventDispatcherFactory,
      Authorization pushAuthorization,
      BuildableManifestTemplate manifestTemplate,
      String tag,
      DescriptorDigest imageDigest,
      DescriptorDigest imageId) {
    this.buildConfiguration = buildConfiguration;
    this.progressEventDispatcherFactory = progressEventDispatcherFactory;
    this.pushAuthorization = pushAuthorization;
    this.manifestTemplate = manifestTemplate;
    this.tag = tag;
    this.imageDigest = imageDigest;
    this.imageId = imageId;
  }

  static ImmutableList<PushImageStep> makeList(
      BuildConfiguration buildConfiguration,
      ProgressEventDispatcher.Factory progressEventDispatcherFactory,
      Authorization pushAuthorization,
      BlobDescriptor containerConfigurationDigestAndSize,
      Image builtImage)
      throws IOException {
    Set<String> tags = buildConfiguration.getAllTargetImageTags();

    try (TimerEventDispatcher ignored =
            new TimerEventDispatcher(
                buildConfiguration.getEventHandlers(), "Preparing image (tag) pushers");
        ProgressEventDispatcher progressEventDispatcher =
            progressEventDispatcherFactory.create("preparing (image) tag pushers", tags.size())) {

      // Gets the image manifest to push.
      BuildableManifestTemplate manifestTemplate =
          new ImageToJsonTranslator(builtImage)
              .getManifestTemplate(
                  buildConfiguration.getTargetFormat(), containerConfigurationDigestAndSize);

      ImmutableList.Builder<PushImageStep> imagePushers = new ImmutableList.Builder<>();
      for (String tag : buildConfiguration.getAllTargetImageTags()) {
        imagePushers.add(
            new PushImageStep(
                buildConfiguration,
                progressEventDispatcher.newChildProducer(),
                pushAuthorization,
                manifestTemplate,
                tag,
                Digests.computeJsonDigest(manifestTemplate),
                containerConfigurationDigestAndSize.getDigest()));
      }
      return imagePushers.build();
    }
  }

  @Override
  public BuildResult call() throws IOException, RegistryException {
    try (TimerEventDispatcher ignored =
            new TimerEventDispatcher(buildConfiguration.getEventHandlers(), DESCRIPTION);
        ProgressEventDispatcher ignored2 =
            progressEventDispatcherFactory.create("tagging with " + tag, 1)) {
      buildConfiguration.getEventHandlers().dispatch(LogEvent.info("Tagging with " + tag + "..."));

      RegistryClient registryClient =
          buildConfiguration
              .newTargetImageRegistryClientFactory()
              .setAuthorization(pushAuthorization)
              .newRegistryClient();

      registryClient.pushManifest(manifestTemplate, tag);
      return new BuildResult(imageDigest, imageId);
    }
  }
}
