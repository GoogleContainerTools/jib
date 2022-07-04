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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.cloud.tools.jib.image.ImageTarball;
import com.google.cloud.tools.jib.json.JsonTemplate;
import java.io.IOException;
import java.nio.file.Path;
import java.security.DigestException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
  DockerImageDetails inspect(ImageReference imageReference)
      throws IOException, InterruptedException;

  /**
   * Contains the size, image ID, and diff IDs of an image inspected with {@code docker inspect}.
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  class DockerImageDetails implements JsonTemplate {

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class RootFsTemplate implements JsonTemplate {
      @JsonProperty("Layers")
      private final List<String> layers = Collections.emptyList();
    }

    @JsonProperty("Size")
    private long size;

    @JsonProperty("Id")
    private String imageId = "";

    @JsonProperty("RootFS")
    private final RootFsTemplate rootFs = new RootFsTemplate();

    public long getSize() {
      return size;
    }

    public DescriptorDigest getImageId() throws DigestException {
      return DescriptorDigest.fromDigest(imageId);
    }

    /**
     * Return a list of diff ids of the layers in the image.
     *
     * @return a list of diff ids
     * @throws DigestException if a digest is invalid
     */
    public List<DescriptorDigest> getDiffIds() throws DigestException {
      List<DescriptorDigest> processedDiffIds = new ArrayList<>(rootFs.layers.size());
      for (String diffId : rootFs.layers) {
        processedDiffIds.add(DescriptorDigest.fromDigest(diffId.trim()));
      }
      return processedDiffIds;
    }
  }
}
