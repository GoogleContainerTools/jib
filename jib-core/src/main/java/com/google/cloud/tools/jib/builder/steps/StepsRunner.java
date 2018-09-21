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

package com.google.cloud.tools.jib.builder.steps;

import com.google.cloud.tools.jib.async.AsyncSteps;
import com.google.cloud.tools.jib.async.NonBlockingSteps;
import com.google.cloud.tools.jib.cache.Cache;
import com.google.cloud.tools.jib.cache.CachedLayer;
import com.google.cloud.tools.jib.cache.CachedLayerWithMetadata;
import com.google.cloud.tools.jib.configuration.BuildConfiguration;
import com.google.cloud.tools.jib.global.JibSystemProperties;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.annotation.Nullable;

/**
 * Runs steps for building an image.
 *
 * <p>Use by constructing the runner and calling {@code run...} each step. Make sure that steps are
 * run before other steps that depend on them. Wait on the last step.
 */
public class StepsRunner {

  private final ListeningExecutorService listeningExecutorService;
  private final BuildConfiguration buildConfiguration;
  private final Cache baseLayersCache;
  private final Cache applicationLayersCache;

  @Nullable private RetrieveRegistryCredentialsStep retrieveTargetRegistryCredentialsStep;
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
  @Nullable private LoadDockerStep loadDockerStep;
  @Nullable private WriteTarFileStep writeTarFileStep;

  public StepsRunner(
      BuildConfiguration buildConfiguration, Cache baseLayersCache, Cache applicationLayersCache) {
    this.buildConfiguration = buildConfiguration;
    this.baseLayersCache = baseLayersCache;
    this.applicationLayersCache = applicationLayersCache;

    ExecutorService executorService =
        JibSystemProperties.isSerializedExecutionEnabled()
            ? MoreExecutors.newDirectExecutorService()
            : Executors.newCachedThreadPool();
    listeningExecutorService = MoreExecutors.listeningDecorator(executorService);
  }

  public StepsRunner runRetrieveTargetRegistryCredentialsStep() {
    retrieveTargetRegistryCredentialsStep =
        RetrieveRegistryCredentialsStep.forTargetImage(
            listeningExecutorService, buildConfiguration);
    return this;
  }

  public StepsRunner runAuthenticatePushStep() {
    authenticatePushStep =
        new AuthenticatePushStep(
            listeningExecutorService,
            buildConfiguration,
            Preconditions.checkNotNull(retrieveTargetRegistryCredentialsStep));
    return this;
  }

  public StepsRunner runPullBaseImageStep() {
    pullBaseImageStep = new PullBaseImageStep(listeningExecutorService, buildConfiguration);
    return this;
  }

  public StepsRunner runPullAndCacheBaseImageLayersStep() {
    pullAndCacheBaseImageLayersStep =
        new PullAndCacheBaseImageLayersStep(
            listeningExecutorService,
            buildConfiguration,
            baseLayersCache,
            Preconditions.checkNotNull(pullBaseImageStep));
    return this;
  }

  public StepsRunner runPushBaseImageLayersStep() {
    pushBaseImageLayersStep =
        new PushLayersStep(
            listeningExecutorService,
            buildConfiguration,
            Preconditions.checkNotNull(authenticatePushStep),
            Preconditions.checkNotNull(pullAndCacheBaseImageLayersStep));
    return this;
  }

  public StepsRunner runBuildAndCacheApplicationLayerSteps() {
    buildAndCacheApplicationLayerSteps =
        BuildAndCacheApplicationLayerStep.makeList(
            listeningExecutorService, buildConfiguration, applicationLayersCache);
    return this;
  }

  public StepsRunner runBuildImageStep() {
    buildImageStep =
        new BuildImageStep(
            listeningExecutorService,
            buildConfiguration,
            Preconditions.checkNotNull(pullBaseImageStep),
            Preconditions.checkNotNull(pullAndCacheBaseImageLayersStep),
            Preconditions.checkNotNull(buildAndCacheApplicationLayerSteps));
    return this;
  }

  public StepsRunner runPushContainerConfigurationStep() {
    pushContainerConfigurationStep =
        new PushContainerConfigurationStep(
            listeningExecutorService,
            buildConfiguration,
            Preconditions.checkNotNull(authenticatePushStep),
            Preconditions.checkNotNull(buildImageStep));
    return this;
  }

