/*
 * Copyright 2018 Google LLC. All rights reserved.
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

package com.google.cloud.tools.jib.builder;

import com.google.cloud.tools.jib.Timer;
import com.google.cloud.tools.jib.blob.BlobDescriptor;
import com.google.cloud.tools.jib.cache.Cache;
import com.google.cloud.tools.jib.cache.CacheDirectoryNotOwnedException;
import com.google.cloud.tools.jib.cache.CacheMetadataCorruptedException;
import com.google.cloud.tools.jib.cache.Caches;
import com.google.cloud.tools.jib.http.Authorization;
import com.google.cloud.tools.jib.image.Image;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

/** All the steps to build an image. */
public class BuildImageSteps implements BuildSteps {

  private static final String DESCRIPTION = "Building and pushing image";
  private static final String STARTUP_MESSAGE_FORMAT = "Containerizing application to %s...";
  // String parameter (target image reference) in cyan.
  private static final String SUCCESS_MESSAGE_FORMAT =
      "Built and pushed image as \u001B[36m%s\u001B[0m";

  private final BuildConfiguration buildConfiguration;
  private final SourceFilesConfiguration sourceFilesConfiguration;
  private final Caches.Initializer cachesInitializer;
  private final String startupMessage;
  private final String successMessage;

  public BuildImageSteps(
      BuildConfiguration buildConfiguration,
      SourceFilesConfiguration sourceFilesConfiguration,
      Caches.Initializer cachesInitializer) {
    this.buildConfiguration = buildConfiguration;
    this.sourceFilesConfiguration = sourceFilesConfiguration;
    this.cachesInitializer = cachesInitializer;
    startupMessage =
        String.format(STARTUP_MESSAGE_FORMAT, buildConfiguration.getTargetImageReference());
    successMessage =
        String.format(SUCCESS_MESSAGE_FORMAT, buildConfiguration.getTargetImageReference());
  }

  @Override
  public BuildConfiguration getBuildConfiguration() {
    return buildConfiguration;
  }

  @Override
  public SourceFilesConfiguration getSourceFilesConfiguration() {
    return sourceFilesConfiguration;
  }

  @Override
  public String getStartupMessage() {
    return startupMessage;
  }

  @Override
  public String getSuccessMessage() {
    return successMessage;
  }

