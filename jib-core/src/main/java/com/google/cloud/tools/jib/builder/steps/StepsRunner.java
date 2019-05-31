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

import com.google.cloud.tools.jib.async.AsyncStep;
import com.google.cloud.tools.jib.async.AsyncSteps;
import com.google.cloud.tools.jib.builder.ProgressEventDispatcher;
import com.google.cloud.tools.jib.configuration.BuildConfiguration;
import com.google.cloud.tools.jib.docker.DockerClient;
import com.google.cloud.tools.jib.global.JibSystemProperties;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import javax.annotation.Nullable;

/**
 * Runs steps for building an image.
 *
 * <p>Use by first calling {@link #begin} and then calling the individual step running methods. Note
 * that order matters, so make sure that steps are run before other steps that depend on them. Wait
 * on the last step by calling the respective {@code wait...} methods.
 */
public class StepsRunner {

  /** Holds the individual steps. */
  private static class Steps {

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

    @Nullable private AsyncStep<BuildResult> finalStep;
  }

  /**
   * Starts building the steps to run.
   *
   * @param buildConfiguration the {@link BuildConfiguration}
   * @return a new {@link StepsRunner}
   */
  public static StepsRunner begin(BuildConfiguration buildConfiguration) {
    ExecutorService executorService =
        JibSystemProperties.isSerializedExecutionEnabled()
            ? MoreExecutors.newDirectExecutorService()
            : buildConfiguration.getExecutorService();

    return new StepsRunner(MoreExecutors.listeningDecorator(executorService), buildConfiguration);
  }

  private final Steps steps = new Steps();

  private final ListeningExecutorService listeningExecutorService;
  private final BuildConfiguration buildConfiguration;

  /** Runnable to run all the steps. */
  private Runnable stepsRunnable = () -> {};

  /** The total number of steps added. */
  private int stepsCount = 0;

  @Nullable private String rootProgressAllocationDescription;
  @Nullable private ProgressEventDispatcher rootProgressEventDispatcher;

  private StepsRunner(
      ListeningExecutorService listeningExecutorService, BuildConfiguration buildConfiguration) {
    this.listeningExecutorService = listeningExecutorService;
    this.buildConfiguration = buildConfiguration;
  }

  public StepsRunner retrieveTargetRegistryCredentials() {
    return enqueueStep(
        () ->
            steps.retrieveTargetRegistryCredentialsStep =
                RetrieveRegistryCredentialsStep.forTargetImage(
                    listeningExecutorService,
                    buildConfiguration,
                    Preconditions.checkNotNull(rootProgressEventDispatcher).newChildProducer()));
  }

  public StepsRunner authenticatePush() {
    return enqueueStep(
        () ->
            steps.authenticatePushStep =
                new AuthenticatePushStep(
                    listeningExecutorService,
                    buildConfiguration,
                    Preconditions.checkNotNull(rootProgressEventDispatcher).newChildProducer(),
                    Preconditions.checkNotNull(steps.retrieveTargetRegistryCredentialsStep)));
  }

  public StepsRunner pullBaseImage() {
    return enqueueStep(
        () ->
            steps.pullBaseImageStep =
                new PullBaseImageStep(
                    listeningExecutorService,
                    buildConfiguration,
                    Preconditions.checkNotNull(rootProgressEventDispatcher).newChildProducer()));
  }

  public StepsRunner pullAndCacheBaseImageLayers() {
    return enqueueStep(
        () ->
            steps.pullAndCacheBaseImageLayersStep =
                new PullAndCacheBaseImageLayersStep(
                    listeningExecutorService,
                    buildConfiguration,
                    Preconditions.checkNotNull(rootProgressEventDispatcher).newChildProducer(),
                    Preconditions.checkNotNull(steps.pullBaseImageStep)));
  }

  public StepsRunner pushBaseImageLayers() {
    return enqueueStep(
        () ->
            steps.pushBaseImageLayersStep =
                new PushLayersStep(
                    listeningExecutorService,
                    buildConfiguration,
                    Preconditions.checkNotNull(rootProgressEventDispatcher).newChildProducer(),
                    Preconditions.checkNotNull(steps.authenticatePushStep),
                    Preconditions.checkNotNull(steps.pullAndCacheBaseImageLayersStep)));
  }

  public StepsRunner buildAndCacheApplicationLayers() {
    return enqueueStep(
        () ->
            steps.buildAndCacheApplicationLayerSteps =
                BuildAndCacheApplicationLayerStep.makeList(
                    listeningExecutorService,
                    buildConfiguration,
                    Preconditions.checkNotNull(rootProgressEventDispatcher).newChildProducer()));
  }

