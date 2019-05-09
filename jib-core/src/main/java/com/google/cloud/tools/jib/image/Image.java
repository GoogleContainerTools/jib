/*
 * Copyright 2017 Google LLC.
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

package com.google.cloud.tools.jib.image;

import com.google.cloud.tools.jib.api.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.Port;
import com.google.cloud.tools.jib.configuration.DockerHealthCheck;
import com.google.cloud.tools.jib.image.json.HistoryEntry;
import com.google.cloud.tools.jib.image.json.ManifestTemplate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

/** Represents an image. */
public class Image {

  /** Builds the immutable {@link Image}. */
  public static class Builder {

    private final Class<? extends ManifestTemplate> imageFormat;
    private final ImageLayers.Builder imageLayersBuilder = ImageLayers.builder();
    private final ImmutableList.Builder<HistoryEntry> historyBuilder = ImmutableList.builder();

    // Don't use ImmutableMap.Builder because it does not allow for replacing existing keys with new
    // values.
    private final Map<String, String> environmentBuilder = new HashMap<>();
    private final Map<String, String> labelsBuilder = new HashMap<>();
    private final Set<Port> exposedPortsBuilder = new HashSet<>();
    private final Set<AbsoluteUnixPath> volumesBuilder = new HashSet<>();

    @Nullable private Instant created;
    private String architecture = "amd64";
    private String os = "linux";
    @Nullable private ImmutableList<String> entrypoint;
    @Nullable private ImmutableList<String> programArguments;
    @Nullable private DockerHealthCheck healthCheck;
    @Nullable private String workingDirectory;
    @Nullable private String user;

    private Builder(Class<? extends ManifestTemplate> imageFormat) {
      this.imageFormat = imageFormat;
    }

    /**
     * Sets the image creation time.
     *
     * @param created the creation time
     * @return this
     */
    public Builder setCreated(Instant created) {
      this.created = created;
      return this;
    }

    /**
     * Sets the image architecture.
     *
     * @param architecture the architecture
     * @return this
     */
    public Builder setArchitecture(String architecture) {
      this.architecture = architecture;
      return this;
    }

    /**
     * Sets the image operating system.
     *
     * @param os the operating system
     * @return this
     */
    public Builder setOs(String os) {
      this.os = os;
      return this;
    }

    /**
     * Adds a map of environment variables to the current map.
     *
     * @param environment the map of environment variables
     * @return this
     */
    public Builder addEnvironment(@Nullable Map<String, String> environment) {
      if (environment != null) {
        this.environmentBuilder.putAll(environment);
      }
      return this;
    }

    /**
     * Adds an environment variable with a given name and value.
     *
     * @param name the name of the variable
     * @param value the value to set it to
     * @return this
     */
    public Builder addEnvironmentVariable(String name, String value) {
      environmentBuilder.put(name, value);
      return this;
    }

    /**
     * Sets the entrypoint of the image.
     *
     * @param entrypoint the list of entrypoint tokens
     * @return this
     */
    public Builder setEntrypoint(@Nullable List<String> entrypoint) {
      this.entrypoint = (entrypoint == null) ? null : ImmutableList.copyOf(entrypoint);
      return this;
    }

    /**
     * Sets the user/group to run the container as.
     *
     * @param user the username/UID and optionally the groupname/GID
     * @return this
     */
    public Builder setUser(@Nullable String user) {
      this.user = user;
      return this;
    }

    /**
     * Sets the items in the "Cmd" field in the container configuration.
     *
     * @param programArguments the list of arguments to append to the image entrypoint
     * @return this
     */
    public Builder setProgramArguments(@Nullable List<String> programArguments) {
      this.programArguments =
          (programArguments == null) ? null : ImmutableList.copyOf(programArguments);
      return this;
    }

    /**
     * Sets the container's healthcheck configuration.
     *
     * @param healthCheck the healthcheck configuration
     * @return this
     */
    public Builder setHealthCheck(@Nullable DockerHealthCheck healthCheck) {
      this.healthCheck = healthCheck;
      return this;
    }

    /**
     * Adds items to the "ExposedPorts" field in the container configuration.
     *
     * @param exposedPorts the exposed ports to add
     * @return this
     */
    public Builder addExposedPorts(@Nullable Set<Port> exposedPorts) {
      if (exposedPorts != null) {
        exposedPortsBuilder.addAll(exposedPorts);
      }
      return this;
    }

    /**
     * Adds items to the "Volumes" field in the container configuration.
     *
     * @param volumes the directories to create volumes
     * @return this
     */
    public Builder addVolumes(@Nullable Set<AbsoluteUnixPath> volumes) {
      if (volumes != null) {
        volumesBuilder.addAll(ImmutableSet.copyOf(volumes));
      }
      return this;
    }

    /**
     * Adds items to the "Labels" field in the container configuration.
     *
     * @param labels the map of labels to add
     * @return this
     */
    public Builder addLabels(@Nullable Map<String, String> labels) {
      if (labels != null) {
        labelsBuilder.putAll(labels);
      }
      return this;
    }

