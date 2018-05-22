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

import com.google.cloud.tools.jib.cache.Cache;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import javax.annotation.Nullable;

/**
 * Runs steps for building an image.
 *
 * <p>Use by constructing the runner and calling {@code run...} each step. Make sure that steps are
 * run before other steps that depend on them. Wait on the last step.
 */
class StepsRunner {

  private final ListeningExecutorService listeningExecutorService =
      MoreExecutors.listeningDecorator(Executors.newCachedThreadPool());
  private final BuildConfiguration buildConfiguration;
  private final SourceFilesConfiguration sourceFilesConfiguration;
  private final Cache baseLayersCache;
  private final Cache applicationLayersCache;

  @Nullable private RetrieveRegistryCredentialsStep retrieveBaseRegistryCredentialsStep;
  @Nullable private RetrieveRegistryCredentialsStep retrieveTargetRegistryCredentialsStep;
  @Nullable private AuthenticatePullStep authenticatePullStep;
  @Nullable private AuthenticatePushStep authenticatePushStep;
  @Nullable private PullBaseImageStep pullBaseImageStep;
  @Nullable private PullAndCacheBaseImageLayersStep pullAndCacheBaseImageLayersStep;

  @Nullable
  private ImmutableList<BuildAndCacheApplicationLayerStep> buildAndCacheApplicationLayerSteps;

  @Nullable private PushLayersStep pushBaseImageLayersStep;
  @Nullable private PushLayersStep pushApplicationLayersStep;
  @Nullable private BuildImageStep buildImageStep;
  @Nullable private PushContainerConfigurationStep pushContainerConfigurationStep;
  @Nullable private PushImageStep pushImageStep;
  @Nullable private BuildTarballAndLoadDockerStep buildTarballAndLoadDockerStep;

  StepsRunner(
      BuildConfiguration buildConfiguration,
      SourceFilesConfiguration sourceFilesConfiguration,
      Cache baseLayersCache,
      Cache applicationLayersCache) {
    this.buildConfiguration = buildConfiguration;
    this.sourceFilesConfiguration = sourceFilesConfiguration;
    this.baseLayersCache = baseLayersCache;
    this.applicationLayersCache = applicationLayersCache;
  }

  StepsRunner runRetrieveBaseRegistryCredentialsStep() {
    retrieveBaseRegistryCredentialsStep =
        RetrieveRegistryCredentialsStep.forBaseImage(listeningExecutorService, buildConfiguration);
    return this;
  }

  StepsRunner runRetrieveTargetRegistryCredentialsStep() {
    retrieveTargetRegistryCredentialsStep =
        RetrieveRegistryCredentialsStep.forTargetImage(
            listeningExecutorService, buildConfiguration);
    return this;
  }

  StepsRunner runAuthenticatePushStep() {
    authenticatePushStep =
        new AuthenticatePushStep(
            listeningExecutorService,
            buildConfiguration,
            Preconditions.checkNotNull(retrieveTargetRegistryCredentialsStep));
    return this;
  }

  StepsRunner runAuthenticatePullStep() {
    authenticatePullStep =
        new AuthenticatePullStep(
            listeningExecutorService,
            buildConfiguration,
            Preconditions.checkNotNull(retrieveBaseRegistryCredentialsStep));
    return this;
  }

  StepsRunner runPullBaseImageStep() {
    pullBaseImageStep =
        new PullBaseImageStep(
            listeningExecutorService,
            buildConfiguration,
            Preconditions.checkNotNull(authenticatePullStep));
    return this;
  }

  StepsRunner runPullAndCacheBaseImageLayersStep() {
    pullAndCacheBaseImageLayersStep =
        new PullAndCacheBaseImageLayersStep(
            listeningExecutorService,
            buildConfiguration,
            baseLayersCache,
            Preconditions.checkNotNull(authenticatePullStep),
            Preconditions.checkNotNull(pullBaseImageStep));
    return this;
  }

  StepsRunner runPushBaseImageLayersStep() {
    pushBaseImageLayersStep =
        new PushLayersStep(
            listeningExecutorService,
            buildConfiguration,
            Preconditions.checkNotNull(authenticatePushStep),
            Preconditions.checkNotNull(pullAndCacheBaseImageLayersStep));
    return this;
  }

  StepsRunner runBuildAndCacheApplicationLayerSteps() {
    buildAndCacheApplicationLayerSteps =
        BuildAndCacheApplicationLayerStep.makeList(
            listeningExecutorService,
            buildConfiguration,
            sourceFilesConfiguration,
            applicationLayersCache);
    return this;
  }

  StepsRunner runBuildImageStep(ImmutableList<String> entrypoint) {
    buildImageStep =
        new BuildImageStep(
            listeningExecutorService,
            buildConfiguration,
            Preconditions.checkNotNull(pullAndCacheBaseImageLayersStep),
            Preconditions.checkNotNull(buildAndCacheApplicationLayerSteps),
            entrypoint);
    return this;
  }

  StepsRunner runPushContainerConfigurationStep() {
    pushContainerConfigurationStep =
        new PushContainerConfigurationStep(
            listeningExecutorService,
            buildConfiguration,
            Preconditions.checkNotNull(authenticatePushStep),
            Preconditions.checkNotNull(buildImageStep));
    return this;
  }

  StepsRunner runPushApplicationLayersStep() {
    pushApplicationLayersStep =
        new PushLayersStep(
            listeningExecutorService,
            buildConfiguration,
            Preconditions.checkNotNull(authenticatePushStep),
            AsyncSteps.immediate(Preconditions.checkNotNull(buildAndCacheApplicationLayerSteps)));
    return this;
  }

  StepsRunner runFinalizingPushStep() {
    new FinalizingStep(
        listeningExecutorService,
        buildConfiguration,
        Arrays.asList(
            Preconditions.checkNotNull(pushBaseImageLayersStep),
            Preconditions.checkNotNull(pushApplicationLayersStep)),
        Collections.emptyList());
    return this;
  }

  StepsRunner runFinalizingBuildStep() {
    new FinalizingStep(
        listeningExecutorService,
        buildConfiguration,
        Collections.singletonList(Preconditions.checkNotNull(pullAndCacheBaseImageLayersStep)),
        Preconditions.checkNotNull(buildAndCacheApplicationLayerSteps));
    return this;
  }

  StepsRunner runPushImageStep() {
    pushImageStep =
        new PushImageStep(
            listeningExecutorService,
            buildConfiguration,
            Preconditions.checkNotNull(authenticatePushStep),
            Preconditions.checkNotNull(pushBaseImageLayersStep),
            Preconditions.checkNotNull(pushApplicationLayersStep),
            Preconditions.checkNotNull(pushContainerConfigurationStep),
            Preconditions.checkNotNull(buildImageStep));
    return this;
  }

  StepsRunner runBuildTarballAndLoadDockerStep() {
    buildTarballAndLoadDockerStep =
        new BuildTarballAndLoadDockerStep(
            listeningExecutorService,
            buildConfiguration,
            Preconditions.checkNotNull(pullAndCacheBaseImageLayersStep),
            Preconditions.checkNotNull(buildAndCacheApplicationLayerSteps),
            Preconditions.checkNotNull(buildImageStep));
    return this;
  }

  void waitOnPushImageStep() throws ExecutionException, InterruptedException {
    Preconditions.checkNotNull(pushImageStep).getFuture().get();
  }

  void waitOnBuildTarballAndLoadDockerStep() throws ExecutionException, InterruptedException {
    Preconditions.checkNotNull(buildTarballAndLoadDockerStep).getFuture().get();
  }
}
