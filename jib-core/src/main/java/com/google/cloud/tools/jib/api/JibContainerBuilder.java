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

import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.buildplan.ContainerBuildPlan;
import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer;
import com.google.cloud.tools.jib.api.buildplan.FileEntry;
import com.google.cloud.tools.jib.api.buildplan.ImageFormat;
import com.google.cloud.tools.jib.api.buildplan.LayerObject;
import com.google.cloud.tools.jib.api.buildplan.Platform;
import com.google.cloud.tools.jib.api.buildplan.Port;
import com.google.cloud.tools.jib.builder.TimerEventDispatcher;
import com.google.cloud.tools.jib.builder.steps.BuildResult;
import com.google.cloud.tools.jib.configuration.BuildContext;
import com.google.cloud.tools.jib.configuration.ContainerConfiguration;
import com.google.cloud.tools.jib.configuration.ImageConfiguration;
import com.google.cloud.tools.jib.docker.DockerClient;
import com.google.cloud.tools.jib.event.EventHandlers;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Verify;
import java.io.IOException;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.apache.http.conn.HttpHostConnectException;

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
public class JibContainerBuilder {

  private static String capitalizeFirstLetter(String string) {
    if (string.isEmpty()) {
      return string;
    }
    return Character.toUpperCase(string.charAt(0)) + string.substring(1);
  }

  private final ContainerBuildPlan.Builder containerBuildPlanBuilder = ContainerBuildPlan.builder();
  // TODO(chanseok): remove and use containerBuildPlanBuilder instead. Note that
  // ContainerConfiguation implements equals() and hashCode(), so need to verify
  // if they are required.
  private final ContainerConfiguration.Builder containerConfigurationBuilder =
      ContainerConfiguration.builder();
  private final BuildContext.Builder buildContextBuilder;

  private ImageConfiguration baseImageConfiguration;
  // TODO(chanseok): remove and use containerBuildPlanBuilder instead.
  private List<FileEntriesLayer> layerConfigurations = new ArrayList<>();

  /** Instantiate with {@link Jib#from}. */
  JibContainerBuilder(RegistryImage baseImage) {
    this(
        ImageConfiguration.builder(baseImage.getImageReference())
            .setCredentialRetrievers(baseImage.getCredentialRetrievers())
            .build(),
        BuildContext.builder());
  }

  /** Instantiate with {@link Jib#from}. */
  JibContainerBuilder(DockerDaemonImage baseImage) {
    this(
        ImageConfiguration.builder(baseImage.getImageReference())
            .setDockerClient(
                new DockerClient(baseImage.getDockerExecutable(), baseImage.getDockerEnvironment()))
            .build(),
        BuildContext.builder());
  }

  /** Instantiate with {@link Jib#from}. */
  JibContainerBuilder(TarImage baseImage) {
    // TODO: Cleanup using scratch as placeholder
    this(
        ImageConfiguration.builder(baseImage.getImageReference().orElse(ImageReference.scratch()))
            .setTarPath(baseImage.getPath())
            .build(),
        BuildContext.builder());
  }

  @VisibleForTesting
  JibContainerBuilder(
      ImageConfiguration imageConfiguration, BuildContext.Builder buildContextBuilder) {
    this.buildContextBuilder = buildContextBuilder.setBaseImageConfiguration(imageConfiguration);
    baseImageConfiguration = imageConfiguration;
    containerBuildPlanBuilder.setBaseImage(imageConfiguration.getImage().toString());
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
   * <p>and the destination to copy to is {@code /path/in/container}, then the new layer will have
   * the following entries for the container file system:
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
    FileEntriesLayer.Builder layerConfigurationBuilder = FileEntriesLayer.builder();

    for (Path file : files) {
      layerConfigurationBuilder.addEntryRecursive(
          file, pathInContainer.resolve(file.getFileName()));
    }

    return addFileEntriesLayer(layerConfigurationBuilder.build());
  }

  /**
   * Adds a new layer to the container with {@code files} as the source files and {@code
   * pathInContainer} as the path to copy the source files to in the container file system.
   *
   * @param files the source files to copy to a new layer in the container
   * @param pathInContainer the path in the container file system corresponding to the {@code
   *     sourceFile}
   * @return this
   * @throws IOException if an exception occurred when recursively listing any directories
   * @throws IllegalArgumentException if {@code pathInContainer} is not an absolute Unix-style path
   * @see #addLayer(List, AbsoluteUnixPath)
   */
  public JibContainerBuilder addLayer(List<Path> files, String pathInContainer) throws IOException {
    return addLayer(files, AbsoluteUnixPath.get(pathInContainer));
  }

