/*
 * Copyright 2022 Google LLC.
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

import com.google.cloud.tools.jib.image.ImageTarball;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Consumer;

public interface DockerClient {

  /**
   * Validate if the DockerClient is supported.
   *
   * @param parameters to be used by the docker client
   * @return true if conditions are met
   */
  boolean supported(Map<String, String> parameters);

  /**
   * Loads an image tarball into the Docker daemon.
   *
   * @see <a
   *     href="https://docs.docker.com/engine/reference/commandline/load/">https://docs.docker.com/engine/reference/commandline/load</a>
   * @param imageTarball the built container tarball
   * @param writtenByteCountListener callback to call when bytes are loaded
   * @return stdout from {@code docker}
   * @throws InterruptedException if the 'docker load' process is interrupted
   * @throws IOException if streaming the blob to 'docker load' fails
   */
  String load(ImageTarball imageTarball, Consumer<Long> writtenByteCountListener)
      throws InterruptedException, IOException;

  /**
   * Saves an image tarball from the Docker daemon.
   *
   * @see <a
   *     href="https://docs.docker.com/engine/reference/commandline/save/">https://docs.docker.com/engine/reference/commandline/save</a>
   * @param imageReference the image to save
   * @param outputPath the destination path to save the output tarball
   * @param writtenByteCountListener callback to call when bytes are saved
   * @throws InterruptedException if the 'docker save' process is interrupted
   * @throws IOException if creating the tarball fails
   */
  void save(ImageReference imageReference, Path outputPath, Consumer<Long> writtenByteCountListener)
      throws InterruptedException, IOException;

  /**
   * Gets the size, image ID, and diff IDs of an image in the Docker daemon.
   *
   * @param imageReference the image to inspect
   * @return the size, image ID, and diff IDs of the image
   * @throws IOException if an I/O exception occurs or {@code docker inspect} failed
   * @throws InterruptedException if the {@code docker inspect} process was interrupted
   */
  ImageDetails inspect(ImageReference imageReference) throws IOException, InterruptedException;

  /**
   * Gets the os and architecture of the local docker installation.
   *
   * @return the os type and architecture of the image
   * @throws IOException if an I/O exception occurs or {@code docker info} failed
   * @throws InterruptedException if the {@code docker info} process was interrupted
   */
  default DockerInfoDetails info() throws IOException, InterruptedException {
    return new DockerInfoDetails();
  }
}
