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
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nullable;

/** Pushes the final image. Outputs the pushed image digest. */
class PushImageStep implements Callable<BuildResult> {

  private static final String DESCRIPTION = "Pushing new image";

  private final BuildConfiguration buildConfiguration;
  private final ProgressEventDispatcher.Factory progressEventDispatcherFactory;

  @Nullable private final Authorization pushAuthorization;
  private final BlobDescriptor containerConfigurationDigestAndSize;
  private final Image builtImage;

  private final ListeningExecutorService listeningExecutorService;

  // TODO: remove listeningExecutorService like other siblings
  PushImageStep(
      ListeningExecutorService listeningExecutorService,
      BuildConfiguration buildConfiguration,
      ProgressEventDispatcher.Factory progressEventDispatcherFactory,
      @Nullable Authorization pushAuthorization,
      BlobDescriptor containerConfigurationDigestAndSize,
      Image builtImage) {
    this.listeningExecutorService = listeningExecutorService;
    this.buildConfiguration = buildConfiguration;
    this.progressEventDispatcherFactory = progressEventDispatcherFactory;
    this.pushAuthorization = pushAuthorization;
    this.containerConfigurationDigestAndSize = containerConfigurationDigestAndSize;
    this.builtImage = builtImage;
  }

  @Override
  public BuildResult call() throws IOException, InterruptedException, ExecutionException {
    ImmutableSet<String> targetImageTags = buildConfiguration.getAllTargetImageTags();
    ProgressEventDispatcher progressEventDispatcher =
        progressEventDispatcherFactory.create("pushing image manifest", targetImageTags.size());

    try (TimerEventDispatcher ignored =
        new TimerEventDispatcher(buildConfiguration.getEventHandlers(), DESCRIPTION)) {
      RegistryClient registryClient =
          buildConfiguration
              .newTargetImageRegistryClientFactory()
              .setAuthorization(pushAuthorization)
              .newRegistryClient();

      // Constructs the image.
      ImageToJsonTranslator imageToJsonTranslator = new ImageToJsonTranslator(builtImage);

      // Gets the image manifest to push.
      BuildableManifestTemplate manifestTemplate =
          imageToJsonTranslator.getManifestTemplate(
              buildConfiguration.getTargetFormat(), containerConfigurationDigestAndSize);

      // Pushes to all target image tags.
      List<ListenableFuture<Void>> pushAllTagsFutures = new ArrayList<>();
      for (String tag : targetImageTags) {
        ProgressEventDispatcher.Factory progressEventDispatcherFactory =
            progressEventDispatcher.newChildProducer();
        pushAllTagsFutures.add(
            listeningExecutorService.submit(
                () -> {
                  try (ProgressEventDispatcher ignored2 =
                      progressEventDispatcherFactory.create("tagging with " + tag, 1)) {
                    buildConfiguration
                        .getEventHandlers()
                        .dispatch(LogEvent.info("Tagging with " + tag + "..."));
                    registryClient.pushManifest(manifestTemplate, tag);
                  }
                  return null;
                }));
      }

      DescriptorDigest imageDigest = Digests.computeJsonDigest(manifestTemplate);
      DescriptorDigest imageId = containerConfigurationDigestAndSize.getDigest();
      BuildResult result = new BuildResult(imageDigest, imageId);

      return Futures.whenAllSucceed(pushAllTagsFutures)
          .call(
              () -> {
                progressEventDispatcher.close();
                return result;
              },
              listeningExecutorService)
          .get();
    }
  }
}
