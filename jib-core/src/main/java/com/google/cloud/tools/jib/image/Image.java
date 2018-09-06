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

import com.google.cloud.tools.jib.configuration.Port;
import com.google.cloud.tools.jib.image.json.HistoryEntry;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/** Represents an image. */
public class Image<T extends Layer> {

  /** Builds the immutable {@link Image}. */
  public static class Builder<T extends Layer> {

    private final ImageLayers.Builder<T> imageLayersBuilder = ImageLayers.builder();
    private final ImmutableList.Builder<HistoryEntry> historyBuilder = ImmutableList.builder();
    private final ImmutableMap.Builder<String, String> environmentBuilder = ImmutableMap.builder();
    private final ImmutableMap.Builder<String, String> labelsBuilder = ImmutableMap.builder();

    @Nullable private Instant created;
    @Nullable private ImmutableList<String> entrypoint;
    @Nullable private ImmutableList<String> javaArguments;
    @Nullable private ImmutableList<Port> exposedPorts;
    @Nullable private String workingDirectory;

    /**
     * Sets the image creation time.
     *
     * @param created the creation time
     * @return this
     */
    public Builder<T> setCreated(Instant created) {
      this.created = created;
      return this;
    }

    /**
     * Adds a map of environment variables to the current map.
     *
     * @param environment the map of environment variables
     * @return this
     */
    public Builder<T> addEnvironment(@Nullable Map<String, String> environment) {
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
    public Builder<T> addEnvironmentVariable(String name, String value) {
      environmentBuilder.put(name, value);
      return this;
    }

    /**
     * Sets the entrypoint of the image.
     *
     * @param entrypoint the list of entrypoint tokens
     * @return this
     */
    public Builder<T> setEntrypoint(@Nullable List<String> entrypoint) {
      this.entrypoint = (entrypoint == null) ? null : ImmutableList.copyOf(entrypoint);
      return this;
    }

    /**
     * Sets the items in the "Cmd" field in the container configuration (i.e. the main args).
     *
     * @param javaArguments the list of main args to add
     * @return this
     */
    public Builder<T> setJavaArguments(@Nullable List<String> javaArguments) {
      this.javaArguments = (javaArguments == null) ? null : ImmutableList.copyOf(javaArguments);
      return this;
    }

    /**
     * Sets the items in the "ExposedPorts" field in the container configuration.
     *
     * @param exposedPorts the list of exposed ports to add
     * @return this
     */
    public Builder<T> setExposedPorts(@Nullable List<Port> exposedPorts) {
      this.exposedPorts = (exposedPorts == null) ? null : ImmutableList.copyOf(exposedPorts);
      return this;
    }

    /**
     * Adds items to the "Labels" field in the container configuration.
     *
     * @param labels the map of labels to add
     * @return this
     */
    public Builder<T> addLabels(@Nullable Map<String, String> labels) {
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
    public Builder<T> addLabel(String name, String value) {
      labelsBuilder.put(name, value);
      return this;
    }

    /**
     * Sets the item in the "WorkingDir" field in the container configuration.
     *
     * @param workingDirectory the working directory
     * @return this
     */
    public Builder<T> setWorkingDirectory(@Nullable String workingDirectory) {
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
    public Builder<T> addLayer(T layer) throws LayerPropertyNotFoundException {
      imageLayersBuilder.add(layer);
      return this;
    }

    /**
     * Adds a history element to the image.
     *
     * @param history the history object to add
     * @return this
     */
    public Builder<T> addHistory(HistoryEntry history) {
      historyBuilder.add(history);
      return this;
    }

    public Image<T> build() {
      return new Image<>(
          created,
          imageLayersBuilder.build(),
          historyBuilder.build(),
          environmentBuilder.build(),
          entrypoint,
          javaArguments,
          exposedPorts,
          labelsBuilder.build(),
          workingDirectory);
    }
  }

  public static <T extends Layer> Builder<T> builder() {
    return new Builder<>();
  }

  /** The image creation time. */
  @Nullable private final Instant created;

  /** The layers of the image, in the order in which they are applied. */
  private final ImageLayers<T> layers;

  /** The commands used to build each layer of the image */
  private final ImmutableList<HistoryEntry> history;

  /** Environment variable definitions for running the image, in the format {@code NAME=VALUE}. */
  @Nullable private final ImmutableMap<String, String> environment;

  /** Initial command to run when running the image. */
  @Nullable private final ImmutableList<String> entrypoint;

  /** Arguments to pass into main when running the image. */
  @Nullable private final ImmutableList<String> javaArguments;

  /** Ports that the container listens on. */
  @Nullable private final ImmutableList<Port> exposedPorts;

  /** Labels on the container configuration */
  @Nullable private final ImmutableMap<String, String> labels;

  /** Working directory on the container configuration */
  @Nullable private final String workingDirectory;

  private Image(
      @Nullable Instant created,
      ImageLayers<T> layers,
      ImmutableList<HistoryEntry> history,
      @Nullable ImmutableMap<String, String> environment,
      @Nullable ImmutableList<String> entrypoint,
      @Nullable ImmutableList<String> javaArguments,
      @Nullable ImmutableList<Port> exposedPorts,
      @Nullable ImmutableMap<String, String> labels,
      @Nullable String workingDirectory) {
    this.created = created;
    this.layers = layers;
    this.history = history;
    this.environment = environment;
    this.entrypoint = entrypoint;
    this.javaArguments = javaArguments;
    this.exposedPorts = exposedPorts;
    this.labels = labels;
    this.workingDirectory = workingDirectory;
  }

  @Nullable
  public Instant getCreated() {
    return created;
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
  public ImmutableList<String> getJavaArguments() {
    return javaArguments;
  }

  @Nullable
  public ImmutableList<Port> getExposedPorts() {
    return exposedPorts;
  }

  @Nullable
  public ImmutableMap<String, String> getLabels() {
    return labels;
  }

  @Nullable
  public String getWorkingDirectory() {
    return workingDirectory;
  }

  public ImmutableList<T> getLayers() {
    return layers.getLayers();
  }

  public ImmutableList<HistoryEntry> getHistory() {
    return history;
  }
}
