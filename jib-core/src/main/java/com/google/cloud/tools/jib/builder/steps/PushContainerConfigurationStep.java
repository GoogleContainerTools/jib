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

import com.google.cloud.tools.jib.async.AsyncDependencies;
import com.google.cloud.tools.jib.async.AsyncStep;
import com.google.cloud.tools.jib.async.NonBlockingSteps;
import com.google.cloud.tools.jib.blob.BlobDescriptor;
import com.google.cloud.tools.jib.blob.Blobs;
import com.google.cloud.tools.jib.builder.ProgressEventDispatcher;
import com.google.cloud.tools.jib.builder.TimerEventDispatcher;
import com.google.cloud.tools.jib.configuration.BuildConfiguration;
import com.google.cloud.tools.jib.hash.Digests;
import com.google.cloud.tools.jib.image.Image;
import com.google.cloud.tools.jib.image.json.ImageToJsonTranslator;
import com.google.cloud.tools.jib.json.JsonTemplate;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

/** Pushes the container configuration. */
class PushContainerConfigurationStep
    implements AsyncStep<AsyncStep<PushBlobStep>>, Callable<AsyncStep<PushBlobStep>> {

  private static final String DESCRIPTION = "Pushing container configuration";

  private final BuildConfiguration buildConfiguration;
  private final ListeningExecutorService listeningExecutorService;
  private final ProgressEventDispatcher.Factory progressEventDispatcherFactory;

  private final AuthenticatePushStep authenticatePushStep;
  private final BuildImageStep buildImageStep;

  private final ListenableFuture<AsyncStep<PushBlobStep>> listenableFuture;

  PushContainerConfigurationStep(
      ListeningExecutorService listeningExecutorService,
      BuildConfiguration buildConfiguration,
      ProgressEventDispatcher.Factory progressEventDispatcherFactory,
      AuthenticatePushStep authenticatePushStep,
      BuildImageStep buildImageStep) {
    this.listeningExecutorService = listeningExecutorService;
    this.buildConfiguration = buildConfiguration;
    this.progressEventDispatcherFactory = progressEventDispatcherFactory;
    this.authenticatePushStep = authenticatePushStep;
    this.buildImageStep = buildImageStep;

    listenableFuture =
        AsyncDependencies.using(listeningExecutorService)
            .addStep(buildImageStep)
            .whenAllSucceed(this);
  }

  @Override
  public ListenableFuture<AsyncStep<PushBlobStep>> getFuture() {
    return listenableFuture;
  }

  @Override
  public AsyncStep<PushBlobStep> call() throws ExecutionException {
    ListenableFuture<PushBlobStep> pushBlobStepFuture =
        AsyncDependencies.using(listeningExecutorService)
            .addStep(authenticatePushStep)
            .addStep(NonBlockingSteps.get(buildImageStep))
            .whenAllSucceed(this::afterBuildConfigurationFutureFuture);
    return () -> pushBlobStepFuture;
  }

  private PushBlobStep afterBuildConfigurationFutureFuture()
      throws ExecutionException, IOException {
    try (ProgressEventDispatcher progressEventDispatcher =
            progressEventDispatcherFactory.create("pushing container configuration", 1);
        TimerEventDispatcher ignored =
            new TimerEventDispatcher(buildConfiguration.getEventHandlers(), DESCRIPTION)) {
      Image image = NonBlockingSteps.get(NonBlockingSteps.get(buildImageStep));
      JsonTemplate containerConfiguration =
          new ImageToJsonTranslator(image).getContainerConfiguration();
      BlobDescriptor blobDescriptor = Digests.computeDigest(containerConfiguration);

      return new PushBlobStep(
          listeningExecutorService,
          buildConfiguration,
          progressEventDispatcher.newChildProducer(),
          authenticatePushStep,
          blobDescriptor,
          Blobs.from(containerConfiguration));
    }
  }
}
