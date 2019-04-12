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

package com.google.cloud.tools.jib.api;
// TODO: Move to com.google.cloud.tools.jib once that package is cleaned up.

import com.google.cloud.tools.jib.builder.BuildSteps;
import com.google.cloud.tools.jib.configuration.BuildConfiguration;
import com.google.cloud.tools.jib.configuration.ImageConfiguration;
import com.google.cloud.tools.jib.docker.DockerClient;
import com.google.cloud.tools.jib.image.ImageReference;
import com.google.common.collect.ImmutableMap;
import java.nio.file.Path;
import java.util.Map;
import javax.annotation.Nullable;

class DockerDaemonTargetImage implements DockerDaemonImage, TargetImage {

  private final ImageReference imageReference;
  @Nullable private Path dockerExecutable;
  @Nullable private Map<String, String> dockerEnvironment;

  DockerDaemonTargetImage(ImageReference imageReference) {
    this.imageReference = imageReference;
  }

  @Override
  public DockerDaemonTargetImage setDockerExecutable(Path dockerExecutable) {
    this.dockerExecutable = dockerExecutable;
    return this;
  }

  @Override
  public DockerDaemonTargetImage setDockerEnvironment(Map<String, String> dockerEnvironment) {
    this.dockerEnvironment = dockerEnvironment;
    return this;
  }

  @Override
  public ImageConfiguration toImageConfiguration() {
    return ImageConfiguration.builder(imageReference).build();
  }

  @Override
  public BuildSteps toBuildSteps(BuildConfiguration buildConfiguration) {
    DockerClient.Builder dockerClientBuilder = DockerClient.builder();
    if (dockerExecutable != null) {
      dockerClientBuilder.setDockerExecutable(dockerExecutable);
    }
    if (dockerEnvironment != null) {
      dockerClientBuilder.setDockerEnvironment(ImmutableMap.copyOf(dockerEnvironment));
    }
    return BuildSteps.forBuildToDockerDaemon(dockerClientBuilder.build(), buildConfiguration);
  }
}
