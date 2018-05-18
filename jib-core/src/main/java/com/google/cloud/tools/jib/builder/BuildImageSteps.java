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
import com.google.cloud.tools.jib.cache.CachedLayer;
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
public class BuildImageSteps {

  private static final String DESCRIPTION = "Building and pushing image";

  private final BuildConfiguration buildConfiguration;
  private final SourceFilesConfiguration sourceFilesConfiguration;
  private final Caches.Initializer cachesInitializer;

  public BuildImageSteps(
      BuildConfiguration buildConfiguration,
      SourceFilesConfiguration sourceFilesConfiguration,
      Caches.Initializer cachesInitializer) {
    this.buildConfiguration = buildConfiguration;
    this.sourceFilesConfiguration = sourceFilesConfiguration;
    this.cachesInitializer = cachesInitializer;
  }

  public BuildConfiguration getBuildConfiguration() {
    return buildConfiguration;
  }

  public SourceFilesConfiguration getSourceFilesConfiguration() {
    return sourceFilesConfiguration;
  }

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
          ListenableFuture<Authorization> retrieveTargetRegistryCredentialsFuture =
              listeningExecutorService.submit(
                  RetrieveRegistryCredentialsStep.forTargetImage(buildConfiguration));
          ListenableFuture<Authorization> retrieveBaseImageRegistryCredentialsFuture =
              listeningExecutorService.submit(
                  RetrieveRegistryCredentialsStep.forBaseImage(buildConfiguration));

          timer2.lap("Setting up image push authentication");
          // Authenticates push.
          ListenableFuture<Authorization> authenticatePushFuture =
              Futures.whenAllSucceed(retrieveTargetRegistryCredentialsFuture)
                  .call(
                      new AuthenticatePushStep(
                          buildConfiguration, retrieveTargetRegistryCredentialsFuture),
                      listeningExecutorService);

          timer2.lap("Setting up image pull authentication");
          // Authenticates base image pull.
          ListenableFuture<Authorization> authenticatePullFuture =
              Futures.whenAllSucceed(retrieveBaseImageRegistryCredentialsFuture)
                  .call(
                      new AuthenticatePullStep(
                          buildConfiguration, retrieveBaseImageRegistryCredentialsFuture),
                      listeningExecutorService);

          timer2.lap("Setting up base image pull");
          // Pulls the base image.
          ListenableFuture<Image> pullBaseImageFuture =
              Futures.whenAllSucceed(authenticatePullFuture)
                  .call(
                      new PullBaseImageStep(buildConfiguration, authenticatePullFuture),
                      listeningExecutorService);
          timer2.lap("Setting up base image layer pull");
          // Pulls and caches the base image layers.
          ListenableFuture<ImmutableList<ListenableFuture<CachedLayer>>>
              pullBaseImageLayerFuturesFuture =
                  Futures.whenAllSucceed(pullBaseImageFuture)
                      .call(
                          new PullAndCacheBaseImageLayersStep(
                              buildConfiguration,
                              baseLayersCache,
                              listeningExecutorService,
                              authenticatePullFuture,
                              pullBaseImageFuture),
                          listeningExecutorService);

          timer2.lap("Setting up base image layer push");
          // Pushes the base image layers.
          ListenableFuture<ImmutableList<ListenableFuture<Void>>> pushBaseImageLayerFuturesFuture =
              Futures.whenAllSucceed(pullBaseImageLayerFuturesFuture)
                  .call(
                      new PushLayersStep(
                          buildConfiguration,
                          listeningExecutorService,
                          authenticatePushFuture,
                          pullBaseImageLayerFuturesFuture),
                      listeningExecutorService);

          timer2.lap("Setting up build application layers");
          // Builds the application layers.
          ImmutableList<ListenableFuture<CachedLayer>> buildAndCacheApplicationLayerFutures =
              new BuildAndCacheApplicationLayersStep(
                      buildConfiguration,
                      sourceFilesConfiguration,
                      applicationLayersCache,
                      listeningExecutorService)
                  .call();

          timer2.lap("Setting up build container configuration");
          // Builds the container configuration.
          ListenableFuture<ListenableFuture<Image>> buildImageFutureFuture =
              Futures.whenAllSucceed(pullBaseImageLayerFuturesFuture)
                  .call(
                      new BuildImageStep(
                          buildConfiguration,
                          listeningExecutorService,
                          pullBaseImageLayerFuturesFuture,
                          buildAndCacheApplicationLayerFutures,
                          entrypoint),
                      listeningExecutorService);

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
          ImmutableList<ListenableFuture<Void>> pushApplicationLayersFutures =
              new PushLayersStep(
                      buildConfiguration,
                      listeningExecutorService,
                      authenticatePushFuture,
                      Futures.immediateFuture(buildAndCacheApplicationLayerFutures))
                  .call();

          // TODO: Move this somewhere that doesn't clutter this method.
          // Logs a message after pushing all the layers.
          Futures.whenAllSucceed(pushBaseImageLayerFuturesFuture)
              .call(
                  () -> {
                    // Depends on all the layers being pushed.
                    ImmutableList.Builder<ListenableFuture<?>> beforeFinalizingDependenciesBuilder =
                        ImmutableList.builder();
                    beforeFinalizingDependenciesBuilder.addAll(
                        NonBlockingFutures.get(pushBaseImageLayerFuturesFuture));
                    beforeFinalizingDependenciesBuilder.addAll(pushApplicationLayersFutures);

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
          ListenableFuture<Void> pushImageFuture =
              Futures.whenAllSucceed(
                      pushBaseImageLayerFuturesFuture, pushContainerConfigurationFutureFuture)
                  .call(
                      new PushImageStep(
                          buildConfiguration,
                          listeningExecutorService,
                          authenticatePushFuture,
                          pushBaseImageLayerFuturesFuture,
                          pushApplicationLayersFutures,
                          pushContainerConfigurationFutureFuture,
                          buildImageFutureFuture),
                      listeningExecutorService);

          timer2.lap("Running push new image");
          pushImageFuture.get();
        }
      }
    }

    buildConfiguration.getBuildLogger().lifecycle("");
    buildConfiguration.getBuildLogger().lifecycle("Container entrypoint set to " + entrypoint);
  }
}
