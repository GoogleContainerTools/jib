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

import com.google.cloud.tools.jib.docker.CliDockerClient;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;

/** Builds to the Docker daemon. */
public class DockerDaemonImage {

  /**
   * Instantiate with the image reference to tag the built image with. This is the name that shows
   * up on the Docker daemon.
   *
   * @param imageReference the image reference
   * @return a new {@link DockerDaemonImage}
   */
  public static DockerDaemonImage named(ImageReference imageReference) {
    return new DockerDaemonImage(imageReference);
  }

  /**
   * Instantiate with the image reference to tag the built image with. This is the name that shows
   * up on the Docker daemon.
   *
   * @param imageReference the image reference
   * @return a new {@link DockerDaemonImage}
   * @throws InvalidImageReferenceException if {@code imageReference} is not a valid image reference
   */
  public static DockerDaemonImage named(String imageReference)
      throws InvalidImageReferenceException {
    return named(ImageReference.parse(imageReference));
  }

  private final ImageReference imageReference;
  private Path dockerExecutable = CliDockerClient.getExistingDefaultDocker();
  private Map<String, String> dockerEnvironment = Collections.emptyMap();

  /** Instantiate with {@link #named}. */
  private DockerDaemonImage(ImageReference imageReference) {
    this.imageReference = imageReference;
  }

  /**
   * Sets the path to the {@code docker} CLI. This is {@code docker} by default.
   *
   * @param dockerExecutable the path to the {@code docker} CLI
   * @return this
   */
  public DockerDaemonImage setDockerExecutable(Path dockerExecutable) {
    this.dockerExecutable = dockerExecutable;
    return this;
  }

  /**
   * Sets the additional environment variables to use when running {@link #dockerExecutable docker}.
   *
   * @param dockerEnvironment additional environment variables
   * @return this
   */
  public DockerDaemonImage setDockerEnvironment(Map<String, String> dockerEnvironment) {
    this.dockerEnvironment = dockerEnvironment;
    return this;
  }

  ImageReference getImageReference() {
    return imageReference;
  }

  Path getDockerExecutable() {
    return dockerExecutable;
  }

  Map<String, String> getDockerEnvironment() {
    return dockerEnvironment;
  }
}
