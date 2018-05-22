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
import java.io.IOException;
import java.util.concurrent.ExecutionException;

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
      try (Caches caches = cachesInitializer.init()) {
        Cache baseLayersCache = caches.getBaseCache();
        Cache applicationLayersCache = caches.getApplicationCache();

        new StepsRunner(
                buildConfiguration,
                sourceFilesConfiguration,
                baseLayersCache,
                applicationLayersCache)
            .runRetrieveBaseRegistryCredentialsStep()
            .runAuthenticatePullStep()
            .runPullBaseImageStep()
            .runPullAndCacheBaseImageLayersStep()
            .runBuildAndCacheApplicationLayerSteps()
            .runBuildImageStep(entrypoint)
            .runFinalizingBuildStep()
            .runBuildTarballAndLoadDockerStep()
            .waitOnBuildTarballAndLoadDockerStep();
      }
    }

    buildConfiguration.getBuildLogger().lifecycle("");
    buildConfiguration.getBuildLogger().lifecycle("Container entrypoint set to " + entrypoint);
  }
}
