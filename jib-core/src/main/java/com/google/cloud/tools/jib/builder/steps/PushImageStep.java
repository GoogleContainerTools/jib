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
import com.google.cloud.tools.jib.async.AsyncDependencies;
import com.google.cloud.tools.jib.async.AsyncStep;
import com.google.cloud.tools.jib.async.NonBlockingSteps;
import com.google.cloud.tools.jib.blob.BlobDescriptor;
import com.google.cloud.tools.jib.builder.ProgressEventDispatcher;
import com.google.cloud.tools.jib.builder.TimerEventDispatcher;
import com.google.cloud.tools.jib.configuration.BuildConfiguration;
import com.google.cloud.tools.jib.hash.Digests;
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

/** Pushes the final image. Outputs the pushed image digest. */
class PushImageStep implements AsyncStep<BuildResult>, Callable<BuildResult> {

  private static final String DESCRIPTION = "Pushing new image";

  private final BuildConfiguration buildConfiguration;
  private final ListeningExecutorService listeningExecutorService;
  private final ProgressEventDispatcher.Factory progressEventDispatcherFactory;

  private final AuthenticatePushStep authenticatePushStep;

  private final PushLayersStep pushBaseImageLayersStep;
  private final PushLayersStep pushApplicationLayersStep;
  private final PushContainerConfigurationStep pushContainerConfigurationStep;
  private final BuildImageStep buildImageStep;

  private final ListenableFuture<BuildResult> listenableFuture;

  PushImageStep(
      ListeningExecutorService listeningExecutorService,
      BuildConfiguration buildConfiguration,
      ProgressEventDispatcher.Factory progressEventDispatcherFactory,
      AuthenticatePushStep authenticatePushStep,
      PushLayersStep pushBaseImageLayersStep,
      PushLayersStep pushApplicationLayersStep,
      PushContainerConfigurationStep pushContainerConfigurationStep,
      BuildImageStep buildImageStep) {
    this.listeningExecutorService = listeningExecutorService;
    this.buildConfiguration = buildConfiguration;
    this.progressEventDispatcherFactory = progressEventDispatcherFactory;
    this.authenticatePushStep = authenticatePushStep;
    this.pushBaseImageLayersStep = pushBaseImageLayersStep;
    this.pushApplicationLayersStep = pushApplicationLayersStep;
    this.pushContainerConfigurationStep = pushContainerConfigurationStep;
    this.buildImageStep = buildImageStep;

    listenableFuture =
        AsyncDependencies.using(listeningExecutorService)
            .addStep(pushBaseImageLayersStep)
            .addStep(pushApplicationLayersStep)
            .addStep(pushContainerConfigurationStep)
            .whenAllSucceed(this);
  }

  @Override
  public ListenableFuture<BuildResult> getFuture() {
    return listenableFuture;
  }

  @Override
  public BuildResult call() throws ExecutionException, InterruptedException {
    return AsyncDependencies.using(listeningExecutorService)
        .addStep(authenticatePushStep)
        .addSteps(NonBlockingSteps.get(pushBaseImageLayersStep))
        .addSteps(NonBlockingSteps.get(pushApplicationLayersStep))
        .addStep(NonBlockingSteps.get(pushContainerConfigurationStep))
        .addStep(NonBlockingSteps.get(buildImageStep))
        .whenAllSucceed(this::afterPushSteps)
        .get();
  }

  private BuildResult afterPushSteps() throws ExecutionException, InterruptedException {
    AsyncDependencies dependencies = AsyncDependencies.using(listeningExecutorService);
    for (AsyncStep<PushBlobStep> pushBaseImageLayerStep :
        NonBlockingSteps.get(pushBaseImageLayersStep)) {
      dependencies.addStep(NonBlockingSteps.get(pushBaseImageLayerStep));
    }
    for (AsyncStep<PushBlobStep> pushApplicationLayerStep :
        NonBlockingSteps.get(pushApplicationLayersStep)) {
      dependencies.addStep(NonBlockingSteps.get(pushApplicationLayerStep));
    }
    return dependencies
        .addStep(NonBlockingSteps.get(NonBlockingSteps.get(pushContainerConfigurationStep)))
        .whenAllSucceed(this::afterAllPushed)
        .get();
  }

  private BuildResult afterAllPushed()
      throws ExecutionException, IOException, InterruptedException {
    ImmutableSet<String> targetImageTags = buildConfiguration.getAllTargetImageTags();
    ProgressEventDispatcher progressEventDispatcher =
        progressEventDispatcherFactory.create("pushing image manifest", targetImageTags.size());

    try (TimerEventDispatcher ignored =
        new TimerEventDispatcher(buildConfiguration.getEventHandlers(), DESCRIPTION)) {
      RegistryClient registryClient =
          buildConfiguration
              .newTargetImageRegistryClientFactory()
              .setAuthorization(NonBlockingSteps.get(authenticatePushStep))
              .newRegistryClient();

      // Constructs the image.
      ImageToJsonTranslator imageToJsonTranslator =
          new ImageToJsonTranslator(NonBlockingSteps.get(NonBlockingSteps.get(buildImageStep)));

      // Gets the image manifest to push.
      BlobDescriptor containerConfigurationBlobDescriptor =
          NonBlockingSteps.get(
              NonBlockingSteps.get(NonBlockingSteps.get(pushContainerConfigurationStep)));
      BuildableManifestTemplate manifestTemplate =
          imageToJsonTranslator.getManifestTemplate(
              buildConfiguration.getTargetFormat(), containerConfigurationBlobDescriptor);

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
      DescriptorDigest imageId = containerConfigurationBlobDescriptor.getDigest();
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
