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

import com.google.cloud.tools.jib.builder.steps.BuildResult;
import com.google.cloud.tools.jib.configuration.BuildConfiguration;
import com.google.cloud.tools.jib.configuration.CacheDirectoryCreationException;
import com.google.cloud.tools.jib.configuration.ContainerConfiguration;
import com.google.cloud.tools.jib.configuration.LayerConfiguration;
import com.google.cloud.tools.jib.configuration.Port;
import com.google.cloud.tools.jib.event.DefaultEventDispatcher;
import com.google.cloud.tools.jib.filesystem.AbsoluteUnixPath;
import com.google.cloud.tools.jib.image.ImageFormat;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
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
  private ImageFormat imageFormat = ImageFormat.Docker;
  private Instant creationTime = Instant.EPOCH;
  @Nullable private String user;

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
  public JibContainerBuilder setEntrypoint(@Nullable List<String> entrypoint) {
    this.entrypoint = entrypoint == null ? null : ImmutableList.copyOf(entrypoint);
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
  public JibContainerBuilder setProgramArguments(@Nullable List<String> programArguments) {
    this.programArguments =
        programArguments == null ? null : ImmutableList.copyOf(programArguments);
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

  /**
   * Sets the format to build the container image as. Use {@link ImageFormat#Docker} for Docker V2.2
   * or {@link ImageFormat#OCI} for OCI.
   *
   * @param imageFormat the {@link ImageFormat}
   * @return this
   */
  public JibContainerBuilder setFormat(ImageFormat imageFormat) {
    this.imageFormat = imageFormat;
    return this;
  }

  /**
   * Sets the container image creation time. The default is {@link Instant#EPOCH}.
   *
   * @param creationTime the container image creation time
   * @return this
   */
  public JibContainerBuilder setCreationTime(Instant creationTime) {
    this.creationTime = creationTime;
    return this;
  }

  /**
   * Sets the user and group to run the container as. {@code user} can be a username or UID along
   * with an optional groupname or GID.
   *
   * <p>The following are valid formats for {@code user}
   *
   * <ul>
   *   <li>{@code user}
   *   <li>{@code uid}
   *   <li>{@code user:group}
   *   <li>{@code uid:gid}
   *   <li>{@code uid:group}
   *   <li>{@code user:gid}
   * </ul>
   *
   * @param user the user to run the container as
   * @return this
   */
  public JibContainerBuilder setUser(@Nullable String user) {
    this.user = user;
    return this;
  }

  /**
   * Builds the container.
   *
   * @param containerizer the {@link Containerizer} that configures how to containerize
   * @return the built container
   * @throws CacheDirectoryCreationException if a directory to be used for the cache could not be
   *     created
   * @throws ExecutionException if an exception occurred during execution
   * @throws InterruptedException if the execution was interrupted
   * @throws IOException if an I/O exception occurs
   */
  public JibContainer containerize(Containerizer containerizer)
      throws InterruptedException, ExecutionException, IOException,
          CacheDirectoryCreationException {
    BuildConfiguration buildConfiguration =
        toBuildConfiguration(BuildConfiguration.builder(), containerizer);
    BuildResult result = containerizer.getTargetImage().toBuildSteps(buildConfiguration).run();
    return new JibContainer(result.getImageDigest(), result.getImageId());
  }

  /**
   * Builds a {@link BuildConfiguration} using this and a {@link Containerizer}.
   *
   * @param buildConfigurationBuilder the {@link BuildConfiguration.Builder} to use
   * @param containerizer the {@link Containerizer}
   * @return the {@link BuildConfiguration}
   * @throws CacheDirectoryCreationException if a cache directory could not be created
   * @throws IOException if an I/O exception occurs
   */
  @VisibleForTesting
  BuildConfiguration toBuildConfiguration(
      BuildConfiguration.Builder buildConfigurationBuilder, Containerizer containerizer)
      throws CacheDirectoryCreationException, IOException {
    buildConfigurationBuilder
        .setBaseImageConfiguration(baseImage.toImageConfiguration())
        .setTargetImageConfiguration(containerizer.getTargetImage().toImageConfiguration())
        .setAdditionalTargetImageTags(containerizer.getAdditionalTags())
        .setBaseImageLayersCacheDirectory(containerizer.getBaseImageLayersCacheDirectory())
        .setApplicationLayersCacheDirectory(containerizer.getApplicationLayersCacheDirectory())
        .setContainerConfiguration(toContainerConfiguration())
        .setLayerConfigurations(layerConfigurations)
        .setTargetFormat(imageFormat.getManifestTemplateClass())
        .setAllowInsecureRegistries(containerizer.getAllowInsecureRegistries())
        .setToolName(containerizer.getToolName());

    containerizer.getExecutorService().ifPresent(buildConfigurationBuilder::setExecutorService);

    containerizer
        .getEventHandlers()
        .ifPresent(
            eventHandlers ->
                buildConfigurationBuilder.setEventDispatcher(
                    new DefaultEventDispatcher(eventHandlers)));

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
        .setCreationTime(creationTime)
        .setUser(user)
        .build();
  }
}
