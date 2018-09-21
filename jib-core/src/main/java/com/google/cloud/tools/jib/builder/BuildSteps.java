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

package com.google.cloud.tools.jib.builder;

import com.google.cloud.tools.jib.Timer;
import com.google.cloud.tools.jib.builder.steps.StepsRunner;
import com.google.cloud.tools.jib.cache.Cache;
import com.google.cloud.tools.jib.cache.CacheDirectoryCreationException;
import com.google.cloud.tools.jib.cache.CacheDirectoryNotOwnedException;
import com.google.cloud.tools.jib.cache.CacheMetadataCorruptedException;
import com.google.cloud.tools.jib.cache.Caches;
import com.google.cloud.tools.jib.configuration.BuildConfiguration;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;

/** Steps for building an image. */
public class BuildSteps {

  private static final String DESCRIPTION_FOR_DOCKER_REGISTRY = "Building and pushing image";
  private static final String DESCRIPTION_FOR_DOCKER_DAEMON = "Building image to Docker daemon";
  private static final String DESCRIPTION_FOR_TARBALL = "Building image tarball";

  /** Accepts {@link StepsRunner} by running the appropriate steps. */
  @FunctionalInterface
  private interface StepsRunnerConsumer {

    void accept(StepsRunner stepsRunner) throws ExecutionException, InterruptedException;
  }

  /**
   * All the steps to build an image to a Docker registry.
   *
   * @param buildConfiguration the configuration parameters for the build
   * @param cachesInitializer the {@link Caches.Initializer} used to setup the cache
   * @return a new {@link BuildSteps} for building to a registry
   */
  public static BuildSteps forBuildToDockerRegistry(
      BuildConfiguration buildConfiguration, Caches.Initializer cachesInitializer) {
    return new BuildSteps(
        DESCRIPTION_FOR_DOCKER_REGISTRY,
        buildConfiguration,
        cachesInitializer,
        stepsRunner ->
            stepsRunner
                .runRetrieveTargetRegistryCredentialsStep()
                .runAuthenticatePushStep()
                .runPullBaseImageStep()
                .runPullAndCacheBaseImageLayersStep()
                .runPushBaseImageLayersStep()
                .runBuildAndCacheApplicationLayerSteps()
                .runBuildImageStep()
                .runPushContainerConfigurationStep()
                .runPushApplicationLayersStep()
                .runFinalizingPushStep()
                .runPushImageStep()
                .waitOnPushImageStep());
  }

  /**
   * All the steps to build to Docker daemon
   *
   * @param buildConfiguration the configuration parameters for the build
   * @param cachesInitializer the {@link Caches.Initializer} used to setup the cache
   * @return a new {@link BuildSteps} for building to a Docker daemon
   */
  public static BuildSteps forBuildToDockerDaemon(
      BuildConfiguration buildConfiguration, Caches.Initializer cachesInitializer) {
    return new BuildSteps(
        DESCRIPTION_FOR_DOCKER_DAEMON,
        buildConfiguration,
        cachesInitializer,
        stepsRunner ->
            stepsRunner
                .runPullBaseImageStep()
                .runPullAndCacheBaseImageLayersStep()
                .runBuildAndCacheApplicationLayerSteps()
                .runBuildImageStep()
                .runFinalizingBuildStep()
                .runLoadDockerStep()
                .waitOnLoadDockerStep());
  }

  /**
   * All the steps to build an image tarball.
   *
   * @param outputPath the path to output the tarball to
   * @param buildConfiguration the configuration parameters for the build
   * @param cachesInitializer the {@link Caches.Initializer} used to setup the cache
   * @return a new {@link BuildSteps} for building a tarball
   */
  public static BuildSteps forBuildToTar(
      Path outputPath,
      BuildConfiguration buildConfiguration,
      Caches.Initializer cachesInitializer) {
    return new BuildSteps(
        DESCRIPTION_FOR_TARBALL,
        buildConfiguration,
        cachesInitializer,
        stepsRunner ->
            stepsRunner
                .runPullBaseImageStep()
                .runPullAndCacheBaseImageLayersStep()
                .runBuildAndCacheApplicationLayerSteps()
                .runBuildImageStep()
                .runFinalizingBuildStep()
                .runWriteTarFileStep(outputPath)
                .waitOnWriteTarFileStep());
  }

  private final String description;
  private final BuildConfiguration buildConfiguration;
  private final Caches.Initializer cachesInitializer;
  private final StepsRunnerConsumer stepsRunnerConsumer;

  /**
   * @param description a description of what the steps do
   * @param stepsRunnerConsumer accepts a {@link StepsRunner} by running the necessary steps
   */
  private BuildSteps(
      String description,
      BuildConfiguration buildConfiguration,
      Caches.Initializer cachesInitializer,
      StepsRunnerConsumer stepsRunnerConsumer) {
    this.description = description;
    this.buildConfiguration = buildConfiguration;
    this.cachesInitializer = cachesInitializer;
    this.stepsRunnerConsumer = stepsRunnerConsumer;
  }

  public BuildConfiguration getBuildConfiguration() {
    return buildConfiguration;
  }

  public void run()
      throws InterruptedException, ExecutionException, CacheMetadataCorruptedException, IOException,
          CacheDirectoryNotOwnedException, CacheDirectoryCreationException {
    buildConfiguration.getBuildLogger().lifecycle("");

    try (Timer ignored = new Timer(buildConfiguration.getBuildLogger(), description)) {
      try (Caches caches = cachesInitializer.init()) {
        Cache baseImageLayersCache = caches.getBaseCache();
        Cache applicationLayersCache = caches.getApplicationCache();

        StepsRunner stepsRunner =
            new StepsRunner(buildConfiguration, baseImageLayersCache, applicationLayersCache);
        stepsRunnerConsumer.accept(stepsRunner);

        // Writes the cached layers to the cache metadata.
        baseImageLayersCache.addCachedLayersToMetadata(stepsRunner.getCachedBaseImageLayers());
        applicationLayersCache.addCachedLayersWithMetadataToMetadata(
            stepsRunner.getCachedApplicationLayers());
      }
    }

    if (buildConfiguration.getContainerConfiguration() != null) {
      buildConfiguration.getBuildLogger().lifecycle("");
      buildConfiguration
          .getBuildLogger()
          .lifecycle(
              "Container entrypoint set to "
                  + buildConfiguration.getContainerConfiguration().getEntrypoint());
    }
  }
}