  /**
   * Adds a layer (defined by a {@link LayerConfiguration}).
   *
   * @deprecated use {@link #addFileEntriesLayer(FileEntriesLayer)}.
   * @param layerConfiguration the {@link LayerConfiguration}
   * @return this
   */
  @Deprecated
  public JibContainerBuilder addLayer(LayerConfiguration layerConfiguration) {
    return addFileEntriesLayer(layerConfiguration.toFileEntriesLayer());
  }

  /**
   * Adds a layer (defined by a {@link FileEntriesLayer}).
   *
   * @param layer the {@link FileEntriesLayer}
   * @return this
   */
  public JibContainerBuilder addFileEntriesLayer(FileEntriesLayer layer) {
    containerBuildPlanBuilder.addLayer(layer);
    layerConfigurations.add(layer);
    return this;
  }

  /**
   * Sets the layers (defined by a list of {@link LayerConfiguration}s). This replaces any
   * previously-added layers.
   *
   * @deprecated use {@link #setFileEntriesLayers(List)}.
   * @param layerConfigurations the list of {@link LayerConfiguration}s
   * @return this
   */
  @Deprecated
  public JibContainerBuilder setLayers(List<LayerConfiguration> layerConfigurations) {
    return setFileEntriesLayers(
        layerConfigurations
            .stream()
            .map(LayerConfiguration::toFileEntriesLayer)
            .collect(Collectors.toList()));
  }

  /**
   * Sets the layers (defined by a list of {@link FileEntriesLayer}s). This replaces any
   * previously-added layers.
   *
   * @param layers the list of {@link FileEntriesLayer}s
   * @return this
   */
  public JibContainerBuilder setFileEntriesLayers(List<FileEntriesLayer> layers) {
    containerBuildPlanBuilder.setLayers(layers);
    layerConfigurations = new ArrayList<>(layers);
    return this;
  }

  /**
   * Sets the layers. This replaces any previously-added layers.
   *
   * @deprecated use {@link #setFileEntriesLayers(FileEntriesLayer...)}.
   * @param layerConfigurations the {@link LayerConfiguration}s
   * @return this
   */
  @Deprecated
  public JibContainerBuilder setLayers(LayerConfiguration... layerConfigurations) {
    return setLayers(Arrays.asList(layerConfigurations));
  }

  /**
   * Sets the layers. This replaces any previously-added layers.
   *
   * @param layers the {@link FileEntriesLayer}s
   * @return this
   */
  public JibContainerBuilder setFileEntriesLayers(FileEntriesLayer... layers) {
    return setFileEntriesLayers(Arrays.asList(layers));
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
    containerBuildPlanBuilder.setEntrypoint(entrypoint);
    containerConfigurationBuilder.setEntrypoint(entrypoint);
    return this;
  }

  /**
   * Sets the container entrypoint.
   *
   * @param entrypoint the entrypoint command
   * @return this
   * @see #setEntrypoint(List)
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
    containerBuildPlanBuilder.setCmd(programArguments);
    containerConfigurationBuilder.setProgramArguments(programArguments);
    return this;
  }

  /**
   * Sets the container entrypoint program arguments.
   *
   * @param programArguments program arguments tokens
   * @return this
   * @see #setProgramArguments(List)
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
    containerBuildPlanBuilder.setEnvironment(environmentMap);
    containerConfigurationBuilder.setEnvironment(environmentMap);
    return this;
  }

  /**
   * Adds a variable in the container environment.
   *
   * @param name the environment variable name
   * @param value the environment variable value
   * @return this
   * @see #setEnvironment
   */
  public JibContainerBuilder addEnvironmentVariable(String name, String value) {
    containerBuildPlanBuilder.addEnvironmentVariable(name, value);
    containerConfigurationBuilder.addEnvironment(name, value);
    return this;
  }

