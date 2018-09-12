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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nullable;

/** Immutable configuration options for the container. */
public class ContainerConfiguration {

  /** The default creation time of the container (constant to ensure reproducibility by default). */
  public static final Instant DEFAULT_CREATION_TIME = Instant.EPOCH;

  /** Builder for instantiating a {@link ContainerConfiguration}. */
  public static class Builder {

    private Instant creationTime = DEFAULT_CREATION_TIME;
    @Nullable private ImmutableList<String> entrypoint;
    @Nullable private ImmutableList<String> programArguments;
    @Nullable private ImmutableMap<String, String> environmentMap;
    @Nullable private ImmutableList<Port> exposedPorts;
    @Nullable private ImmutableMap<String, String> labels;

    /**
     * Sets the image creation time.
     *
     * @param creationTime the creation time
     * @return this
     */
    public Builder setCreationTime(Instant creationTime) {
      this.creationTime = creationTime;
      return this;
    }

    /**
     * Sets the commandline arguments for main.
     *
     * @param programArguments the list of arguments
     * @return this
     */
    public Builder setProgramArguments(@Nullable List<String> programArguments) {
      if (programArguments == null) {
        this.programArguments = null;
      } else {
        Preconditions.checkArgument(!programArguments.contains(null));
        this.programArguments = ImmutableList.copyOf(programArguments);
      }
      return this;
    }

    /**
     * Sets the container's environment variables, mapping variable name to value.
     *
     * @param environmentMap the map
     * @return this
     */
    public Builder setEnvironment(@Nullable Map<String, String> environmentMap) {
      if (environmentMap == null) {
        this.environmentMap = null;
      } else {
        Preconditions.checkArgument(!Iterables.any(environmentMap.keySet(), Objects::isNull));
        Preconditions.checkArgument(!Iterables.any(environmentMap.values(), Objects::isNull));
        this.environmentMap = ImmutableMap.copyOf(environmentMap);
      }
      return this;
    }

    /**
     * Sets the container's exposed ports.
     *
     * @param exposedPorts the list of ports
     * @return this
     */
    public Builder setExposedPorts(@Nullable List<Port> exposedPorts) {
      if (exposedPorts == null) {
        this.exposedPorts = null;
      } else {
        Preconditions.checkArgument(!exposedPorts.contains(null));
        this.exposedPorts = ImmutableList.copyOf(exposedPorts);
      }
      return this;
    }

    /**
     * Sets the container's labels.
     *
     * @param labels the map of labels
     * @return this
     */
    public Builder setLabels(@Nullable Map<String, String> labels) {
      if (labels == null) {
        this.labels = null;
      } else {
        Preconditions.checkArgument(!Iterables.any(labels.keySet(), Objects::isNull));
        Preconditions.checkArgument(!Iterables.any(labels.values(), Objects::isNull));
        this.labels = ImmutableMap.copyOf(labels);
      }
      return this;
    }

    /**
     * Sets the container entrypoint.
     *
     * @param entrypoint the tokenized command to run when the container starts
     * @return this
     */
    public Builder setEntrypoint(@Nullable List<String> entrypoint) {
      if (entrypoint == null) {
        this.entrypoint = null;
      } else {
        Preconditions.checkArgument(!entrypoint.contains(null));
        this.entrypoint = ImmutableList.copyOf(entrypoint);
      }
      return this;
    }

    /**
     * Builds the {@link ContainerConfiguration}.
     *
     * @return the corresponding {@link ContainerConfiguration}
     */
    public ContainerConfiguration build() {
      return new ContainerConfiguration(
          creationTime, entrypoint, programArguments, environmentMap, exposedPorts, labels);
    }

    private Builder() {}
  }

  /**
   * Constructs a builder for a {@link ContainerConfiguration}.
   *
   * @return the builder
   */
  public static Builder builder() {
    return new Builder();
  }

  private final Instant creationTime;
  @Nullable private final ImmutableList<String> entrypoint;
  @Nullable private final ImmutableList<String> programArguments;
  @Nullable private final ImmutableMap<String, String> environmentMap;
  @Nullable private final ImmutableList<Port> exposedPorts;
  @Nullable private final ImmutableMap<String, String> labels;

  private ContainerConfiguration(
      Instant creationTime,
      @Nullable ImmutableList<String> entrypoint,
      @Nullable ImmutableList<String> programArguments,
      @Nullable ImmutableMap<String, String> environmentMap,
      @Nullable ImmutableList<Port> exposedPorts,
      @Nullable ImmutableMap<String, String> labels) {
    this.creationTime = creationTime;
    this.entrypoint = entrypoint;
    this.programArguments = programArguments;
    this.environmentMap = environmentMap;
    this.exposedPorts = exposedPorts;
    this.labels = labels;
  }

  public Instant getCreationTime() {
    return creationTime;
  }

  @Nullable
  public ImmutableList<String> getEntrypoint() {
    return entrypoint;
  }

  @Nullable
  public ImmutableList<String> getProgramArguments() {
    return programArguments;
  }

  @Nullable
  public ImmutableMap<String, String> getEnvironmentMap() {
    return environmentMap;
  }

  @Nullable
  public ImmutableList<Port> getExposedPorts() {
    return exposedPorts;
  }

  @Nullable
  public ImmutableMap<String, String> getLabels() {
    return labels;
  }
}
