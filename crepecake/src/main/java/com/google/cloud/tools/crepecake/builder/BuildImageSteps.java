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
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;

/** All the steps to build an image. */
public class BuildImageSteps {

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
    ListeningExecutorService listeningExecutorService =
        MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(10));

    try (Cache cache = Cache.init(cacheDirectory)) {
      // Authenticates base image pull.
      ListenableFuture<Authorization> authenticatePullFuture =
          listeningExecutorService.submit(new AuthenticatePullStep(buildConfiguration));
      // Pulls the base image.
      ListenableFuture<Image> pullBaseImageFuture =
          Futures.whenAllSucceed(authenticatePullFuture)
              .call(
                  new PullBaseImageStep(buildConfiguration, authenticatePullFuture),
                  listeningExecutorService);
      // Pulls and caches the base image layers.
      List<ListenableFuture<CachedLayer>> pullBaseImageLayerFutures =
          new PullAndCacheBaseImageLayersStep(
                  buildConfiguration,
                  cache,
                  listeningExecutorService,
                  authenticatePullFuture,
                  pullBaseImageFuture)
              .call();

      // Authenticates push.
      ListenableFuture<Authorization> authenticatePushFuture =
          listeningExecutorService.submit(new AuthenticatePushStep(buildConfiguration));
      // Pushes the base image layers.
      List<ListenableFuture<Void>> pushBaseImageLayerFutures =
          new PushLayersStep(
                  buildConfiguration,
                  listeningExecutorService,
                  authenticatePushFuture,
                  pullBaseImageLayerFutures)
              .call();

      // Builds the application layers.
      List<ListenableFuture<CachedLayer>> buildAndCacheApplicationLayerFutures =
          new BuildAndCacheApplicationLayersStep(
                  sourceFilesConfiguration, cache, listeningExecutorService)
              .call();

      // Builds and pushes the container configuration.
      List<ListenableFuture<?>> buildAndCacheApplicationLayerFuturesDependencies =
          new ArrayList<>(pullBaseImageLayerFutures);
      buildAndCacheApplicationLayerFuturesDependencies.addAll(buildAndCacheApplicationLayerFutures);
      ListenableFuture<BlobDescriptor> buildAndPushContainerConfigurationFuture =
          Futures.whenAllSucceed(buildAndCacheApplicationLayerFuturesDependencies)
              .call(
                  new BuildAndPushContainerConfigurationStep(
                      buildConfiguration,
                      authenticatePushFuture,
                      pullBaseImageLayerFutures,
                      buildAndCacheApplicationLayerFutures,
                      getEntrypoint()),
                  listeningExecutorService);

      // Pushes the application layers.
      List<ListenableFuture<Void>> pushApplicationLayersFuture =
          new PushLayersStep(
                  buildConfiguration,
                  listeningExecutorService,
                  authenticatePushFuture,
                  buildAndCacheApplicationLayerFutures)
              .call();

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

      pushImageFuture.get();
    }
  }

  private List<String> getEntrypoint() {
    List<String> classPaths = new ArrayList<>();
    classPaths.add(
        sourceFilesConfiguration.getDependenciesExtractionPath().resolve("*").toString());
    classPaths.add(sourceFilesConfiguration.getResourcesExtractionPath().toString());
    classPaths.add(sourceFilesConfiguration.getClassesExtractionPath().toString());

    String classPathsString = String.join(":", classPaths);

    List<String> entrypoint =
        Arrays.asList("java", "-cp", classPathsString, buildConfiguration.getMainClass());
    entrypoint.addAll(buildConfiguration.getJvmFlags());
    return entrypoint;
  }
}