  /**
   * Sets the directories that may hold externally mounted volumes.
   *
   * <p>This is similar to <a href="https://docs.docker.com/engine/reference/builder/#volume">{@code
   * VOLUME} in Dockerfiles</a>.
   *
   * @param volumes the directory paths on the container filesystem to set as volumes
   * @return this
   */
  public JibContainerBuilder setVolumes(Set<AbsoluteUnixPath> volumes) {
    containerBuildPlanBuilder.setVolumes(volumes);
    containerConfigurationBuilder.setVolumes(volumes);
    return this;
  }

  /**
   * Sets the directories that may hold externally mounted volumes.
   *
   * @param volumes the directory paths on the container filesystem to set as volumes
   * @return this
   * @see #setVolumes(Set)
   */
  public JibContainerBuilder setVolumes(AbsoluteUnixPath... volumes) {
    return setVolumes(new HashSet<>(Arrays.asList(volumes)));
  }

  /**
   * Adds a directory that may hold an externally mounted volume.
   *
   * @param volume a directory path on the container filesystem to represent a volume
   * @return this
   * @see #setVolumes(Set)
   */
  public JibContainerBuilder addVolume(AbsoluteUnixPath volume) {
    containerBuildPlanBuilder.addVolume(volume);
    containerConfigurationBuilder.addVolume(volume);
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
   * @param ports the ports to expose
   * @return this
   */
  public JibContainerBuilder setExposedPorts(Set<Port> ports) {
    containerBuildPlanBuilder.setExposedPorts(ports);
    containerConfigurationBuilder.setExposedPorts(ports);
    return this;
  }

  /**
   * Sets the ports to expose from the container. This replaces any previously-set exposed ports.
   *
   * @param ports the ports to expose
   * @return this
   * @see #setExposedPorts(Set)
   */
  public JibContainerBuilder setExposedPorts(Port... ports) {
    return setExposedPorts(new HashSet<>(Arrays.asList(ports)));
  }

  /**
   * Adds a port to expose from the container.
   *
   * @param port the port to expose
   * @return this
   * @see #setExposedPorts(Set)
   */
  public JibContainerBuilder addExposedPort(Port port) {
    containerBuildPlanBuilder.addExposedPort(port);
    containerConfigurationBuilder.addExposedPort(port);
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
    containerBuildPlanBuilder.setLabels(labelMap);
    containerConfigurationBuilder.setLabels(labelMap);
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
    containerBuildPlanBuilder.addLabel(key, value);
    containerConfigurationBuilder.addLabel(key, value);
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
    containerBuildPlanBuilder.setFormat(imageFormat);
    buildContextBuilder.setTargetFormat(imageFormat);
    return this;
  }

  /**
   * Sets the container image creation time. The default is {@link Instant#EPOCH}.
   *
   * @param creationTime the container image creation time
   * @return this
   */
  public JibContainerBuilder setCreationTime(Instant creationTime) {
    containerBuildPlanBuilder.setCreationTime(creationTime);
    containerConfigurationBuilder.setCreationTime(creationTime);
    return this;
  }

  /**
   * Sets a desired platform (properties including OS and architecture) list. If the base image
   * reference is a Docker manifest list or an OCI image index, an image builder may select the base
   * images matching the given platforms. If the base image reference is an image manifest, an image
   * builder may ignore the given platforms and use the platform of the base image or may decide to
   * raise on error.
   *
   * <p>Note that a new container builder starts with "amd64/linux" as the default platform.
   *
   * @param platforms list of platforms to select base images in case of a manifest list
   * @return this
   */
  public JibContainerBuilder setPlatforms(Set<Platform> platforms) {
    containerBuildPlanBuilder.setPlatforms(platforms);
    containerConfigurationBuilder.setPlatforms(platforms);
    return this;
  }

  /**
   * Adds a desired image platform (OS and architecture pair). If the base image reference is a
   * Docker manifest list or an OCI image index, an image builder may select the base image matching
   * the given platform. If the base image reference is an image manifest, an image builder may
   * ignore the given platform and use the platform of the base image or may decide to raise on
   * error.
   *
   * <p>Note that a new new container builder starts with "amd64/linux" as the default platform. If
   * you want to reset the default platform instead of adding a new one, use {@link
   * #setPlatforms(Set)}.
   *
   * @param architecture architecture (for example, {@code amd64}) to select a base image in case of
   *     a manifest list
   * @param os OS (for example, {@code linux}) to select a base image in case of a manifest list
   * @return this
   */
  public JibContainerBuilder addPlatform(String architecture, String os) {
    containerBuildPlanBuilder.addPlatform(architecture, os);
    containerConfigurationBuilder.addPlatform(architecture, os);
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
   *   <li>{@code :group}
   *   <li>{@code :gid}
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
    containerBuildPlanBuilder.setUser(user);
    containerConfigurationBuilder.setUser(user);
    return this;
  }

  /**
   * Sets the working directory in the container.
   *
   * @param workingDirectory the working directory
   * @return this
   */
  public JibContainerBuilder setWorkingDirectory(@Nullable AbsoluteUnixPath workingDirectory) {
    containerBuildPlanBuilder.setWorkingDirectory(workingDirectory);
    containerConfigurationBuilder.setWorkingDirectory(workingDirectory);
    return this;
  }

  /**
   * Builds the container.
   *
   * @param containerizer the {@link Containerizer} that configures how to containerize
   * @return the built container
   * @throws IOException if an I/O exception occurs
   * @throws CacheDirectoryCreationException if a directory to be used for the cache could not be
   *     created
   * @throws HttpHostConnectException if jib failed to connect to a registry
   * @throws RegistryUnauthorizedException if a registry request is unauthorized and needs
   *     authentication
   * @throws RegistryAuthenticationFailedException if registry authentication failed
   * @throws UnknownHostException if the registry does not exist
   * @throws InsecureRegistryException if a server could not be verified due to an insecure
   *     connection
   * @throws RegistryException if some other error occurred while interacting with a registry
   * @throws ExecutionException if some other exception occurred during execution
   * @throws InterruptedException if the execution was interrupted
   */
  public JibContainer containerize(Containerizer containerizer)
      throws InterruptedException, RegistryException, IOException, CacheDirectoryCreationException,
          ExecutionException {
    try (BuildContext buildContext = toBuildContext(containerizer);
        TimerEventDispatcher ignored =
            new TimerEventDispatcher(
                buildContext.getEventHandlers(), containerizer.getDescription())) {
      logSources(buildContext.getEventHandlers());

      BuildResult buildResult = containerizer.run(buildContext);
      return JibContainer.from(buildContext, buildResult);

    } catch (ExecutionException ex) {
      // If an ExecutionException occurs, re-throw the cause to be more easily handled by the user
      if (ex.getCause() instanceof RegistryException) {
        throw (RegistryException) ex.getCause();
      }
      throw ex;
    }
  }

  /**
   * Describes the container contents and configuration without actually physically building a
   * container.
   *
   * @deprecated use {@link #toContainerBuildPlan}.
   * @return a description of the container being built
   */
  @Deprecated
  public JibContainerDescription describeContainer() {
    return new JibContainerDescription(layerConfigurations);
  }

  /**
   * Internal method. API end users should not use it.
   *
   * <p>Converts to {@link ContainerBuildPlan}. Note that not all values that this class holds can
   * be described by a build plan, such as {@link CredentialRetriever}s for {@link RegistryImage},
   * {@link DockerClient} for {@link DockerDaemonImage}, and output path for {@link TarImage}.
   *
   * @return {@link ContainerBuildPlan}
   */
  public ContainerBuildPlan toContainerBuildPlan() {
    return containerBuildPlanBuilder.build();
  }

  /**
   * Internal method. API end users should not use it.
   *
   * <p>Reconfigures {@link JibContainerBuilder} from the given {@code buildPlan}. Every value
   * configurable using "setters" in this class is overwritten by the value in {@code buildPlan};
   * only retained are some base image properties inherent in {@link JibContainerBuilder} but absent
   * in {@link ContainerBuildPlan}, such as {@link CredentialRetriever}s for {@link RegistryImage},
   * {@link DockerClient} for {@link DockerDaemonImage}, and output path for {@link TarImage}.
   *
   * @param buildPlan build plan to apply
   * @return {@link JibContainerBuilder} reconfigured from {@code buildPlan}
   * @throws InvalidImageReferenceException if the base image value in {@code buildPlan} is an
   *     invalid reference
   */
  public JibContainerBuilder applyContainerBuildPlan(ContainerBuildPlan buildPlan)
      throws InvalidImageReferenceException {
    containerBuildPlanBuilder
        .setBaseImage(buildPlan.getBaseImage())
        .setPlatforms(buildPlan.getPlatforms())
        .setCreationTime(buildPlan.getCreationTime())
        .setFormat(buildPlan.getFormat())
        .setEnvironment(buildPlan.getEnvironment())
        .setLabels(buildPlan.getLabels())
        .setVolumes(buildPlan.getVolumes())
        .setExposedPorts(buildPlan.getExposedPorts())
        .setUser(buildPlan.getUser())
        .setWorkingDirectory(buildPlan.getWorkingDirectory())
        .setEntrypoint(buildPlan.getEntrypoint())
        .setCmd(buildPlan.getCmd())
        .setLayers(buildPlan.getLayers());

    containerConfigurationBuilder
        .setPlatforms(buildPlan.getPlatforms())
        .setCreationTime(buildPlan.getCreationTime())
        .setEnvironment(buildPlan.getEnvironment())
        .setLabels(buildPlan.getLabels())
        .setVolumes(buildPlan.getVolumes())
        .setExposedPorts(buildPlan.getExposedPorts())
        .setUser(buildPlan.getUser())
        .setWorkingDirectory(buildPlan.getWorkingDirectory())
        .setEntrypoint(buildPlan.getEntrypoint())
        .setProgramArguments(buildPlan.getCmd());

    ImageConfiguration.Builder builder =
        ImageConfiguration.builder(ImageReference.parse(buildPlan.getBaseImage()))
            .setCredentialRetrievers(baseImageConfiguration.getCredentialRetrievers());
    baseImageConfiguration.getDockerClient().ifPresent(builder::setDockerClient);
    baseImageConfiguration.getTarPath().ifPresent(builder::setTarPath);
    baseImageConfiguration = builder.build();

    // For now, only FileEntriesLayer is supported in jib-core.
    Function<LayerObject, FileEntriesLayer> castToFileEntriesLayer =
        layer -> {
          Verify.verify(
              layer instanceof FileEntriesLayer,
              "layer types other than FileEntriesLayer not yet supported in build plan layers");
          return (FileEntriesLayer) layer;
        };
    layerConfigurations =
        buildPlan.getLayers().stream().map(castToFileEntriesLayer).collect(Collectors.toList());

    buildContextBuilder
        .setTargetFormat(buildPlan.getFormat())
        .setBaseImageConfiguration(baseImageConfiguration)
        .setLayerConfigurations(layerConfigurations);
    return this;
  }

  /**
   * Builds a {@link BuildContext} using this and a {@link Containerizer}.
   *
   * @param containerizer the {@link Containerizer}
   * @return the {@link BuildContext}
   * @throws CacheDirectoryCreationException if a cache directory could not be created
   */
  @VisibleForTesting
  BuildContext toBuildContext(Containerizer containerizer) throws CacheDirectoryCreationException {
    return buildContextBuilder
        .setTargetImageConfiguration(containerizer.getImageConfiguration())
        .setAdditionalTargetImageTags(containerizer.getAdditionalTags())
        .setBaseImageLayersCacheDirectory(containerizer.getBaseImageLayersCacheDirectory())
        .setApplicationLayersCacheDirectory(containerizer.getApplicationLayersCacheDirectory())
        .setContainerConfiguration(containerConfigurationBuilder.build())
        .setLayerConfigurations(layerConfigurations)
        .setAllowInsecureRegistries(containerizer.getAllowInsecureRegistries())
        .setOffline(containerizer.isOfflineMode())
        .setToolName(containerizer.getToolName())
        .setToolVersion(containerizer.getToolVersion())
        .setExecutorService(containerizer.getExecutorService().orElse(null))
        .setEventHandlers(containerizer.buildEventHandlers())
        .setAlwaysCacheBaseImage(containerizer.getAlwaysCacheBaseImage())
        .build();
  }

  private void logSources(EventHandlers eventHandlers) {
    // Logs the different source files used.
    eventHandlers.dispatch(LogEvent.info("Containerizing application with the following files:"));

    for (FileEntriesLayer layer : layerConfigurations) {
      if (layer.getEntries().isEmpty()) {
        continue;
      }

      eventHandlers.dispatch(LogEvent.info("\t" + capitalizeFirstLetter(layer.getName()) + ":"));

      for (FileEntry entry : layer.getEntries()) {
        eventHandlers.dispatch(LogEvent.info("\t\t" + entry.getSourceFile()));
      }
    }
  }
}