  public StepsRunner buildImage() {
    return enqueueStep(
        () ->
            steps.buildImageStep =
                new BuildImageStep(
                    listeningExecutorService,
                    buildConfiguration,
                    Preconditions.checkNotNull(rootProgressEventDispatcher).newChildProducer(),
                    Preconditions.checkNotNull(steps.pullBaseImageStep),
                    Preconditions.checkNotNull(steps.pullAndCacheBaseImageLayersStep),
                    Preconditions.checkNotNull(steps.buildAndCacheApplicationLayerSteps)));
  }

  public StepsRunner pushContainerConfiguration() {
    return enqueueStep(
        () ->
            steps.pushContainerConfigurationStep =
                new PushContainerConfigurationStep(
                    listeningExecutorService,
                    buildConfiguration,
                    Preconditions.checkNotNull(rootProgressEventDispatcher).newChildProducer(),
                    Preconditions.checkNotNull(steps.authenticatePushStep),
                    Preconditions.checkNotNull(steps.buildImageStep)));
  }

  public StepsRunner pushApplicationLayers() {
    return enqueueStep(
        () ->
            steps.pushApplicationLayersStep =
                new PushLayersStep(
                    listeningExecutorService,
                    buildConfiguration,
                    Preconditions.checkNotNull(rootProgressEventDispatcher).newChildProducer(),
                    Preconditions.checkNotNull(steps.authenticatePushStep),
                    AsyncSteps.immediate(
                        Preconditions.checkNotNull(steps.buildAndCacheApplicationLayerSteps))));
  }

  public StepsRunner pushImage() {
    rootProgressAllocationDescription = "building image to registry";

    return enqueueStep(
        () ->
            steps.finalStep =
                new PushImageStep(
                    listeningExecutorService,
                    buildConfiguration,
                    Preconditions.checkNotNull(rootProgressEventDispatcher).newChildProducer(),
                    Preconditions.checkNotNull(steps.authenticatePushStep),
                    Preconditions.checkNotNull(steps.pushBaseImageLayersStep),
                    Preconditions.checkNotNull(steps.pushApplicationLayersStep),
                    Preconditions.checkNotNull(steps.pushContainerConfigurationStep),
                    Preconditions.checkNotNull(steps.buildImageStep)));
  }

  public StepsRunner loadDocker(DockerClient dockerClient) {
    rootProgressAllocationDescription = "building image to Docker daemon";

    return enqueueStep(
        () ->
            steps.finalStep =
                new LoadDockerStep(
                    listeningExecutorService,
                    buildConfiguration,
                    Preconditions.checkNotNull(rootProgressEventDispatcher).newChildProducer(),
                    dockerClient,
                    Preconditions.checkNotNull(steps.pullAndCacheBaseImageLayersStep),
                    Preconditions.checkNotNull(steps.buildAndCacheApplicationLayerSteps),
                    Preconditions.checkNotNull(steps.buildImageStep)));
  }

  public StepsRunner writeTarFile(Path outputPath) {
    rootProgressAllocationDescription = "building image to tar file";

    return enqueueStep(
        () ->
            steps.finalStep =
                new WriteTarFileStep(
                    listeningExecutorService,
                    buildConfiguration,
                    Preconditions.checkNotNull(rootProgressEventDispatcher).newChildProducer(),
                    outputPath,
                    Preconditions.checkNotNull(steps.pullAndCacheBaseImageLayersStep),
                    Preconditions.checkNotNull(steps.buildAndCacheApplicationLayerSteps),
                    Preconditions.checkNotNull(steps.buildImageStep)));
  }

  public BuildResult run() throws ExecutionException, InterruptedException {
    Preconditions.checkNotNull(rootProgressAllocationDescription);

    try (ProgressEventDispatcher progressEventDispatcher =
        ProgressEventDispatcher.newRoot(
            buildConfiguration.getEventHandlers(), rootProgressAllocationDescription, stepsCount)) {
      rootProgressEventDispatcher = progressEventDispatcher;
      stepsRunnable.run();
      return Preconditions.checkNotNull(steps.finalStep).getFuture().get();
    }
  }

  private StepsRunner enqueueStep(Runnable stepRunnable) {
    Runnable previousStepsRunnable = stepsRunnable;
    stepsRunnable =
        () -> {
          previousStepsRunnable.run();
          stepRunnable.run();
        };
    stepsCount++;
    return this;
  }
}
