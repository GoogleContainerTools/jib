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

import com.google.cloud.tools.jib.Timer;
import com.google.cloud.tools.jib.async.AsyncStep;
import com.google.cloud.tools.jib.async.NonBlockingSteps;
import com.google.cloud.tools.jib.configuration.BuildConfiguration;
import com.google.cloud.tools.jib.image.json.BuildableManifestTemplate;
import com.google.cloud.tools.jib.image.json.ImageToJsonTranslator;
import com.google.cloud.tools.jib.registry.RegistryClient;
import com.google.cloud.tools.jib.registry.RegistryException;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

/** Pushes the final image. */
class PushImageStep implements AsyncStep<Void>, Callable<Void> {

  private static final String DESCRIPTION = "Pushing new image";

  private final BuildConfiguration buildConfiguration;
  private final AuthenticatePushStep authenticatePushStep;

  private final PushLayersStep pushBaseImageLayersStep;
  private final PushLayersStep pushApplicationLayersStep;
  private final PushContainerConfigurationStep pushContainerConfigurationStep;
  private final BuildImageStep buildImageStep;

  private final ListeningExecutorService listeningExecutorService;
  private final ListenableFuture<Void> listenableFuture;

  PushImageStep(
      ListeningExecutorService listeningExecutorService,
      BuildConfiguration buildConfiguration,
      AuthenticatePushStep authenticatePushStep,
      PushLayersStep pushBaseImageLayersStep,
      PushLayersStep pushApplicationLayersStep,
      PushContainerConfigurationStep pushContainerConfigurationStep,
      BuildImageStep buildImageStep) {
    this.listeningExecutorService = listeningExecutorService;
    this.buildConfiguration = buildConfiguration;
    this.authenticatePushStep = authenticatePushStep;

    this.pushBaseImageLayersStep = pushBaseImageLayersStep;
    this.pushApplicationLayersStep = pushApplicationLayersStep;
    this.pushContainerConfigurationStep = pushContainerConfigurationStep;
    this.buildImageStep = buildImageStep;

    listenableFuture =
        Futures.whenAllSucceed(
                pushBaseImageLayersStep.getFuture(),
                pushApplicationLayersStep.getFuture(),
                pushContainerConfigurationStep.getFuture())
            .call(this, listeningExecutorService);
  }

  @Override
  public ListenableFuture<Void> getFuture() {
    return listenableFuture;
  }

  @Override
  public Void call() throws ExecutionException, InterruptedException {
    ImmutableList.Builder<ListenableFuture<?>> dependenciesBuilder = ImmutableList.builder();
    dependenciesBuilder.add(authenticatePushStep.getFuture());
    for (AsyncStep<PushBlobStep> pushBlobStepStep : NonBlockingSteps.get(pushBaseImageLayersStep)) {
      dependenciesBuilder.add(pushBlobStepStep.getFuture());
    }
    for (AsyncStep<PushBlobStep> pushBlobStepStep :
        NonBlockingSteps.get(pushApplicationLayersStep)) {
      dependenciesBuilder.add(pushBlobStepStep.getFuture());
    }
    dependenciesBuilder.add(NonBlockingSteps.get(pushContainerConfigurationStep).getFuture());
    dependenciesBuilder.add(NonBlockingSteps.get(buildImageStep).getFuture());
    return Futures.whenAllSucceed(dependenciesBuilder.build())
        .call(this::afterPushSteps, listeningExecutorService)
        .get()
        .get();
  }

  private ListenableFuture<Void> afterPushSteps() throws ExecutionException {
    List<ListenableFuture<?>> dependencies = new ArrayList<>();
    for (AsyncStep<PushBlobStep> pushBlobStepStep : NonBlockingSteps.get(pushBaseImageLayersStep)) {
      dependencies.add(NonBlockingSteps.get(pushBlobStepStep).getFuture());
    }
    for (AsyncStep<PushBlobStep> pushBlobStepStep :
        NonBlockingSteps.get(pushApplicationLayersStep)) {
      dependencies.add(NonBlockingSteps.get(pushBlobStepStep).getFuture());
    }
    dependencies.add(
        NonBlockingSteps.get(NonBlockingSteps.get(pushContainerConfigurationStep)).getFuture());
    return Futures.whenAllSucceed(dependencies)
        .call(this::afterAllPushed, listeningExecutorService);
  }

  private Void afterAllPushed() throws IOException, RegistryException, ExecutionException {
    try (Timer ignored = new Timer(buildConfiguration.getBuildLogger(), DESCRIPTION)) {
      RegistryClient registryClient =
          buildConfiguration
              .newTargetImageRegistryClientFactory()
              .setAuthorization(NonBlockingSteps.get(authenticatePushStep))
              .newRegistryClient();

      // Constructs the image.
      ImageToJsonTranslator imageToJsonTranslator =
          new ImageToJsonTranslator(NonBlockingSteps.get(NonBlockingSteps.get(buildImageStep)));

      // Gets the image manifest to push.
      BuildableManifestTemplate manifestTemplate =
          imageToJsonTranslator.getManifestTemplate(
              buildConfiguration.getTargetFormat(),
              NonBlockingSteps.get(
                  NonBlockingSteps.get(NonBlockingSteps.get(pushContainerConfigurationStep))));

      // Pushes to all target image tags.
      // TODO: Parallelize.
      for (String tag : buildConfiguration.getAllTargetImageTags()) {
        registryClient.pushManifest(manifestTemplate, tag);
      }
    }

    return null;
  }
}
