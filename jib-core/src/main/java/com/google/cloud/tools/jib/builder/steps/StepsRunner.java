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
import com.google.cloud.tools.jib.configuration.BuildConfiguration;
import com.google.cloud.tools.jib.docker.DockerClient;
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
    @Nullable private PushImageStep pushImageStep;
    @Nullable private LoadDockerStep loadDockerStep;
    @Nullable private WriteTarFileStep writeTarFileStep;
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

  /** Collects the functions to start running the steps. */
  private final List<Runnable> stepsRunnables = new ArrayList<>();

  private StepsRunner(
      ListeningExecutorService listeningExecutorService, BuildConfiguration buildConfiguration) {
    this.listeningExecutorService = listeningExecutorService;
    this.buildConfiguration = buildConfiguration;
  }

  public StepsRunner retrieveTargetRegistryCredentials() {
    stepsRunnables.add(
        () ->
            steps.retrieveTargetRegistryCredentialsStep =
                RetrieveRegistryCredentialsStep.forTargetImage(
                    listeningExecutorService, buildConfiguration));
    return this;
  }

  public StepsRunner authenticatePush() {
    stepsRunnables.add(
        () ->
            steps.authenticatePushStep =
                new AuthenticatePushStep(
                    listeningExecutorService,
                    buildConfiguration,
                    Preconditions.checkNotNull(steps.retrieveTargetRegistryCredentialsStep)));
    return this;
  }

  public StepsRunner pullBaseImage() {
    stepsRunnables.add(
        () ->
            steps.pullBaseImageStep =
                new PullBaseImageStep(listeningExecutorService, buildConfiguration));
    return this;
  }

  public StepsRunner pullAndCacheBaseImageLayers() {
    stepsRunnables.add(
        () ->
            steps.pullAndCacheBaseImageLayersStep =
                new PullAndCacheBaseImageLayersStep(
                    listeningExecutorService,
                    buildConfiguration,
                    Preconditions.checkNotNull(steps.pullBaseImageStep)));
    return this;
  }

  public StepsRunner pushBaseImageLayers() {
    stepsRunnables.add(
        () ->
            steps.pushBaseImageLayersStep =
                new PushLayersStep(
                    listeningExecutorService,
                    buildConfiguration,
                    Preconditions.checkNotNull(steps.authenticatePushStep),
                    Preconditions.checkNotNull(steps.pullAndCacheBaseImageLayersStep)));
    return this;
  }

  public StepsRunner buildAndCacheApplicationLayers() {
    stepsRunnables.add(
        () ->
            steps.buildAndCacheApplicationLayerSteps =
                BuildAndCacheApplicationLayerStep.makeList(
                    listeningExecutorService, buildConfiguration));
    return this;
  }

  public StepsRunner buildImage() {
    stepsRunnables.add(
        () ->
            steps.buildImageStep =
                new BuildImageStep(
                    listeningExecutorService,
                    buildConfiguration,
                    Preconditions.checkNotNull(steps.pullBaseImageStep),
                    Preconditions.checkNotNull(steps.pullAndCacheBaseImageLayersStep),
                    Preconditions.checkNotNull(steps.buildAndCacheApplicationLayerSteps)));
    return this;
  }

  public StepsRunner pushContainerConfiguration() {
    stepsRunnables.add(
        () ->
            steps.pushContainerConfigurationStep =
                new PushContainerConfigurationStep(
                    listeningExecutorService,
                    buildConfiguration,
                    Preconditions.checkNotNull(steps.authenticatePushStep),
                    Preconditions.checkNotNull(steps.buildImageStep)));
    return this;
  }

  public StepsRunner pushApplicationLayers() {
    stepsRunnables.add(
        () ->
            steps.pushApplicationLayersStep =
                new PushLayersStep(
                    listeningExecutorService,
                    buildConfiguration,
                    Preconditions.checkNotNull(steps.authenticatePushStep),
                    AsyncSteps.immediate(
                        Preconditions.checkNotNull(steps.buildAndCacheApplicationLayerSteps))));
    return this;
  }

  public StepsRunner finalizingPush() {
    stepsRunnables.add(
        () ->
            new FinalizingStep(
                listeningExecutorService,
                buildConfiguration,
                Arrays.asList(
                    Preconditions.checkNotNull(steps.pushBaseImageLayersStep),
                    Preconditions.checkNotNull(steps.pushApplicationLayersStep)),
                Collections.emptyList()));
    return this;
  }

  public StepsRunner finalizingBuild() {
    stepsRunnables.add(
        () ->
            new FinalizingStep(
                listeningExecutorService,
                buildConfiguration,
                Collections.singletonList(
                    Preconditions.checkNotNull(steps.pullAndCacheBaseImageLayersStep)),
                Preconditions.checkNotNull(steps.buildAndCacheApplicationLayerSteps)));
    return this;
  }

  public StepsRunner pushImage() {
    stepsRunnables.add(
        () ->
            steps.pushImageStep =
                new PushImageStep(
                    listeningExecutorService,
                    buildConfiguration,
                    Preconditions.checkNotNull(steps.authenticatePushStep),
                    Preconditions.checkNotNull(steps.pushBaseImageLayersStep),
                    Preconditions.checkNotNull(steps.pushApplicationLayersStep),
                    Preconditions.checkNotNull(steps.pushContainerConfigurationStep),
                    Preconditions.checkNotNull(steps.buildImageStep)));
    return this;
  }

  public StepsRunner loadDocker(DockerClient dockerClient) {
    stepsRunnables.add(
        () ->
            steps.loadDockerStep =
                new LoadDockerStep(
                    listeningExecutorService,
                    dockerClient,
                    buildConfiguration,
                    Preconditions.checkNotNull(steps.pullAndCacheBaseImageLayersStep),
                    Preconditions.checkNotNull(steps.buildAndCacheApplicationLayerSteps),
                    Preconditions.checkNotNull(steps.buildImageStep)));
    return this;
  }

  public StepsRunner writeTarFile(Path outputPath) {
    stepsRunnables.add(
        () ->
            steps.writeTarFileStep =
                new WriteTarFileStep(
                    listeningExecutorService,
                    outputPath,
                    buildConfiguration,
                    Preconditions.checkNotNull(steps.pullAndCacheBaseImageLayersStep),
                    Preconditions.checkNotNull(steps.buildAndCacheApplicationLayerSteps),
                    Preconditions.checkNotNull(steps.buildImageStep)));
    return this;
  }

  public BuildResult waitOnPushImage() throws ExecutionException, InterruptedException {
    runStepsRunners();
    return Preconditions.checkNotNull(steps.pushImageStep).getFuture().get();
  }

  public BuildResult waitOnLoadDocker() throws ExecutionException, InterruptedException {
    runStepsRunners();
    return Preconditions.checkNotNull(steps.loadDockerStep).getFuture().get();
  }

  public BuildResult waitOnWriteTarFile() throws ExecutionException, InterruptedException {
    runStepsRunners();
    return Preconditions.checkNotNull(steps.writeTarFileStep).getFuture().get();
  }

  private void runStepsRunners() {
    for (Runnable stepsRunner : stepsRunnables) {
      stepsRunner.run();
    }
  }
}
