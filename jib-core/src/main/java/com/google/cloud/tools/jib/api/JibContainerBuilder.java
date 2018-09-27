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
// TODO: Move to com.google.cloud.tools.jib once that package is cleaned up.

import com.google.cloud.tools.jib.configuration.BuildConfiguration;
import com.google.cloud.tools.jib.configuration.CacheDirectoryCreationException;
import com.google.cloud.tools.jib.configuration.ContainerConfiguration;
import com.google.cloud.tools.jib.configuration.LayerConfiguration;
import com.google.cloud.tools.jib.configuration.Port;
import com.google.cloud.tools.jib.filesystem.AbsoluteUnixPath;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
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
 *    .addLayer(sourceFiles, extractionPath)
 *    .setEntrypoint("myprogram", "--flag", "subcommand")
 *    .setProgramArguments("hello", "world")
 *    .addEnvironmentVariable("HOME", "/app")
 *    .addExposedPort(Port.tcp(8080))
 *    .addLabel("containerizer", "jib")
 *    .containerize(...);
 * }</pre>
 */
// TODO: Add tests once containerize() is added.
public class JibContainerBuilder {

  private final SourceImage baseImage;

  private List<LayerConfiguration> layerConfigurations = new ArrayList<>();
  private Map<String, String> environment = new HashMap<>();
  private List<Port> ports = new ArrayList<>();
  private Map<String, String> labels = new HashMap<>();
  @Nullable private ImmutableList<String> entrypoint;
  @Nullable private ImmutableList<String> programArguments;

  /** Instantiate with {@link Jib#from}. */
  JibContainerBuilder(SourceImage baseImage) {
    this.baseImage = baseImage;
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
   * @param pathInContainer the path in the container file system corresponding to the {@code
   *     sourceFile}
   * @return this
   * @throws IOException if an exception occurred when recursively listing any directories
   */
  public JibContainerBuilder addLayer(List<Path> files, AbsoluteUnixPath pathInContainer)
      throws IOException {
    LayerConfiguration.Builder layerConfigurationBuilder = LayerConfiguration.builder();

    for (Path file : files) {
      layerConfigurationBuilder.addEntryRecursive(
          file, pathInContainer.resolve(file.getFileName()));
    }

    return addLayer(layerConfigurationBuilder.build());
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
   * Sets the layers. This replaces any previously-added layers.
   *
   * @param layerConfigurations the {@link LayerConfiguration}s
   * @return this
   */
  public JibContainerBuilder setLayers(LayerConfiguration... layerConfigurations) {
    return setLayers(Arrays.asList(layerConfigurations));
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
   * <p>This is similar to <a
   * href="https://docs.docker.com/engine/reference/builder/#exec-form-entrypoint-example">{@code
   * ENTRYPOINT} in Dockerfiles</a> or {@code command} in the <a
   * href="https://kubernetes.io/docs/reference/generated/kubernetes-api/v1.11/#container-v1-core">Kubernetes
   * Container spec</a>.
   *
   * @param entrypoint a list of the entrypoint command
   * @return this
   */
  public JibContainerBuilder setEntrypoint(List<String> entrypoint) {
    this.entrypoint = ImmutableList.copyOf(entrypoint);
    return this;
  }

  /**
   * Sets the container entrypoint.
   *
   * @param entrypoint the entrypoint command
   * @return this
   * @see #setEntrypoint(List) for more details
   */
  public JibContainerBuilder setEntrypoint(String... entrypoint) {
    return setEntrypoint(Arrays.asList(entrypoint));
  }

  /**
   * Sets the container entrypoint program arguments. These are additional tokens added to the end
   * of the entrypoint command.
   *
   * <p>This is similar to <a href="https://docs.docker.com/engine/reference/builder/#cmd">{@code
   * CMD} in Dockerfiles</a> or {@code args} in the <a
   * href="https://kubernetes.io/docs/reference/generated/kubernetes-api/v1.11/#container-v1-core">Kubernetes
   * Container spec</a>.
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
    return setProgramArguments(Arrays.asList(programArguments));
  }

  /**
   * Sets the container environment. These environment variables are available to the program
   * launched by the container entrypoint command. This replaces any previously-set environment
   * variables.
   *
   * <p>This is similar to <a href="https://docs.docker.com/engine/reference/builder/#env">{@code
   * ENV} in Dockerfiles</a> or {@code env} in the <a
   * href="https://kubernetes.io/docs/reference/generated/kubernetes-api/v1.11/#container-v1-core">Kubernetes
   * Container spec</a>.
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
   * <p>This is similar to <a href="https://docs.docker.com/engine/reference/builder/#expose">{@code
   * EXPOSE} in Dockerfiles</a> or {@code ports} in the <a
   * href="https://kubernetes.io/docs/reference/generated/kubernetes-api/v1.11/#container-v1-core">Kubernetes
   * Container spec</a>.
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
    return setExposedPorts(Arrays.asList(ports));
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
   * <p>This is similar to <a href="https://docs.docker.com/engine/reference/builder/#label">{@code
   * LABEL} in Dockerfiles</a>.
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

  @VisibleForTesting
  BuildConfiguration toBuildConfiguration(TargetImage targetImage)
      throws IOException, CacheDirectoryCreationException {
    BuildConfiguration.Builder buildConfigurationBuilder = BuildConfiguration.builder();

    buildConfigurationBuilder
        .setBaseImageConfiguration(baseImage.toImageConfiguration())
        .setTargetImageConfiguration(targetImage.toImageConfiguration())
        .setContainerConfiguration(toContainerConfiguration())
        .setLayerConfigurations(layerConfigurations);

    // TODO: Allow users to configure this.
    buildConfigurationBuilder.setToolName("jib-core");

    return buildConfigurationBuilder.build();
  }

  @VisibleForTesting
  ContainerConfiguration toContainerConfiguration() {
    return ContainerConfiguration.builder()
        .setEntrypoint(entrypoint)
        .setProgramArguments(programArguments)
        .setEnvironment(environment)
        .setExposedPorts(ports)
        .setLabels(labels)
        .build();
  }
}
