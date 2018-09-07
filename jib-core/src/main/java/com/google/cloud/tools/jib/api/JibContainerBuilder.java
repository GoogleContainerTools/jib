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

package com.google.cloud.tools.jib.api;
// TODO: Move to com.google.cloud.tools.jib

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
import javax.annotation.Nullable;

/**
 * Builds a container with Jib.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * Jib.from(baseImage)
 *    .addFiles(sourceFiles, extractionPath)
 *    .setEntrypoint("myprogram", "--flag", "subcommand")
 *    .setProgramArguments("hello", "world")
 *    .addEnvironmentVariable("HOME", "/app")
 *    .addExposedPort(Port.tcp(8080))
 *    .addLabel("containerizer", "jib")
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
   * Adds a new layer to the container with {@code files} as the source files and {@code
   * pathInContainer} as the path to copy the source files to in the container file system.
   *
   * <p>Source files that are directories will be recursively copied. For example, if the source
   * files are:
   *
   * <ul>
   *   <li>{@code fileA}
   *   <li>{@code fileB}
   *   <li>{@code directory/}
   * </ul>
   *
   * and the destination to copy to is {@code /path/in/container}, then the new layer will have the
   * following entries for the container file system:
   *
   * <ul>
   *   <li>{@code /path/in/container/fileA}
   *   <li>{@code /path/in/container/fileB}
   *   <li>{@code /path/in/container/directory/}
   *   <li>{@code /path/in/container/directory/...} (all contents of {@code directory/})
   * </ul>
   *
   * @param files the source files to copy to a new layer in the container
   * @param pathInContainer the Unix-style path to copy the source files to in the container file
   *     system
   * @return this
   */
  public JibContainerBuilder addLayer(List<Path> files, String pathInContainer) {
    addLayer(LayerConfiguration.builder().addEntry(files, pathInContainer).build());
    return this;
  }

  /**
   * Sets the layers (defined by a list of {@link LayerConfiguration}s). This replaces any
   * previously-added layers.
   *
   * @param layerConfigurations the list of {@link LayerConfiguration}s
   * @return this
   */
  public JibContainerBuilder setLayers(List<LayerConfiguration> layerConfigurations) {
    this.layerConfigurations = new ArrayList<>(layerConfigurations);
    return this;
  }

  /**
   * Adds a layer (defined by a {@link LayerConfiguration}).
   *
   * @param layerConfiguration the {@link LayerConfiguration}
   * @return this
   */
  public JibContainerBuilder addLayer(LayerConfiguration layerConfiguration) {
    layerConfigurations.add(layerConfiguration);
    return this;
  }

  /**
   * Sets the container entrypoint. This is the beginning of the command that is run when the
   * container starts. {@link #setProgramArguments} sets additional tokens.
   *
   * @param entrypointTokens a list of tokens for the entrypoint command
   * @return this
   */
  public JibContainerBuilder setEntrypoint(List<String> entrypointTokens) {
    entrypoint = ImmutableList.copyOf(entrypointTokens);
    return this;
  }

  /**
   * Sets the container entrypoint.
   *
   * @param entrypointTokens tokens for the entrypoint command
   * @return this
   * @see #setEntrypoint(List) for more details
   */
  public JibContainerBuilder setEntrypoint(String... entrypointTokens) {
    setEntrypoint(Arrays.asList(entrypointTokens));
    return this;
  }

  /**
   * Sets the container entrypoint program arguments. These are additional tokens added to the end
   * of the entrypoint command.
   *
   * <p>For example, if the entrypoint was {@code myprogram --flag subcommand} and program arguments
   * were {@code hello world}, then the command that run when the container starts is {@code
   * myprogram --flag subcommand hello world}.
   *
   * @param programArguments a list of program argument tokens
   * @return this
   */
  public JibContainerBuilder setProgramArguments(List<String> programArguments) {
    this.programArguments = ImmutableList.copyOf(programArguments);
    return this;
  }

  /**
   * Sets the container entrypoint program arguments.
   *
   * @param programArguments program arguments tokens
   * @return this
   * @see #setProgramArguments(List) for more details
   */
  public JibContainerBuilder setProgramArguments(String... programArguments) {
    setProgramArguments(Arrays.asList(programArguments));
    return this;
  }

  /**
   * Sets the container environment. These environment variables are available to the program
   * launched by the container entrypoint command. This replaces any previously-set environment
   * variables.
   *
   * @param environmentMap a map of environment variable names to values
   * @return this
   */
  public JibContainerBuilder setEnvironment(Map<String, String> environmentMap) {
    environment = new HashMap<>(environmentMap);
    return this;
  }

  /**
   * Sets a variable in the container environment.
   *
   * @param name the environment variable name
   * @param value the environment variable value
   * @return this
   * @see #setEnvironment for more details
   */
  public JibContainerBuilder addEnvironmentVariable(String name, String value) {
    environment.put(name, value);
    return this;
  }

  /**
   * Sets the ports to expose from the container. Ports exposed will allow ingress traffic. This
   * replaces any previously-set exposed ports.
   *
   * <p>Use {@link Port#tcp} to expose a port for TCP traffic and {@link Port#udp} to expose a port
   * for UDP traffic.
   *
   * @param ports the list of ports to expose
   * @return this
   */
  public JibContainerBuilder setExposedPorts(List<Port> ports) {
    this.ports = new ArrayList<>(ports);
    return this;
  }

  /**
   * Sets the ports to expose from the container. This replaces any previously-set exposed ports.
   *
   * @param ports the ports to expose
   * @return this
   * @see #setExposedPorts(List) for more details
   */
  public JibContainerBuilder setExposedPorts(Port... ports) {
    setExposedPorts(Arrays.asList(ports));
    return this;
  }

  /**
   * Adds a port to expose from the container.
   *
   * @param port the port to expose
   * @return this
   * @see #setExposedPorts(List) for more details
   */
  public JibContainerBuilder addExposedPort(Port port) {
    ports.add(port);
    return this;
  }

  /**
   * Sets the labels for the container. This replaces any previously-set labels.
   *
   * @param labelMap a map of label keys to values
   * @return this
   */
  public JibContainerBuilder setLabels(Map<String, String> labelMap) {
    labels = new HashMap<>(labelMap);
    return this;
  }

  /**
   * Sets a label for the container.
   *
   * @param key the label key
   * @param value the label value
   * @return this
   */
  public JibContainerBuilder addLabel(String key, String value) {
    labels.put(key, value);
    return this;
  }

  // TODO: Add containerize(...).
}
