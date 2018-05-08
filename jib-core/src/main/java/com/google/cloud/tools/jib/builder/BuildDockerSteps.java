/*
 * Copyright 2018 Google LLC. All Rights Reserved.
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
import com.google.cloud.tools.jib.blob.Blob;
import com.google.cloud.tools.jib.cache.Cache;
import com.google.cloud.tools.jib.cache.CacheDirectoryNotOwnedException;
import com.google.cloud.tools.jib.cache.CacheMetadataCorruptedException;
import com.google.cloud.tools.jib.cache.CachedLayer;
import com.google.cloud.tools.jib.cache.Caches;
import com.google.cloud.tools.jib.http.Authorization;
import com.google.cloud.tools.jib.image.Image;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

public class BuildDockerSteps {

  private static final String DESCRIPTION = "Building and pushing image";

  private final BuildConfiguration buildConfiguration;
  private final SourceFilesConfiguration sourceFilesConfiguration;
  private final Caches.Initializer cachesInitializer;

  BuildDockerSteps(
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

  public void run()
      throws InterruptedException, ExecutionException, CacheMetadataCorruptedException, IOException,
          CacheDirectoryNotOwnedException {
    List<String> entrypoint =
        EntrypointBuilder.makeEntrypoint(
            sourceFilesConfiguration,
            buildConfiguration.getJvmFlags(),
            buildConfiguration.getMainClass());

    try (Timer timer = new Timer(buildConfiguration.getBuildLogger(), DESCRIPTION)) {
      try (Timer timer2 = timer.subTimer("Initializing cache")) {
        ListeningExecutorService listeningExecutorService =
            MoreExecutors.listeningDecorator(Executors.newCachedThreadPool());

        try (Caches caches = cachesInitializer.init()) {
          Cache baseLayersCache = caches.getBaseCache();
          Cache applicationLayersCache = caches.getApplicationCache();

          timer2.lap("Setting up credential retrieval");
          ListenableFuture<Authorization> retrieveBaseImageRegistryCredentialsFuture =
              listeningExecutorService.submit(
                  RetrieveRegistryCredentialsStep.forBaseImage(buildConfiguration));

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
          ListenableFuture<List<ListenableFuture<CachedLayer>>> pullBaseImageLayerFuturesFuture =
              Futures.whenAllSucceed(pullBaseImageFuture)
                  .call(
                      new PullAndCacheBaseImageLayersStep(
                          buildConfiguration,
                          baseLayersCache,
                          listeningExecutorService,
                          authenticatePullFuture,
                          pullBaseImageFuture),
                      listeningExecutorService);

          timer2.lap("Setting up build application layers");
          // Builds the application layers.
          List<ListenableFuture<CachedLayer>> buildAndCacheApplicationLayerFutures =
              new BuildAndCacheApplicationLayersStep(
                      buildConfiguration,
                      sourceFilesConfiguration,
                      applicationLayersCache,
                      listeningExecutorService)
                  .call();

          timer2.lap("Setting up build container configuration");
          // Builds the container configuration.
          ListenableFuture<ListenableFuture<Blob>> buildContainerConfigurationFutureFuture =
              Futures.whenAllSucceed(pullBaseImageLayerFuturesFuture)
                  .call(
                      new BuildContainerConfigurationStep(
                          buildConfiguration,
                          listeningExecutorService,
                          pullBaseImageLayerFuturesFuture,
                          buildAndCacheApplicationLayerFutures,
                          entrypoint),
                      listeningExecutorService);

          timer2.lap("Setting up push to docker daemon");
          // Pushes the new image manifest.
          ListenableFuture<Void> pushImageFuture =
              Futures.whenAllSucceed(
                      pullBaseImageLayerFuturesFuture, buildContainerConfigurationFutureFuture)
                  .call(
                      new BuildTarballAndLoadDockerStep(
                          buildConfiguration,
                          listeningExecutorService,
                          pullBaseImageLayerFuturesFuture,
                          buildAndCacheApplicationLayerFutures,
                          buildContainerConfigurationFutureFuture),
                      listeningExecutorService);

          timer2.lap("Running push to docker daemon");
          pushImageFuture.get();
        }
      }
    }

    buildConfiguration.getBuildLogger().lifecycle("");
    buildConfiguration.getBuildLogger().lifecycle("Container entrypoint set to " + entrypoint);
  }
}