  @Override
  public void run()
      throws InterruptedException, ExecutionException, CacheMetadataCorruptedException, IOException,
          CacheDirectoryNotOwnedException {
    ImmutableList<String> entrypoint =
        EntrypointBuilder.makeEntrypoint(
            sourceFilesConfiguration,
            buildConfiguration.getJvmFlags(),
            buildConfiguration.getMainClass());

    buildConfiguration.getBuildLogger().lifecycle("");

    try (Timer timer = new Timer(buildConfiguration.getBuildLogger(), DESCRIPTION)) {
      try (Timer timer2 = timer.subTimer("Initializing cache")) {
        ListeningExecutorService listeningExecutorService =
            MoreExecutors.listeningDecorator(Executors.newCachedThreadPool());

        try (Caches caches = cachesInitializer.init()) {
          Cache baseLayersCache = caches.getBaseCache();
          Cache applicationLayersCache = caches.getApplicationCache();

          timer2.lap("Setting up credential retrieval");
          RetrieveRegistryCredentialsStep retrieveTargetRegistryCredentialsStep =
              RetrieveRegistryCredentialsStep.forTargetImage(
                  listeningExecutorService, buildConfiguration);
          RetrieveRegistryCredentialsStep retrieveBaseRegistryCredentialsStep =
              RetrieveRegistryCredentialsStep.forBaseImage(
                  listeningExecutorService, buildConfiguration);

          timer2.lap("Setting up image push authentication");
          // Authenticates push.
          AuthenticatePushStep authenticatePushStep =
              new AuthenticatePushStep(
                  listeningExecutorService,
                  buildConfiguration,
                  retrieveTargetRegistryCredentialsStep);

          // TODO: Keep refactoring other steps to implement AsyncStep and remove logic like this.
          ListenableFuture<Authorization> authenticatePushFuture = authenticatePushStep.getFuture();

          timer2.lap("Setting up image pull authentication");
          // Authenticates base image pull.
          AuthenticatePullStep authenticatePullStep =
              new AuthenticatePullStep(
                  listeningExecutorService,
                  buildConfiguration,
                  retrieveBaseRegistryCredentialsStep);

          timer2.lap("Setting up base image pull");
          // Pulls the base image.
          PullBaseImageStep pullBaseImageStep =
              new PullBaseImageStep(
                  listeningExecutorService, buildConfiguration, authenticatePullStep);

          timer2.lap("Setting up base image layer pull");
          // Pulls and caches the base image layers.
          PullAndCacheBaseImageLayersStep pullAndCacheBaseImageLayersStep =
              new PullAndCacheBaseImageLayersStep(
                  listeningExecutorService,
                  buildConfiguration,
                  baseLayersCache,
                  authenticatePullStep,
                  pullBaseImageStep);

          timer2.lap("Setting up base image layer push");
          // Pushes the base image layers.
          PushLayersStep pushBaseImageLayersStep =
              new PushLayersStep(
                  listeningExecutorService,
                  buildConfiguration,
                  authenticatePushStep,
                  pullAndCacheBaseImageLayersStep);

          timer2.lap("Setting up build application layers");
          // Builds the application layers.
          ImmutableList<BuildAndCacheApplicationLayerStep> buildAndCacheApplicationLayerSteps =
              BuildAndCacheApplicationLayerStep.makeList(
                  listeningExecutorService,
                  buildConfiguration,
                  sourceFilesConfiguration,
                  applicationLayersCache);

          timer2.lap("Setting up build container configuration");
          // Builds the image model.
          BuildImageStep buildImageStep =
              new BuildImageStep(
                  listeningExecutorService,
                  buildConfiguration,
                  pullAndCacheBaseImageLayersStep,
                  buildAndCacheApplicationLayerSteps,
                  entrypoint);

          // TODO: Keep refactoring other steps to implement AsyncStep and remove logic like this.
          ListenableFuture<ListenableFuture<Image>> buildImageFutureFuture =
              buildImageStep.getFuture();

          timer2.lap("Setting up container configuration push");
          // Pushes the container configuration.
          ListenableFuture<ListenableFuture<BlobDescriptor>>
              pushContainerConfigurationFutureFuture =
                  Futures.whenAllSucceed(buildImageFutureFuture)
                      .call(
                          new PushContainerConfigurationStep(
                              buildConfiguration,
                              authenticatePushFuture,
                              buildImageFutureFuture,
                              listeningExecutorService),
                          listeningExecutorService);

          timer2.lap("Setting up application layer push");
          // Pushes the application layers.
          PushLayersStep pushApplicationLayersStep =
              new PushLayersStep(
                  listeningExecutorService,
                  buildConfiguration,
                  authenticatePushStep,
                  AsyncSteps.immediate(buildAndCacheApplicationLayerSteps));

          // TODO: Move this somewhere that doesn't clutter this method.
          // Logs a message after pushing all the layers.
          Futures.whenAllSucceed(
                  pushBaseImageLayersStep.getFuture(), pushApplicationLayersStep.getFuture())
              .call(
                  () -> {
                    // Depends on all the layers being pushed.
                    ImmutableList.Builder<ListenableFuture<?>> beforeFinalizingDependenciesBuilder =
                        ImmutableList.builder();
                    for (PushBlobStep pushBaseImageLayerStep :
                        NonBlockingSteps.get(pushBaseImageLayersStep)) {
                      beforeFinalizingDependenciesBuilder.add(pushBaseImageLayerStep.getFuture());
                    }
                    for (PushBlobStep pushApplicationLayerStep :
                        NonBlockingSteps.get(pushApplicationLayersStep)) {
                      beforeFinalizingDependenciesBuilder.add(pushApplicationLayerStep.getFuture());
                    }

                    Futures.whenAllSucceed(beforeFinalizingDependenciesBuilder.build())
                        .call(
                            () -> {
                              // TODO: Have this be more descriptive?
                              buildConfiguration.getBuildLogger().lifecycle("Finalizing...");
                              return null;
                            },
                            listeningExecutorService);

                    return null;
                  },
                  listeningExecutorService);

          timer2.lap("Setting up image manifest push");
          // Pushes the new image manifest.
          PushImageStep pushImageStep =
              new PushImageStep(
                  listeningExecutorService,
                  buildConfiguration,
                  authenticatePushStep,
                  pushBaseImageLayersStep,
                  pushApplicationLayersStep,
                  pushContainerConfigurationFutureFuture,
                  buildImageStep);

          timer2.lap("Running push new image");
          pushImageStep.getFuture().get();
        }
      }
    }

    buildConfiguration.getBuildLogger().lifecycle("");
    buildConfiguration.getBuildLogger().lifecycle("Container entrypoint set to " + entrypoint);
  }
}