  public StepsRunner runPushApplicationLayersStep() {
    pushApplicationLayersStep =
        new PushLayersStep(
            listeningExecutorService,
            buildConfiguration,
            Preconditions.checkNotNull(authenticatePushStep),
            AsyncSteps.immediate(Preconditions.checkNotNull(buildAndCacheApplicationLayerSteps)));
    return this;
  }

  public StepsRunner runFinalizingPushStep() {
    new FinalizingStep(
        listeningExecutorService,
        buildConfiguration,
        Arrays.asList(
            Preconditions.checkNotNull(pushBaseImageLayersStep),
            Preconditions.checkNotNull(pushApplicationLayersStep)),
        Collections.emptyList());
    return this;
  }

  public StepsRunner runFinalizingBuildStep() {
    new FinalizingStep(
        listeningExecutorService,
        buildConfiguration,
        Collections.singletonList(Preconditions.checkNotNull(pullAndCacheBaseImageLayersStep)),
        Preconditions.checkNotNull(buildAndCacheApplicationLayerSteps));
    return this;
  }

  public StepsRunner runPushImageStep() {
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

  public StepsRunner runLoadDockerStep() {
    loadDockerStep =
        new LoadDockerStep(
            listeningExecutorService,
            buildConfiguration,
            Preconditions.checkNotNull(pullAndCacheBaseImageLayersStep),
            Preconditions.checkNotNull(buildAndCacheApplicationLayerSteps),
            Preconditions.checkNotNull(buildImageStep));
    return this;
  }

  public StepsRunner runWriteTarFileStep(Path outputPath) {
    writeTarFileStep =
        new WriteTarFileStep(
            listeningExecutorService,
            outputPath,
            buildConfiguration,
            Preconditions.checkNotNull(pullAndCacheBaseImageLayersStep),
            Preconditions.checkNotNull(buildAndCacheApplicationLayerSteps),
            Preconditions.checkNotNull(buildImageStep));
    return this;
  }

  public void waitOnPushImageStep() throws ExecutionException, InterruptedException {
    Preconditions.checkNotNull(pushImageStep).getFuture().get();
  }

  public void waitOnLoadDockerStep() throws ExecutionException, InterruptedException {
    Preconditions.checkNotNull(loadDockerStep).getFuture().get();
  }

  public void waitOnWriteTarFileStep() throws ExecutionException, InterruptedException {
    Preconditions.checkNotNull(writeTarFileStep).getFuture().get();
  }

  /**
   * @return the layers cached by {@link #pullAndCacheBaseImageLayersStep}
   * @throws ExecutionException if {@link #pullAndCacheBaseImageLayersStep} threw an exception
   *     during execution
   */
  public List<CachedLayer> getCachedBaseImageLayers() throws ExecutionException {
    ImmutableList<PullAndCacheBaseImageLayerStep> pullAndCacheBaseImageLayerSteps =
        NonBlockingSteps.get(Preconditions.checkNotNull(pullAndCacheBaseImageLayersStep));

    List<CachedLayer> cachedLayers = new ArrayList<>(pullAndCacheBaseImageLayerSteps.size());
    for (PullAndCacheBaseImageLayerStep pullAndCacheBaseImageLayerStep :
        pullAndCacheBaseImageLayerSteps) {
      cachedLayers.add(NonBlockingSteps.get(pullAndCacheBaseImageLayerStep));
    }
    return cachedLayers;
  }

  /**
   * @return the layers cached by {@link #buildAndCacheApplicationLayerSteps}
   * @throws ExecutionException if {@link #buildAndCacheApplicationLayerSteps} threw an exception
   *     during execution
   */
  public List<CachedLayerWithMetadata> getCachedApplicationLayers() throws ExecutionException {
    List<CachedLayerWithMetadata> cachedLayersWithMetadata =
        new ArrayList<>(Preconditions.checkNotNull(buildAndCacheApplicationLayerSteps).size());
    for (BuildAndCacheApplicationLayerStep buildAndCacheApplicationLayerStep :
        buildAndCacheApplicationLayerSteps) {
      cachedLayersWithMetadata.add(NonBlockingSteps.get(buildAndCacheApplicationLayerStep));
    }
    return cachedLayersWithMetadata;
  }
}
