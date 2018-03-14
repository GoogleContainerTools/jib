/*
 * Copyright 2017 Google Inc.
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/** Represents an image. */
public class Image {

  /** The layers of the image, in the order in which they are applied. */
  private ImageLayers<Layer> layers = new ImageLayers<>();

  /** Environment variable definitions for running the image, in the format {@code NAME=VALUE}. */
  private final List<String> environment = new ArrayList<>();

  /** Initial command to run when running the image. */
  @Nullable private List<String> entrypoint;

  public List<String> getEnvironment() {
    return Collections.unmodifiableList(environment);
  }

  /** Sets the environment with a map from environment variable names to values. */
  public Image setEnvironment(Map<String, String> environment) {
    for (Map.Entry<String, String> environmentVariable : environment.entrySet()) {
      setEnvironmentVariable(environmentVariable.getKey(), environmentVariable.getValue());
    }
    return this;
  }

  public Image setEnvironmentVariable(String name, String value) {
    environment.add(name + "=" + value);
    return this;
  }

  /** Adds an environment variable definition in the format {@code NAME=VALUE}. */
  public Image addEnvironmentVariableDefinition(String environmentVariableDefinition) {
    environment.add(environmentVariableDefinition);
    return this;
  }

  public List<String> getEntrypoint() {
    return Collections.unmodifiableList(entrypoint);
  }

  public Image setEntrypoint(List<String> entrypoint) {
    this.entrypoint = entrypoint;
    return this;
  }

  public List<Layer> getLayers() {
    return layers.getLayers();
  }

  public Image addLayer(Layer layer) throws LayerPropertyNotFoundException {
    layers.add(layer);
    return this;
  }

  public <T extends Layer> Image addLayers(ImageLayers<T> layers)
      throws LayerPropertyNotFoundException {
    this.layers.addAll(layers);
    return this;
  }
}
