/*
 * Copyright 2017 Google LLC. All rights reserved.
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
import com.google.common.collect.ImmutableList;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/** Represents an image. */
public class Image<T extends Layer> {

  /** Builds the immutable {@link Image}. */
  public static class Builder<T extends Layer> {

    private final ImageLayers.Builder<T> imageLayersBuilder = ImageLayers.builder();
    private final ImmutableList.Builder<String> environmentBuilder = ImmutableList.builder();

    @Nullable private Instant created;
    private ImmutableList<String> entrypoint = ImmutableList.of();
    private ImmutableList<String> javaArguments = ImmutableList.of();
    private ImmutableList<Port> exposedPorts = ImmutableList.of();

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
     * Sets the environment with a map from environment variable names to values.
     *
     * @param environment the map of environment variables
     * @return this
     */
    public Builder<T> setEnvironment(Map<String, String> environment) {
      for (Map.Entry<String, String> environmentVariable : environment.entrySet()) {
        setEnvironmentVariable(environmentVariable.getKey(), environmentVariable.getValue());
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
    public Builder<T> setEnvironmentVariable(String name, String value) {
      environmentBuilder.add(name + "=" + value);
      return this;
    }

    /**
     * Adds an environment variable definition in the format {@code NAME=VALUE}.
     *
     * @param environmentVariableDefinition the definition to add
     * @return this
     */
    public Builder<T> addEnvironmentVariableDefinition(String environmentVariableDefinition) {
      environmentBuilder.add(environmentVariableDefinition);
      return this;
    }

    /**
     * Sets the entrypoint of the image.
     *
     * @param entrypoint the list of entrypoint tokens
     * @return this
     */
    public Builder<T> setEntrypoint(List<String> entrypoint) {
      this.entrypoint = ImmutableList.copyOf(entrypoint);
      return this;
    }

    /**
     * Sets the items in the "Cmd" field in the container configuration (i.e. the main args).
     *
     * @param javaArguments the list of main args to add
     * @return this
     */
    public Builder<T> setJavaArguments(List<String> javaArguments) {
      this.javaArguments = ImmutableList.copyOf(javaArguments);
      return this;
    }

    /**
     * Sets the items in the "ExposedPorts" field in the container configuration.
     *
     * @param exposedPorts the list of exposed ports to add
     * @return this
     */
    public Builder<T> setExposedPorts(ImmutableList<Port> exposedPorts) {
      this.exposedPorts = exposedPorts;
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

    public Image<T> build() {
      return new Image<>(
          created,
          imageLayersBuilder.build(),
          environmentBuilder.build(),
          ImmutableList.copyOf(entrypoint),
          ImmutableList.copyOf(javaArguments),
          exposedPorts);
    }
  }

  public static <T extends Layer> Builder<T> builder() {
    return new Builder<>();
  }

  /** The image creation time. */
  @Nullable private final Instant created;

  /** The layers of the image, in the order in which they are applied. */
  private final ImageLayers<T> layers;

  /** Environment variable definitions for running the image, in the format {@code NAME=VALUE}. */
  private final ImmutableList<String> environmentBuilder;

  /** Initial command to run when running the image. */
  private final ImmutableList<String> entrypoint;

  /** Arguments to pass into main when running the image. */
  private final ImmutableList<String> javaArguments;

  /** Ports that the container listens on. */
  private final ImmutableList<Port> exposedPorts;

  private Image(
      @Nullable Instant created,
      ImageLayers<T> layers,
      ImmutableList<String> environment,
      ImmutableList<String> entrypoint,
      ImmutableList<String> javaArguments,
      ImmutableList<Port> exposedPorts) {
    this.created = created;
    this.layers = layers;
    this.environmentBuilder = environment;
    this.entrypoint = entrypoint;
    this.javaArguments = javaArguments;
    this.exposedPorts = exposedPorts;
  }

  @Nullable
  public Instant getCreated() {
    return created;
  }

  public ImmutableList<String> getEnvironment() {
    return environmentBuilder;
  }

  public ImmutableList<String> getEntrypoint() {
    return entrypoint;
  }

  public ImmutableList<String> getJavaArguments() {
    return javaArguments;
  }

  public ImmutableList<Port> getExposedPorts() {
    return exposedPorts;
  }

  public ImmutableList<T> getLayers() {
    return layers.getLayers();
  }
}
