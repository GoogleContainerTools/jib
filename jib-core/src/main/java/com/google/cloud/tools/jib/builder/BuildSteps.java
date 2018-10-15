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

import com.google.cloud.tools.jib.builder.steps.StepsRunner;
import com.google.cloud.tools.jib.configuration.BuildConfiguration;
import com.google.cloud.tools.jib.docker.DockerClient;
import com.google.cloud.tools.jib.event.events.LogEvent;
import com.google.cloud.tools.jib.image.DescriptorDigest;
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

    /**
     * Runs a {@link StepsRunner}.
     *
     * @param stepsRunner the {@link StepsRunner} to run
     * @return the digest of the built image
     * @throws ExecutionException if an exception occurs during execution
     * @throws InterruptedException if the execution is interrupted
     */
    DescriptorDigest accept(StepsRunner stepsRunner)
        throws ExecutionException, InterruptedException;
  }

  /**
   * All the steps to build an image to a Docker registry.
   *
   * @param buildConfiguration the configuration parameters for the build
   * @return a new {@link BuildSteps} for building to a registry
   */
  public static BuildSteps forBuildToDockerRegistry(BuildConfiguration buildConfiguration) {
    return new BuildSteps(
        DESCRIPTION_FOR_DOCKER_REGISTRY,
        buildConfiguration,
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
   * @param dockerClient the {@link DockerClient} for running {@code docker} commands
   * @param buildConfiguration the configuration parameters for the build
   * @return a new {@link BuildSteps} for building to a Docker daemon
   */
  public static BuildSteps forBuildToDockerDaemon(
      DockerClient dockerClient, BuildConfiguration buildConfiguration) {
    return new BuildSteps(
        DESCRIPTION_FOR_DOCKER_DAEMON,
        buildConfiguration,
        stepsRunner ->
            stepsRunner
                .runPullBaseImageStep()
                .runPullAndCacheBaseImageLayersStep()
                .runBuildAndCacheApplicationLayerSteps()
                .runBuildImageStep()
                .runFinalizingBuildStep()
                .runLoadDockerStep(dockerClient)
                .waitOnLoadDockerStep());
  }

  /**
   * All the steps to build an image tarball.
   *
   * @param outputPath the path to output the tarball to
   * @param buildConfiguration the configuration parameters for the build
   * @return a new {@link BuildSteps} for building a tarball
   */
  public static BuildSteps forBuildToTar(Path outputPath, BuildConfiguration buildConfiguration) {
    return new BuildSteps(
        DESCRIPTION_FOR_TARBALL,
        buildConfiguration,
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
  private final StepsRunnerConsumer stepsRunnerConsumer;

  /**
   * @param description a description of what the steps do
   * @param buildConfiguration the configuration parameters for the build
   * @param stepsRunnerConsumer accepts a {@link StepsRunner} by running the necessary steps
   */
  private BuildSteps(
      String description,
      BuildConfiguration buildConfiguration,
      StepsRunnerConsumer stepsRunnerConsumer) {
    this.description = description;
    this.buildConfiguration = buildConfiguration;
    this.stepsRunnerConsumer = stepsRunnerConsumer;
  }

  public BuildConfiguration getBuildConfiguration() {
    return buildConfiguration;
  }

  /**
   * Executes the build.
   *
   * @return the built image digest
   * @throws InterruptedException if the execution is interrupted
   * @throws ExecutionException if an exception occurs during execution
   */
  public DescriptorDigest run() throws InterruptedException, ExecutionException {
    buildConfiguration.getEventDispatcher().dispatch(LogEvent.lifecycle(""));

    DescriptorDigest imageDigest;
    try (TimerEventDispatcher ignored =
        new TimerEventDispatcher(buildConfiguration.getEventDispatcher(), description)) {
      imageDigest = stepsRunnerConsumer.accept(new StepsRunner(buildConfiguration));
    }

    if (buildConfiguration.getContainerConfiguration() != null) {
      buildConfiguration.getEventDispatcher().dispatch(LogEvent.lifecycle(""));
      // TODO refactor code to also log ENTRYPOINT and CMD when inheriting them in this code,
      // instead of logging them elsewhere.
      if (buildConfiguration.getContainerConfiguration().getEntrypoint() != null) {
        buildConfiguration
            .getEventDispatcher()
            .dispatch(
                LogEvent.lifecycle(
                    "Container entrypoint set to "
                        + buildConfiguration.getContainerConfiguration().getEntrypoint()));
      }
    }

    return imageDigest;
  }
}
