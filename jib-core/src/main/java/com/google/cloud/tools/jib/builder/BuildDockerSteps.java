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
import com.google.cloud.tools.jib.cache.Cache;
import com.google.cloud.tools.jib.cache.CacheDirectoryNotOwnedException;
import com.google.cloud.tools.jib.cache.CacheMetadataCorruptedException;
import com.google.cloud.tools.jib.cache.Caches;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

/**
 * All the steps to build to Docker daemon.
 *
 * <p>TODO: Refactor for less duplicate code w/ BuildImageSteps
 */
public class BuildDockerSteps implements BuildSteps {

  private static final String DESCRIPTION = "Building and pushing image";
  private static final String STARTUP_MESSAGE_FORMAT = "Building to Docker daemon as %s...";
  // String parameter (target image reference) in cyan.
  private static final String SUCCESS_MESSAGE_FORMAT =
      "Built image to Docker daemon as \u001B[36m%s\u001B[0m";

  private final BuildConfiguration buildConfiguration;
  private final SourceFilesConfiguration sourceFilesConfiguration;
  private final Caches.Initializer cachesInitializer;
  private final String startupMessage;
  private final String successMessage;

  public BuildDockerSteps(
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
          RetrieveRegistryCredentialsStep retrieveBaseRegistryCredentialsStep =
              RetrieveRegistryCredentialsStep.forBaseImage(
                  listeningExecutorService, buildConfiguration);

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

          timer2.lap("Setting up build application layers");
          // Builds the application layers.
          ImmutableList<BuildAndCacheApplicationLayerStep> buildAndCacheApplicationLayerSteps =
              BuildAndCacheApplicationLayerStep.makeList(
                  listeningExecutorService,
                  buildConfiguration,
                  sourceFilesConfiguration,
                  applicationLayersCache);

          timer2.lap("Setting up build container configuration");
          // Builds the container configuration.
          BuildImageStep buildImageStep =
              new BuildImageStep(
                  listeningExecutorService,
                  buildConfiguration,
                  pullAndCacheBaseImageLayersStep,
                  buildAndCacheApplicationLayerSteps,
                  entrypoint);

          // TODO: Move this somewhere that doesn't clutter this method. Consolidate with
          // BuildImageSteps.
          // Logs a message after pushing all the layers.
          Futures.whenAllSucceed(pullAndCacheBaseImageLayersStep.getFuture())
              .call(
                  () -> {
                    // Depends on all the layers being pushed.
                    ImmutableList.Builder<ListenableFuture<?>> beforeFinalizingDependenciesBuilder =
                        ImmutableList.builder();
                    for (PullAndCacheBaseImageLayerStep pushBaseImageLayerStep :
                        NonBlockingSteps.get(pullAndCacheBaseImageLayersStep)) {
                      beforeFinalizingDependenciesBuilder.add(pushBaseImageLayerStep.getFuture());
                    }
                    for (BuildAndCacheApplicationLayerStep buildAndCacheApplicationLayerStep :
                        buildAndCacheApplicationLayerSteps) {
                      beforeFinalizingDependenciesBuilder.add(
                          buildAndCacheApplicationLayerStep.getFuture());
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

          timer2.lap("Setting up build to docker daemon");
          // Builds the image tarball and loads into the Docker daemon.
          BuildTarballAndLoadDockerStep buildTarballAndLoadDockerStep =
              new BuildTarballAndLoadDockerStep(
                  listeningExecutorService,
                  buildConfiguration,
                  pullAndCacheBaseImageLayersStep,
                  buildAndCacheApplicationLayerSteps,
                  buildImageStep);

          timer2.lap("Running build to docker daemon");
          buildTarballAndLoadDockerStep.getFuture().get();
        }
      }
    }

    buildConfiguration.getBuildLogger().lifecycle("");
    buildConfiguration.getBuildLogger().lifecycle("Container entrypoint set to " + entrypoint);
  }
}