    /**
     * Adds an item to the "Labels" field in the container configuration.
     *
     * @param name the name of the label
     * @param value the value of the label
     * @return this
     */
    public Builder addLabel(String name, String value) {
      labelsBuilder.put(name, value);
      return this;
    }

    /**
     * Sets the item in the "WorkingDir" field in the container configuration.
     *
     * @param workingDirectory the working directory
     * @return this
     */
    public Builder setWorkingDirectory(@Nullable String workingDirectory) {
      this.workingDirectory = workingDirectory;
      return this;
    }

    /**
     * Adds a layer to the image.
     *
     * @param layer the layer to add
     * @return this
     * @throws LayerPropertyNotFoundException if adding the layer fails
     */
    public Builder addLayer(Layer layer) throws LayerPropertyNotFoundException {
      imageLayersBuilder.add(layer);
      return this;
    }

    /**
     * Adds a history element to the image.
     *
     * @param history the history object to add
     * @return this
     */
    public Builder addHistory(HistoryEntry history) {
      historyBuilder.add(history);
      return this;
    }

    public Image build() {
      return new Image(
          imageFormat,
          created,
          architecture,
          os,
          imageLayersBuilder.build(),
          historyBuilder.build(),
          ImmutableMap.copyOf(environmentBuilder),
          entrypoint,
          programArguments,
          healthCheck,
          ImmutableSet.copyOf(exposedPortsBuilder),
          ImmutableSet.copyOf(volumesBuilder),
          ImmutableMap.copyOf(labelsBuilder),
          workingDirectory,
          user);
    }
  }

  public static Builder builder(Class<? extends ManifestTemplate> imageFormat) {
    return new Builder(imageFormat);
  }

  /** The image format. */
  private final Class<? extends ManifestTemplate> imageFormat;

  /** The image creation time. */
  @Nullable private final Instant created;

  /** The image architecture. */
  private final String architecture;

  /** The image operating system. */
  private final String os;

  /** The layers of the image, in the order in which they are applied. */
  private final ImageLayers layers;

  /** The commands used to build each layer of the image */
  private final ImmutableList<HistoryEntry> history;

  /** Environment variable definitions for running the image, in the format {@code NAME=VALUE}. */
  @Nullable private final ImmutableMap<String, String> environment;

  /** Initial command to run when running the image. */
  @Nullable private final ImmutableList<String> entrypoint;

  /** Arguments to append to the image entrypoint when running the image. */
  @Nullable private final ImmutableList<String> programArguments;

  /** Healthcheck configuration. */
  @Nullable private final DockerHealthCheck healthCheck;

  /** Ports that the container listens on. */
  @Nullable private final ImmutableSet<Port> exposedPorts;

  /** Directories to mount as volumes. */
  @Nullable private final ImmutableSet<AbsoluteUnixPath> volumes;

  /** Labels on the container configuration */
  @Nullable private final ImmutableMap<String, String> labels;

  /** Working directory on the container configuration */
  @Nullable private final String workingDirectory;

  /** User on the container configuration */
  @Nullable private final String user;

  private Image(
      Class<? extends ManifestTemplate> imageFormat,
      @Nullable Instant created,
      String architecture,
      String os,
      ImageLayers layers,
      ImmutableList<HistoryEntry> history,
      @Nullable ImmutableMap<String, String> environment,
      @Nullable ImmutableList<String> entrypoint,
      @Nullable ImmutableList<String> programArguments,
      @Nullable DockerHealthCheck healthCheck,
      @Nullable ImmutableSet<Port> exposedPorts,
      @Nullable ImmutableSet<AbsoluteUnixPath> volumes,
      @Nullable ImmutableMap<String, String> labels,
      @Nullable String workingDirectory,
      @Nullable String user) {
    this.imageFormat = imageFormat;
    this.created = created;
    this.architecture = architecture;
    this.os = os;
    this.layers = layers;
    this.history = history;
    this.environment = environment;
    this.entrypoint = entrypoint;
    this.programArguments = programArguments;
    this.healthCheck = healthCheck;
    this.exposedPorts = exposedPorts;
    this.volumes = volumes;
    this.labels = labels;
    this.workingDirectory = workingDirectory;
    this.user = user;
  }

  public Class<? extends ManifestTemplate> getImageFormat() {
    return this.imageFormat;
  }

  @Nullable
  public Instant getCreated() {
    return created;
  }

  public String getArchitecture() {
    return architecture;
  }

  public String getOs() {
    return os;
  }

  @Nullable
  public ImmutableMap<String, String> getEnvironment() {
    return environment;
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
  public DockerHealthCheck getHealthCheck() {
    return healthCheck;
  }

  @Nullable
  public ImmutableSet<Port> getExposedPorts() {
    return exposedPorts;
  }

  @Nullable
  public ImmutableSet<AbsoluteUnixPath> getVolumes() {
    return volumes;
  }

  @Nullable
  public ImmutableMap<String, String> getLabels() {
    return labels;
  }

  @Nullable
  public String getWorkingDirectory() {
    return workingDirectory;
  }

  @Nullable
  public String getUser() {
    return user;
  }

  public ImmutableList<Layer> getLayers() {
    return layers.getLayers();
  }

  public ImmutableList<HistoryEntry> getHistory() {
    return history;
  }
}
