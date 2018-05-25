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

import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Map;

/** Represents an image. */
public class Image<T extends Layer> {

  /** Builds the immutable {@link Image}. */
  public static class Builder<T extends Layer> {

    private final ImageLayers.Builder<T> imageLayersBuilder = ImageLayers.builder();
    private final ImmutableList.Builder<String> environmentBuilder = ImmutableList.builder();

    private ImmutableList<String> entrypoint = ImmutableList.of();

    /** Sets the environment with a map from environment variable names to values. */
    public Builder<T> setEnvironment(Map<String, String> environment) {
      for (Map.Entry<String, String> environmentVariable : environment.entrySet()) {
        setEnvironmentVariable(environmentVariable.getKey(), environmentVariable.getValue());
      }
      return this;
    }

    public Builder<T> setEnvironmentVariable(String name, String value) {
      environmentBuilder.add(name + "=" + value);
      return this;
    }

    /** Adds an environment variable definition in the format {@code NAME=VALUE}. */
    public Builder<T> addEnvironmentVariableDefinition(String environmentVariableDefinition) {
      environmentBuilder.add(environmentVariableDefinition);
      return this;
    }

    public Builder<T> setEntrypoint(List<String> entrypoint) {
      this.entrypoint = ImmutableList.copyOf(entrypoint);
      return this;
    }

    public Builder<T> addLayer(T layer) throws LayerPropertyNotFoundException {
      imageLayersBuilder.add(layer);
      return this;
    }

    public Image<T> build() {
      return new Image<>(
          imageLayersBuilder.build(), environmentBuilder.build(), ImmutableList.copyOf(entrypoint));
    }
  }

  public static <T extends Layer> Builder<T> builder() {
    return new Builder<>();
  }

  /** The layers of the image, in the order in which they are applied. */
  private final ImageLayers<T> layers;

  /** Environment variable definitions for running the image, in the format {@code NAME=VALUE}. */
  private final ImmutableList<String> environmentBuilder;

  /** Initial command to run when running the image. */
  private final ImmutableList<String> entrypoint;

  private Image(
      ImageLayers<T> layers, ImmutableList<String> environment, ImmutableList<String> entrypoint) {
    this.layers = layers;
    this.environmentBuilder = environment;
    this.entrypoint = entrypoint;
  }

  public ImmutableList<String> getEnvironment() {
    return environmentBuilder;
  }

  public ImmutableList<String> getEntrypoint() {
    return entrypoint;
  }

  public ImmutableList<T> getLayers() {
    return layers.getLayers();
  }
}
