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

import java.nio.file.Paths;

/** Build containers with Jib. */
public class Jib {

  public static final String REGISTRY_IMAGE_PREFIX = "registry://";
  public static final String DOCKER_DAEMON_IMAGE_PREFIX = "docker://";
  public static final String TAR_IMAGE_PREFIX = "tar://";

  /**
   * Starts building the container from a base image. The type of base image can be specified using
   * a prefix, e.g. {@code docker://gcr.io/project/image}. The available prefixes are described
   * below:
   *
   * <ul>
   *   <li>No prefix, or {@code registry://}: uses a registry base image
   *   <li>{@code docker://}: uses a base image found in the local Docker daemon
   *   <li>{@code tar://}: uses a tarball base image at the path following the prefix
   * </ul>
   *
   * @param baseImageReference the base image reference
   * @return a new {@link JibContainerBuilder} to continue building the container
   * @throws InvalidImageReferenceException if the {@code baseImageReference} is not a valid image
   *     reference
   */
  public static JibContainerBuilder from(String baseImageReference)
      throws InvalidImageReferenceException {
    if (baseImageReference.startsWith(DOCKER_DAEMON_IMAGE_PREFIX)) {
      return from(
          DockerDaemonImage.named(baseImageReference.replaceFirst(DOCKER_DAEMON_IMAGE_PREFIX, "")));
    }
    if (baseImageReference.startsWith(TAR_IMAGE_PREFIX)) {
      return from(TarImage.at(Paths.get(baseImageReference.replaceFirst(TAR_IMAGE_PREFIX, ""))));
    }
    return from(RegistryImage.named(baseImageReference.replaceFirst(REGISTRY_IMAGE_PREFIX, "")));
  }

  /**
   * Starts building the container from a base image. The base image should be publicly-available.
   * For a base image that requires credentials, use {@link #from(RegistryImage)}.
   *
   * @param baseImageReference the base image reference
   * @return a new {@link JibContainerBuilder} to continue building the container
   */
  public static JibContainerBuilder from(ImageReference baseImageReference) {
    return from(RegistryImage.named(baseImageReference));
  }

  /**
   * Starts building the container from a registry base image.
   *
   * @param registryImage the {@link RegistryImage} that defines base container registry and
   *     credentials
   * @return a new {@link JibContainerBuilder} to continue building the container
   */
  public static JibContainerBuilder from(RegistryImage registryImage) {
    return new JibContainerBuilder(registryImage);
  }

  /**
   * Starts building the container from a base image stored in the Docker cache. Requires a running
   * Docker daemon.
   *
   * @param dockerDaemonImage the {@link DockerDaemonImage} that defines the base image and Docker
   *     client
   * @return a new {@link JibContainerBuilder} to continue building the container
   */
  public static JibContainerBuilder from(DockerDaemonImage dockerDaemonImage) {
    return new JibContainerBuilder(dockerDaemonImage);
  }

  /**
   * Starts building the container from a tarball.
   *
   * @param tarImage the {@link TarImage} that defines the path to the base image
   * @return a new {@link JibContainerBuilder} to continue building the container
   */
  public static JibContainerBuilder from(TarImage tarImage) {
    return new JibContainerBuilder(tarImage);
  }

  /**
   * Starts building the container from an empty base image.
   *
   * @return a new {@link JibContainerBuilder} to continue building the container
   */
  public static JibContainerBuilder fromScratch() {
    return from(ImageReference.scratch());
  }

  /**
   * Starts building the container from a base image stored in the Docker cache. Requires a running
   * Docker daemon.
   *
   * @param dockerClient the {@link DockerClient} to connect
   * @param dockerDaemonImage the {@link DockerDaemonImage} that defines the base image and Docker
   *     client
   * @return a new {@link JibContainerBuilder} to continue building the container
   */
  public static JibContainerBuilder from(
      DockerClient dockerClient, DockerDaemonImage dockerDaemonImage) {
    return new JibContainerBuilder(dockerClient, dockerDaemonImage);
  }

  private Jib() {}
}
