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

// TODO: Move to com.google.cloud.tools.jib
package com.google.cloud.tools.jib.api;

import com.google.cloud.tools.jib.configuration.LayerConfiguration;
import com.google.cloud.tools.jib.configuration.Port;
import com.google.cloud.tools.jib.image.ImageReference;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/**
 * Builds a container with Jib.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * Jib.from(baseImage)
 *    .addFiles(sourceFiles, extractionPath)
 *    .setEntrypoint(entrypoint)
 *    .setProgramArguments(arguments)
 *    .addEnvironmentVariable(key, value)
 *    .addExposedPort(
 *    .addLabel(key, value)
 *    .containerize(...);
 * }</pre>
 */
public class JibContainerBuilder {

  private final ImageReference baseImageReference;

  private List<LayerConfiguration> layerConfigurations = new ArrayList<>();
  private Map<String, String> environment = new HashMap<>();
  private List<Port> ports = new ArrayList<>();
  private Map<String, String> labels = new HashMap<>();
  @Nullable private ImmutableList<String> entrypoint;
  @Nullable private ImmutableList<String> programArguments;

  /** Instantiate with {@link Jib#from}. */
  JibContainerBuilder(ImageReference baseImageReference) {
    this.baseImageReference = baseImageReference;
  }

  /**
   * Adds a new layer to the container
   *
   * @param files adsf
   * @param pathInContainer adsf
   * @return asdf
   */
  public JibContainerBuilder addFiles(List<Path> files, String pathInContainer) {
    addLayer(LayerConfiguration.builder().addEntry(files, pathInContainer).build());
    return this;
  }

  public JibContainerBuilder setLayers(List<LayerConfiguration> layerConfigurations) {
    this.layerConfigurations = new ArrayList<>(layerConfigurations);
    return this;
  }

  public JibContainerBuilder addLayer(LayerConfiguration layerConfiguration) {
    layerConfigurations.add(layerConfiguration);
    return this;
  }

  public JibContainerBuilder setEntrypoint(List<String> entrypointTokens) {
    entrypoint = ImmutableList.copyOf(entrypointTokens);
    return this;
  }

  public JibContainerBuilder setEntrypoint(String... entrypointTokens) {
    setEntrypoint(Arrays.asList(entrypointTokens));
    return this;
  }

  public JibContainerBuilder setProgramArguments(List<String> programArguments) {
    this.programArguments = ImmutableList.copyOf(programArguments);
    return this;
  }

  public JibContainerBuilder setProgramArguments(String... programArguments) {
    setProgramArguments(Arrays.asList(programArguments));
    return this;
  }

  public JibContainerBuilder setEnvironment(Map<String, String> environmentMap) {
    environment = new HashMap<>(environmentMap);
    return this;
  }

  public JibContainerBuilder setEnvironmentVariable(String name, String value) {
    environment.put(name, value);
    return this;
  }

  public JibContainerBuilder setExposedPorts(List<Integer> ports) {
    this.ports = ports.stream().map(Port::tcp).collect(Collectors.toCollection(ArrayList::new));
    return this;
  }

  public JibContainerBuilder setExposedPorts(int... ports) {
    setExposedPorts(Arrays.stream(ports).boxed().collect(Collectors.toCollection(ArrayList::new)));
    return this;
  }

  public JibContainerBuilder addExposedPort(int port) {
    ports.add(Port.tcp(port));
    return this;
  }

  public JibContainerBuilder setLabels(Map<String, String> labelMap) {
    labels = new HashMap<>(labelMap);
    return this;
  }

  public JibContainerBuilder setLabel(String key, String value) {
    labels.put(key, value);
    return this;
  }

  // TODO: Add containerize(...).
}
