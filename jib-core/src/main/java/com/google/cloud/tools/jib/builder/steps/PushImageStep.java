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
import com.google.cloud.tools.jib.global.JibSystemProperties;
import com.google.cloud.tools.jib.hash.Digests;
import com.google.cloud.tools.jib.image.Image;
import com.google.cloud.tools.jib.image.json.BuildableManifestTemplate;
import com.google.cloud.tools.jib.image.json.ImageToJsonTranslator;
import com.google.cloud.tools.jib.image.json.ManifestTemplate;
import com.google.cloud.tools.jib.registry.RegistryClient;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

/**
 * Pushes a manifest or a manifest list for a tag. If not a manifest list, returns the manifest
 * digest ("image digest") and the container configuration digest ("image id") as {@link
 * BuildResult}. If a manifest list, returns the manifest list digest only.
 */
// TODO: figure out the right return value and type when pushing a manifest list.
class PushImageStep implements Callable<BuildResult> {

  private static final String DESCRIPTION = "Pushing manifest";

  static ImmutableList<PushImageStep> makeList(
          BuildContext buildContext,
          ProgressEventDispatcher.Factory progressEventDispatcherFactory,
          RegistryClient registryClient,
          BlobDescriptor containerConfigurationDigestAndSize,
          Image builtImage,
          boolean manifestAlreadyExists,
          boolean configForNewTagFeature)
      throws IOException {
    boolean singlePlatform = buildContext.getContainerConfiguration().getPlatforms().size() == 1;
    Set<String> tags = buildContext.getAllTargetImageTags();
    int numPushers = singlePlatform ? tags.size() : 1;

    EventHandlers eventHandlers = buildContext.getEventHandlers();
    try (TimerEventDispatcher ignored =
            new TimerEventDispatcher(eventHandlers, "Preparing manifest pushers");
        ProgressEventDispatcher progressDispatcher =
            progressEventDispatcherFactory.create("launching manifest pushers", numPushers)) {

      if (JibSystemProperties.skipExistingImages() && manifestAlreadyExists) {
        eventHandlers.dispatch(LogEvent.info("Skipping pushing manifest; already exists."));
        return ImmutableList.of();
      }

      // Gets the image manifest to push.
      BuildableManifestTemplate manifestTemplate =
          new ImageToJsonTranslator(builtImage)
              .getManifestTemplate(
                  buildContext.getTargetFormat(), containerConfigurationDigestAndSize);

      DescriptorDigest manifestDigest = Digests.computeJsonDigest(manifestTemplate);

      Set<String> imageQualifiers = singlePlatform ? tags : getChildTags(builtImage, tags, manifestDigest, configForNewTagFeature);

      return imageQualifiers.stream()
          .map(
              qualifier ->
                  new PushImageStep(
                      buildContext,
                      progressDispatcher.newChildProducer(),
                      registryClient,
                      manifestTemplate,
                      qualifier,
                      manifestDigest,
                      containerConfigurationDigestAndSize.getDigest()))
          .collect(ImmutableList.toImmutableList());
    }
  }

  private static Set<String> getChildTags(Image builtImage, Set<String> tags, DescriptorDigest manifestDigest, boolean newTagFeatureEnabled) {
    if(newTagFeatureEnabled){
      String architecture = builtImage.getArchitecture();
      return tags.stream().map(tag -> tag + "-" + architecture).collect(Collectors.toSet());
    }else{
      return Collections.singleton(manifestDigest.toString());
    }
  }

  static ImmutableList<PushImageStep> makeListForManifestList(
      BuildContext buildContext,
      ProgressEventDispatcher.Factory progressEventDispatcherFactory,
      RegistryClient registryClient,
      ManifestTemplate manifestList,
      boolean manifestListAlreadyExists)
      throws IOException {
    Set<String> tags = buildContext.getAllTargetImageTags();

    EventHandlers eventHandlers = buildContext.getEventHandlers();
    try (TimerEventDispatcher ignored =
            new TimerEventDispatcher(eventHandlers, "Preparing manifest list pushers");
        ProgressEventDispatcher progressEventDispatcher =
            progressEventDispatcherFactory.create("launching manifest list pushers", tags.size())) {
      boolean singlePlatform = buildContext.getContainerConfiguration().getPlatforms().size() == 1;
      if (singlePlatform) {
        return ImmutableList.of(); // single image; no need to push a manifest list
      }

      if (JibSystemProperties.skipExistingImages() && manifestListAlreadyExists) {
        eventHandlers.dispatch(LogEvent.info("Skipping pushing manifest list; already exists."));
        return ImmutableList.of();
      }
      DescriptorDigest manifestListDigest = Digests.computeJsonDigest(manifestList);
      return tags.stream()
          .map(
              tag ->
                  new PushImageStep(
                      buildContext,
                      progressEventDispatcher.newChildProducer(),
                      registryClient,
                      manifestList,
                      tag,
                      manifestListDigest,
                      // TODO: a manifest list digest isn't an "image id". Figure out the right
                      // return value and type.
                      manifestListDigest))
          .collect(ImmutableList.toImmutableList());
    }
  }

  private final BuildContext buildContext;
  private final ProgressEventDispatcher.Factory progressEventDispatcherFactory;
  private final RegistryClient registryClient;
  private final ManifestTemplate manifestTemplate;
  private final String imageQualifier;
  private final DescriptorDigest imageDigest;
  private final DescriptorDigest imageId;

  PushImageStep(
      BuildContext buildContext,
      ProgressEventDispatcher.Factory progressEventDispatcherFactory,
      RegistryClient registryClient,
      ManifestTemplate manifestTemplate,
      String imageQualifier,
      DescriptorDigest imageDigest,
      DescriptorDigest imageId) {
    this.buildContext = buildContext;
    this.progressEventDispatcherFactory = progressEventDispatcherFactory;
    this.registryClient = registryClient;
    this.manifestTemplate = manifestTemplate;
    this.imageQualifier = imageQualifier;
    this.imageDigest = imageDigest;
    this.imageId = imageId;
  }

  @Override
  public BuildResult call() throws IOException, RegistryException {
    EventHandlers eventHandlers = buildContext.getEventHandlers();
    try (TimerEventDispatcher ignored = new TimerEventDispatcher(eventHandlers, DESCRIPTION);
        ProgressEventDispatcher ignored2 =
            progressEventDispatcherFactory.create("pushing manifest for " + imageQualifier, 1)) {
      eventHandlers.dispatch(LogEvent.info("Pushing manifest for " + imageQualifier + "..."));

      registryClient.pushManifest(manifestTemplate, imageQualifier);
      return new BuildResult(imageDigest, imageId);
    }
  }
}
