/*
 * Copyright 2019 Google LLC.
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
import java.nio.file.Path;
import java.util.function.Function;

/** Creates a {@link StepsRunner} factory for building an image. */
public class StepsRunnerFactory {

  /**
   * Returns a {@link StepsRunner} factory for creating a new {@link StepsRunner} that will execute
   * all the steps to build an image to a Docker registry.
   *
   * @return a new {@link StepsRunner} factory for building to a registry
   */
  public static Function<BuildConfiguration, StepsRunner> forBuildToDockerRegistry() {
    return buildConfiguration ->
        StepsRunner.begin(buildConfiguration)
            .retrieveTargetRegistryCredentials()
            .authenticatePush()
            .pullBaseImage()
            .pullAndCacheBaseImageLayers()
            .pushBaseImageLayers()
            .buildAndCacheApplicationLayers()
            .buildImage()
            .pushContainerConfiguration()
            .pushApplicationLayers()
            .pushImage();
  }

  /**
   * Returns a {@link StepsRunner} factory for creating a new {@link StepsRunner} that will execute
   * all the steps to build to Docker daemon.
   *
   * @param dockerClient the {@link DockerClient} for running {@code docker} commands
   * @return a new {@link StepsRunner} factory for building to a Docker daemon
   */
  public static Function<BuildConfiguration, StepsRunner> forBuildToDockerDaemon(
      DockerClient dockerClient) {
    return buildConfiguration ->
        StepsRunner.begin(buildConfiguration)
            .pullBaseImage()
            .pullAndCacheBaseImageLayers()
            .buildAndCacheApplicationLayers()
            .buildImage()
            .loadDocker(dockerClient);
  }

  /**
   * Returns a {@link StepsRunner} factory for creating a new {@link StepsRunner} that will execute
   * all the steps to build an image tarball.
   *
   * @param outputPath the path to output the tarball to
   * @return a new {@link StepsRunner} factory for building a tarball
   */
  public static Function<BuildConfiguration, StepsRunner> forBuildToTar(Path outputPath) {
    return buildConfiguration ->
        StepsRunner.begin(buildConfiguration)
            .pullBaseImage()
            .pullAndCacheBaseImageLayers()
            .buildAndCacheApplicationLayers()
            .buildImage()
            .writeTarFile(outputPath);
  }
}
