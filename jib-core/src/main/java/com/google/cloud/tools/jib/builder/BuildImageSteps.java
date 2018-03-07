/*
 * Copyright 2018 Google Inc.
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
import com.google.cloud.tools.jib.cache.CacheMetadataCorruptedException;
import com.google.cloud.tools.jib.cache.CachedLayer;
import com.google.cloud.tools.jib.http.Authorization;
import com.google.cloud.tools.jib.image.Image;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

/** All the steps to build an image. */
public class BuildImageSteps {

  private static final String DESCRIPTION = "Building and pushing image";

  private final BuildConfiguration buildConfiguration;
  private final SourceFilesConfiguration sourceFilesConfiguration;
  private final Path cacheDirectory;

  public BuildImageSteps(
      BuildConfiguration buildConfiguration,
      SourceFilesConfiguration sourceFilesConfiguration,
      Path cacheDirectory) {
    this.buildConfiguration = buildConfiguration;
    this.sourceFilesConfiguration = sourceFilesConfiguration;
    this.cacheDirectory = cacheDirectory;
  }

  public BuildConfiguration getBuildConfiguration() {
    return buildConfiguration;
  }

  public void run()
      throws InterruptedException, ExecutionException, CacheMetadataCorruptedException,
          IOException {
    List<String> entrypoint =
        EntrypointBuilder.makeEntrypoint(
            sourceFilesConfiguration,
            buildConfiguration.getJvmFlags(),
            buildConfiguration.getMainClass());

    try (Timer timer = new Timer(buildConfiguration.getBuildLogger(), DESCRIPTION)) {
      try (Timer timer2 = timer.subTimer("Initializing cache")) {
        ListeningExecutorService listeningExecutorService =
            MoreExecutors.listeningDecorator(Executors.newCachedThreadPool());

        try (Cache cache = Cache.init(cacheDirectory)) {
          timer2.lap("Setting up credential retrieval");
          ListenableFuture<Authorization> retrieveTargetRegistryCredentialsFuture =
              listeningExecutorService.submit(
                  new RetrieveRegistryCredentialsStep(
                      buildConfiguration, buildConfiguration.getTargetRegistry()));
          ListenableFuture<Authorization> retrieveBaseImageRegistryCredentialsFuture =
              listeningExecutorService.submit(
                  new RetrieveRegistryCredentialsStep(
                      buildConfiguration, buildConfiguration.getBaseImageRegistry()));

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
          ListenableFuture<List<ListenableFuture<CachedLayer>>> pullBaseImageLayerFuturesFuture =
              Futures.whenAllSucceed(pullBaseImageFuture)
                  .call(
                      new PullAndCacheBaseImageLayersStep(
                          buildConfiguration,
                          cache,
                          listeningExecutorService,
                          authenticatePullFuture,
                          pullBaseImageFuture),
                      listeningExecutorService);

          timer2.lap("Setting up base image layer push");
          // Pushes the base image layers.
          ListenableFuture<List<ListenableFuture<Void>>> pushBaseImageLayerFuturesFuture =
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
          List<ListenableFuture<CachedLayer>> buildAndCacheApplicationLayerFutures =
              new BuildAndCacheApplicationLayersStep(
                      buildConfiguration, sourceFilesConfiguration, cache, listeningExecutorService)
                  .call();

          timer2.lap("Setting up container configuration push");
          // Builds and pushes the container configuration.
          ListenableFuture<ListenableFuture<BlobDescriptor>>
              buildAndPushContainerConfigurationFutureFuture =
                  Futures.whenAllSucceed(pullBaseImageLayerFuturesFuture)
                      .call(
                          new BuildAndPushContainerConfigurationStep(
                              buildConfiguration,
                              listeningExecutorService,
                              authenticatePushFuture,
                              pullBaseImageLayerFuturesFuture,
                              buildAndCacheApplicationLayerFutures,
                              entrypoint),
                          listeningExecutorService);

          timer2.lap("Setting up application layer push");
          // Pushes the application layers.
          List<ListenableFuture<Void>> pushApplicationLayersFuture =
              new PushLayersStep(
                      buildConfiguration,
                      listeningExecutorService,
                      authenticatePushFuture,
                      Futures.immediateFuture(buildAndCacheApplicationLayerFutures))
                  .call();

          timer2.lap("Setting up image manifest push");
          // Pushes the new image manifest.
          ListenableFuture<Void> pushImageFuture =
              Futures.whenAllSucceed(
                      pushBaseImageLayerFuturesFuture,
                      buildAndPushContainerConfigurationFutureFuture)
                  .call(
                      new PushImageStep(
                          buildConfiguration,
                          listeningExecutorService,
                          authenticatePushFuture,
                          pullBaseImageLayerFuturesFuture,
                          buildAndCacheApplicationLayerFutures,
                          pushBaseImageLayerFuturesFuture,
                          pushApplicationLayersFuture,
                          buildAndPushContainerConfigurationFutureFuture),
                      listeningExecutorService);

          timer2.lap("Running push new image");
          pushImageFuture.get();
        }
      }
    }

    buildConfiguration.getBuildLogger().info("");
    buildConfiguration.getBuildLogger().info("Container entrypoint set to " + entrypoint);
  }
}
