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

import com.google.cloud.tools.crepecake.cache.Cache;
import com.google.cloud.tools.crepecake.cache.CachedLayer;
import com.google.cloud.tools.crepecake.http.Authorization;
import com.google.cloud.tools.crepecake.image.Image;
import com.google.cloud.tools.crepecake.image.ImageLayers;
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
          new PushBaseImageLayersStep(
                  buildConfiguration,
                  listeningExecutorService,
                  authenticatePushFuture,
                  pullBaseImageLayerFutures)
              .call();

      // Builds the application layers.
      ListenableFuture<ImageLayers<CachedLayer>> buildAndCacheApplicationLayersFuture =
          listeningExecutorService.submit(
              new BuildAndCacheApplicationLayersStep(sourceFilesConfiguration, cache));
      // Pushes the application layers.
      ListenableFuture<Void> pushApplicationLayersFuture =
          Futures.whenAllSucceed(buildAndCacheApplicationLayersFuture)
              .call(
                  () ->
                      new PushApplicationLayersStep(
                              listeningExecutorService,
                              buildConfiguration,
                              authenticatePushFuture.get(),
                              buildAndCacheApplicationLayersFuture.get())
                          .call(),
                  listeningExecutorService);

      // Pushes the new image manifest.
      List<ListenableFuture<?>> pushImageFutureDependencies =
          new ArrayList<>(pushBaseImageLayerFutures);
      pushImageFutureDependencies.add(pushApplicationLayersFuture);
      ListenableFuture<Void> pushImageFuture =
          Futures.whenAllSucceed(pushImageFutureDependencies)
              .call(
                  () -> {
                    Image image = new Image();
                    for (ListenableFuture<CachedLayer> pullBaseImageLayerFuture :
                        pullBaseImageLayerFutures) {
                      image.addLayer(pullBaseImageLayerFuture.get());
                    }
                    image
                        .addLayers(buildAndCacheApplicationLayersFuture.get())
                        .setEntrypoint(getEntrypoint());

                    return new PushImageStep(
                            buildConfiguration, authenticatePushFuture.get(), image)
                        .call();
                  },
                  listeningExecutorService);

      pushImageFuture.get();
    }
  }

  //  public void run()
  //      throws CacheMetadataCorruptedException, IOException, RegistryAuthenticationFailedException,
  //          RegistryException, DuplicateLayerException, LayerCountMismatchException,
  //          LayerPropertyNotFoundException, NonexistentServerUrlDockerCredentialHelperException,
  //          NonexistentDockerCredentialHelperException, ExecutionException, InterruptedException {
  //    try (Timer t = Timer.push("BuildImageSteps")) {
  //
  //      try (Cache cache = Cache.init(cacheDirectory)) {
  //        try (Timer t2 = Timer.push("AuthenticatePullStep")) {
  //          // Authenticates base image pull.
  //          AuthenticatePullStep authenticatePullStep = new AuthenticatePullStep(buildConfiguration);
  //          Authorization pullAuthorization = authenticatePullStep.call();
  //
  //          Timer.time("PullBaseImageStep");
  //          // Pulls the base image.
  //          PullBaseImageStep pullBaseImageStep =
  //              new PullBaseImageStep(buildConfiguration, pullAuthorization);
  //          Image baseImage = pullBaseImageStep.call();
  //
  //          Timer.time("PullAndCacheBaseImageLayersStep");
  //          // Pulls and caches the base image layers.
  //          PullAndCacheBaseImageLayersStep pullAndCacheBaseImageLayersStep =
  //              new PullAndCacheBaseImageLayersStep(
  //                  buildConfiguration, cache, pullAuthorization, baseImage);
  //          ImageLayers<CachedLayer> baseImageLayers = pullAndCacheBaseImageLayersStep.call();
  //
  //          Timer.time("AuthenticatePushStep");
  //          // Authenticates push.
  //          AuthenticatePushStep authenticatePushStep = new AuthenticatePushStep(buildConfiguration);
  //          Authorization pushAuthorization = authenticatePushStep.call();
  //
  //          Timer.time("PushBaseImageLayersStep");
  //          // Pushes the base image layers.
  //          PushBaseImageLayersStep pushBaseImageLayersStep =
  //              new PushBaseImageLayersStep(buildConfiguration, pushAuthorization, baseImageLayers);
  //          pushBaseImageLayersStep.call();
  //
  //          Timer.time("BuildAndCacheApplicationLayersStep");
  //          BuildAndCacheApplicationLayersStep buildAndCacheApplicationLayersStep =
  //              new BuildAndCacheApplicationLayersStep(sourceFilesConfiguration, cache);
  //          ImageLayers<CachedLayer> applicationLayers = buildAndCacheApplicationLayersStep.call();
  //
  //          Timer.time("PushApplicationLayerStep");
  //          // Pushes the application layers.
  //          PushApplicationLayersStep pushApplicationLayersStep =
  //              new PushApplicationLayersStep(
  //                  null, buildConfiguration, pushAuthorization, applicationLayers);
  //          pushApplicationLayersStep.call();
  //
  //          Timer.time("PushImageStep");
  //          // Pushes the new image manifest.
  //          Image image =
  //              new Image()
  //                  .addLayers(baseImageLayers)
  //                  .addLayers(applicationLayers)
  //                  .setEntrypoint(getEntrypoint());
  //          PushImageStep pushImageStep =
  //              new PushImageStep(buildConfiguration, pushAuthorization, image);
  //          pushImageStep.call();
  //
  //          System.out.println(getEntrypoint());
  //        }
  //      }
  //    } finally {
  //      Timer.print();
  //    }
  //  }

  private List<String> getEntrypoint() {
    List<String> classPaths = new ArrayList<>();
    classPaths.add(
        sourceFilesConfiguration.getDependenciesExtractionPath().resolve("*").toString());
    classPaths.add(sourceFilesConfiguration.getResourcesExtractionPath().toString());
    classPaths.add(sourceFilesConfiguration.getClassesExtractionPath().toString());

    String entrypoint = String.join(":", classPaths);

    return Arrays.asList("java", "-cp", entrypoint, buildConfiguration.getMainClass());
  }
}
