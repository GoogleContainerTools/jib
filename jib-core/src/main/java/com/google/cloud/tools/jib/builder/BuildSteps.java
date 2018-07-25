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

  /** Accepts {@link StepsRunner} by running the appropriate steps. */
  @FunctionalInterface
  private interface StepsRunnerConsumer {

    void accept(StepsRunner stepsRunner) throws ExecutionException, InterruptedException;
  }

  private static final String DESCRIPTION_FOR_DOCKER_REGISTRY = "Building and pushing image";
  private static final String STARTUP_MESSAGE_FORMAT_FOR_DOCKER_REGISTRY =
      "Containerizing application to %s...";
  // String parameter (target image reference) in cyan.
  private static final String SUCCESS_MESSAGE_FORMAT_FOR_DOCKER_REGISTRY =
      "Built and pushed image as \u001B[36m%s\u001B[0m";

  private static final String DESCRIPTION_FOR_DOCKER_DAEMON = "Building image to Docker daemon";
  private static final String STARTUP_MESSAGE_FORMAT_FOR_DOCKER_DAEMON =
      "Containerizing application to Docker daemon as %s...";
  // String parameter (target image reference) in cyan.
  private static final String SUCCESS_MESSAGE_FORMAT_FOR_DOCKER_DAEMON =
      "Built image to Docker daemon as \u001B[36m%s\u001B[0m";

  private static final String DESCRIPTION_FOR_TARBALL = "Building image tarball";
  private static final String STARTUP_MESSAGE_FORMAT_FOR_TARBALL =
      "Containerizing application to file at '%s'...";
  // String parameter (target file) in cyan.
  private static final String SUCCESS_MESSAGE_FORMAT_FOR_TARBALL =
      "Built image tarball at \u001B[36m%s\u001B[0m";

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
        String.format(
            STARTUP_MESSAGE_FORMAT_FOR_DOCKER_REGISTRY,
            buildConfiguration.getTargetImageReference()),
        String.format(
            SUCCESS_MESSAGE_FORMAT_FOR_DOCKER_REGISTRY,
            buildConfiguration.getTargetImageReference()),
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
        String.format(
            STARTUP_MESSAGE_FORMAT_FOR_DOCKER_DAEMON, buildConfiguration.getTargetImageReference()),
        String.format(
            SUCCESS_MESSAGE_FORMAT_FOR_DOCKER_DAEMON, buildConfiguration.getTargetImageReference()),
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
        String.format(STARTUP_MESSAGE_FORMAT_FOR_TARBALL, outputPath.toString()),
        String.format(SUCCESS_MESSAGE_FORMAT_FOR_TARBALL, outputPath.toString()),
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
  private final String startupMessage;
  private final String successMessage;
  private final StepsRunnerConsumer stepsRunnerConsumer;

  /**
   * @param description a description of what the steps do
   * @param startupMessage shown when the steps start running
   * @param successMessage shown when the steps finish successfully
   * @param stepsRunnerConsumer accepts a {@link StepsRunner} by running the necessary steps
   */
  private BuildSteps(
      String description,
      BuildConfiguration buildConfiguration,
      Caches.Initializer cachesInitializer,
      String startupMessage,
      String successMessage,
      StepsRunnerConsumer stepsRunnerConsumer) {
    this.description = description;
    this.buildConfiguration = buildConfiguration;
    this.cachesInitializer = cachesInitializer;
    this.startupMessage = startupMessage;
    this.successMessage = successMessage;
    this.stepsRunnerConsumer = stepsRunnerConsumer;
  }

  public BuildConfiguration getBuildConfiguration() {
    return buildConfiguration;
  }

  public String getStartupMessage() {
    return startupMessage;
  }

  public String getSuccessMessage() {
    return successMessage;
  }

  public void run()
      throws InterruptedException, ExecutionException, CacheMetadataCorruptedException, IOException,
          CacheDirectoryNotOwnedException, CacheDirectoryCreationException {
    buildConfiguration.getBuildLogger().lifecycle("");

    try (Timer timer = new Timer(buildConfiguration.getBuildLogger(), description)) {
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

    buildConfiguration.getBuildLogger().lifecycle("");
    buildConfiguration
        .getBuildLogger()
        .lifecycle("Container entrypoint set to " + buildConfiguration.getEntrypoint());
  }
}
