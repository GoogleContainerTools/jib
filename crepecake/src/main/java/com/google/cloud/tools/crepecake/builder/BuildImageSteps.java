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

package com.google.cloud.tools.crepecake.builder;

import com.google.cloud.tools.crepecake.Timer;
import com.google.cloud.tools.crepecake.blob.BlobDescriptor;
import com.google.cloud.tools.crepecake.cache.Cache;
import com.google.cloud.tools.crepecake.cache.CachedLayer;
import com.google.cloud.tools.crepecake.http.Authorization;
import com.google.cloud.tools.crepecake.image.Image;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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

  public void runAsync() throws Exception {
    try (Timer timer = new Timer(buildConfiguration.getBuildLogger(), DESCRIPTION)) {
      try (Timer timer2 = timer.subTimer("Initializing cache")) {
        ListeningExecutorService listeningExecutorService =
            MoreExecutors.listeningDecorator(Executors.newCachedThreadPool());

        try (Cache cache = Cache.init(cacheDirectory)) {
          timer2.lap("Setting up image pull authentication");
          // Authenticates base image pull.
          ListenableFuture<Authorization> authenticatePullFuture =
              listeningExecutorService.submit(new AuthenticatePullStep(buildConfiguration));
          timer2.lap("Setting up base image pull");
          // Pulls the base image.
          ListenableFuture<Image> pullBaseImageFuture =
              Futures.whenAllSucceed(authenticatePullFuture)
                  .call(
                      new PullBaseImageStep(buildConfiguration, authenticatePullFuture),
                      listeningExecutorService);
          timer2.lap("Setting up base image layer pull");
          // Pulls and caches the base image layers.
          List<ListenableFuture<CachedLayer>> pullBaseImageLayerFutures =
              new PullAndCacheBaseImageLayersStep(
                      buildConfiguration,
                      cache,
                      listeningExecutorService,
                      authenticatePullFuture,
                      pullBaseImageFuture)
                  .call();

          timer2.lap("Setting up image push authentication");
          // Authenticates push.
          ListenableFuture<Authorization> authenticatePushFuture =
              listeningExecutorService.submit(new AuthenticatePushStep(buildConfiguration));
          timer2.lap("Setting up base image layer push");
          // Pushes the base image layers.
          List<ListenableFuture<Void>> pushBaseImageLayerFutures =
              new PushLayersStep(
                      buildConfiguration,
                      listeningExecutorService,
                      authenticatePushFuture,
                      pullBaseImageLayerFutures)
                  .call();

          timer2.lap("Setting up build application layers");
          // Builds the application layers.
          List<ListenableFuture<CachedLayer>> buildAndCacheApplicationLayerFutures =
              new BuildAndCacheApplicationLayersStep(
                      buildConfiguration, sourceFilesConfiguration, cache, listeningExecutorService)
                  .call();

          timer2.lap("Setting up container configuration push");
          // Builds and pushes the container configuration.
          List<ListenableFuture<?>> buildAndPushContainerConfigurationFutureDependencies =
              new ArrayList<>(pullBaseImageLayerFutures);
          buildAndPushContainerConfigurationFutureDependencies.addAll(
              buildAndCacheApplicationLayerFutures);
          buildAndPushContainerConfigurationFutureDependencies.add(authenticatePushFuture);
          ListenableFuture<BlobDescriptor> buildAndPushContainerConfigurationFuture =
              Futures.whenAllSucceed(buildAndPushContainerConfigurationFutureDependencies)
                  .call(
                      new BuildAndPushContainerConfigurationStep(
                          buildConfiguration,
                          authenticatePushFuture,
                          pullBaseImageLayerFutures,
                          buildAndCacheApplicationLayerFutures,
                          getEntrypoint()),
                      listeningExecutorService);

          timer2.lap("Setting up application layer push");
          // Pushes the application layers.
          List<ListenableFuture<Void>> pushApplicationLayersFuture =
              new PushLayersStep(
                      buildConfiguration,
                      listeningExecutorService,
                      authenticatePushFuture,
                      buildAndCacheApplicationLayerFutures)
                  .call();

          timer2.lap("Setting up image manifest push");
          // Pushes the new image manifest.
          List<ListenableFuture<?>> pushImageFutureDependencies =
              new ArrayList<>(pushBaseImageLayerFutures);
          pushImageFutureDependencies.addAll(pushApplicationLayersFuture);
          ListenableFuture<Void> pushImageFuture =
              Futures.whenAllSucceed(pushImageFutureDependencies)
                  .call(
                      new PushImageStep(
                          buildConfiguration,
                          authenticatePushFuture,
                          pullBaseImageLayerFutures,
                          buildAndCacheApplicationLayerFutures,
                          buildAndPushContainerConfigurationFuture),
                      listeningExecutorService);

          timer2.lap("Running push new image");
          pushImageFuture.get();
        }
      }
    }

    buildConfiguration.getBuildLogger().info("Container entrypoint set to " + getEntrypoint());
  }

  private List<String> getEntrypoint() {
    List<String> classPaths = new ArrayList<>();
    classPaths.add(sourceFilesConfiguration.getDependenciesPathOnImage().resolve("*").toString());
    classPaths.add(sourceFilesConfiguration.getResourcesPathOnImage().toString());
    classPaths.add(sourceFilesConfiguration.getClassesPathOnImage().toString());

    String classPathsString = String.join(":", classPaths);

    List<String> entrypoint = new ArrayList<>(4 + buildConfiguration.getJvmFlags().size());
    entrypoint.add("java");
    entrypoint.addAll(buildConfiguration.getJvmFlags());
    entrypoint.add("-cp");
    entrypoint.add(classPathsString);
    entrypoint.add(buildConfiguration.getMainClass());
    return entrypoint;
  }
}
