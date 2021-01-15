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

package com.google.cloud.tools.jib.configuration;

import com.google.cloud.tools.jib.api.CredentialRetriever;
import com.google.cloud.tools.jib.api.ImageReference;
import com.google.cloud.tools.jib.docker.DockerClient;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nullable;

/** Immutable configuration options for a base or target image reference. */
public class ImageConfiguration {

  /** Builder for instantiating an {@link ImageConfiguration}. */
  public static class Builder {

    private ImageReference imageReference;
    private ImmutableList<CredentialRetriever> credentialRetrievers = ImmutableList.of();
    @Nullable private DockerClient dockerClient;
    @Nullable private Path tarPath;

    /**
     * Sets the providers for registry credentials. The order determines the priority in which the
     * retrieval methods are attempted.
     *
     * @param credentialRetrievers the list of {@link CredentialRetriever}s
     * @return this
     */
    public Builder setCredentialRetrievers(List<CredentialRetriever> credentialRetrievers) {
      Preconditions.checkArgument(
          credentialRetrievers.stream().allMatch(Objects::nonNull),
          "credential retriever list contains null elements");
      this.credentialRetrievers = ImmutableList.copyOf(credentialRetrievers);
      return this;
    }

    /**
     * Sets the Docker client to be used for Docker daemon base images.
     *
     * @param dockerClient the Docker client
     * @return this
     */
    public Builder setDockerClient(DockerClient dockerClient) {
      this.dockerClient = dockerClient;
      return this;
    }

    /**
     * Sets the path for tarball base images.
     *
     * @param tarPath the path
     * @return this
     */
    public Builder setTarPath(Path tarPath) {
      this.tarPath = tarPath;
      return this;
    }

    /**
     * Builds the {@link ImageConfiguration}.
     *
     * @return the corresponding {@link ImageConfiguration}
     */
    public ImageConfiguration build() {
      int numArguments = 0;
      if (!credentialRetrievers.isEmpty()) {
        numArguments++;
      }
      if (dockerClient != null) {
        numArguments++;
      }
      if (tarPath != null) {
        numArguments++;
      }
      Preconditions.checkArgument(numArguments <= 1);
      return new ImageConfiguration(imageReference, credentialRetrievers, dockerClient, tarPath);
    }

    private Builder(ImageReference imageReference) {
      this.imageReference = imageReference;
    }
  }

  /**
   * Constructs a builder for an {@link ImageConfiguration}.
   *
   * @param imageReference the image reference, which is a required field
   * @return the builder
   */
  public static Builder builder(ImageReference imageReference) {
    return new Builder(imageReference);
  }

  private final ImageReference image;
  private final ImmutableList<CredentialRetriever> credentialRetrievers;
  @Nullable private DockerClient dockerClient;
  @Nullable private Path tarPath;

  private ImageConfiguration(
      ImageReference image,
      ImmutableList<CredentialRetriever> credentialRetrievers,
      @Nullable DockerClient dockerClient,
      @Nullable Path tarPath) {
    this.image = image;
    this.credentialRetrievers = credentialRetrievers;
    this.dockerClient = dockerClient;
    this.tarPath = tarPath;
  }

  public ImageReference getImage() {
    return image;
  }

  public String getImageRegistry() {
    return image.getRegistry();
  }

  public String getImageRepository() {
    return image.getRepository();
  }

  public String getImageQualifier() {
    return image.getQualifier();
  }

  public ImmutableList<CredentialRetriever> getCredentialRetrievers() {
    return credentialRetrievers;
  }

  public Optional<DockerClient> getDockerClient() {
    return Optional.ofNullable(dockerClient);
  }

  public Optional<Path> getTarPath() {
    return Optional.ofNullable(tarPath);
  }
}
