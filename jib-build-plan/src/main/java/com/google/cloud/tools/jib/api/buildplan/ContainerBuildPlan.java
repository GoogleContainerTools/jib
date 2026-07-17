/*
 * Copyright 2020 Google LLC.
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

package com.google.cloud.tools.jib.api.buildplan;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/** Describes a plan to build a container. */
@Immutable
public class ContainerBuildPlan {

  /** Builder for {@link ContainerBuildPlan}. */
  public static class Builder {

    private String baseImage = "scratch";
    private Instant creationTime = Instant.EPOCH;
    private ImageFormat format = ImageFormat.Docker;
    private CompressionAlgorithm compression = CompressionAlgorithm.GZIP;

    // LinkedHashSet to preserve the order
    private Set<Platform> platforms =
        new LinkedHashSet<>(Collections.singleton(new Platform("amd64", "linux")));

    // image execution parameters
    private Map<String, String> environment = new HashMap<>();
    private Map<String, String> labels = new HashMap<>();
    private Set<AbsoluteUnixPath> volumes = new HashSet<>();
    private Set<Port> exposedPorts = new HashSet<>();
    @Nullable private String user;
    @Nullable private AbsoluteUnixPath workingDirectory;
    @Nullable private List<String> entrypoint;
    @Nullable private List<String> cmd;

    private List<LayerObject> layers = new ArrayList<>();

    private Builder() {}

    /**
     * Image reference to a base image. The default is {@code scratch}.
     *
     * @param baseImage image reference to a base image
     * @return this
     */
    public Builder setBaseImage(String baseImage) {
      this.baseImage = baseImage;
      return this;
    }

    /**
     * Adds a desired image platform (OS and architecture pair). If the base image reference is a
     * Docker manifest list or an OCI image index, an image builder may select the base image
     * matching the given platform. If the base image reference is an image manifest, an image
     * builder may ignore the given platform and use the platform of the base image or may decide to
     * raise on error.
     *
     * <p>Note that a new build plan starts with "amd64/linux" as the default platform. If you want
     * to reset the default platform instead of adding a new one, use {@link #setPlatforms(Set)}.
     *
     * @param architecture architecture (for example, {@code amd64}) to select a base image in case
     *     of a manifest list
     * @param os OS (for example, {@code linux}) to select a base image in case of a manifest list
     * @return this
     */
    public Builder addPlatform(String architecture, String os) {
      platforms.add(new Platform(architecture, os));
      return this;
    }

    /**
     * Sets a desired platform (properties including OS and architecture) list. If the base image
     * reference is a Docker manifest list or an OCI image index, an image builder may select the
     * base images matching the given platforms. If the base image reference is an image manifest,
     * an image builder may ignore the given platforms and use the platform of the base image or may
     * decide to raise on error.
     *
     * <p>Note that a new build plan starts with "amd64/linux" as the default platform.
     *
     * @param platforms list of platforms to select base images in case of a manifest list
     * @return this
     */
    public Builder setPlatforms(Set<Platform> platforms) {
      if (platforms.isEmpty()) {
        throw new IllegalArgumentException("platforms set cannot be empty");
      }
      this.platforms = new LinkedHashSet<>(platforms);
      return this;
    }

    /**
     * Sets the container image creation time. The default is {@link Instant#EPOCH}.
     *
     * @param creationTime the container image creation time
     * @return this
     */
    public Builder setCreationTime(Instant creationTime) {
      this.creationTime = creationTime;
      return this;
    }

    /**
     * Sets the format to build the container image as. Use {@link ImageFormat#Docker} for Docker
     * V2.2 or {@link ImageFormat#OCI} for OCI.
     *
     * @param format the {@link ImageFormat}
     * @return this
     */
    public Builder setFormat(ImageFormat format) {
      this.format = format;
      return this;
    }

    /**
     * Sets the compression algorithm of the built container image layers.
     *
     * @param compression the {@link CompressionAlgorithm}
     * @return this
     */
    public Builder setCompression(CompressionAlgorithm compression) {
      this.compression = compression;
      return this;
    }

    /**
     * Sets the container environment. These environment variables are available to the program
     * launched by the container entrypoint command. This replaces any previously-set environment
     * variables. Note that these values are added to the base image values.
     *
     * <p>This is similar to <a href="https://docs.docker.com/engine/reference/builder/#env">{@code
     * ENV} in Dockerfiles</a> or {@code env} in the <a
     * href="https://kubernetes.io/docs/reference/generated/kubernetes-api/v1.11/#container-v1-core">Kubernetes
     * Container spec</a>.
     *
     * @param environment a map of environment variable names to values
     * @return this
     */
    public Builder setEnvironment(Map<String, String> environment) {
      this.environment = new HashMap<>(environment);
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
    public Builder addEnvironmentVariable(String name, String value) {
      environment.put(name, value);
      return this;
    }

    /**
     * Sets the directories that may hold externally mounted volumes. Note that these values are
     * added to the base image values.
     *
     * <p>This is similar to <a
     * href="https://docs.docker.com/engine/reference/builder/#volume">{@code VOLUME} in
     * Dockerfiles</a>.
     *
     * @param volumes the directory paths on the container filesystem to set as volumes
     * @return this
     */
    public Builder setVolumes(Set<AbsoluteUnixPath> volumes) {
      this.volumes = new HashSet<>(volumes);
      return this;
    }

    /**
     * Adds a directory that may hold an externally mounted volume.
     *
     * @param volume a directory path on the container filesystem to represent a volume
     * @return this
     * @see #setVolumes(Set)
     */
    public Builder addVolume(AbsoluteUnixPath volume) {
      volumes.add(volume);
      return this;
    }

    /**
     * Sets the labels for the container. This replaces any previously-set labels. Note that these
     * values are added to the base image values.
     *
     * <p>This is similar to <a
     * href="https://docs.docker.com/engine/reference/builder/#label">{@code LABEL} in
     * Dockerfiles</a>.
     *
     * @param labels a map of label keys to values
     * @return this
     */
    public Builder setLabels(Map<String, String> labels) {
      this.labels = new HashMap<>(labels);
      return this;
    }

    /**
     * Sets a label for the container.
     *
     * @param key the label key
     * @param value the label value
     * @return this
     */
    public Builder addLabel(String key, String value) {
      labels.put(key, value);
      return this;
    }

    /**
     * Sets the ports to expose from the container. Ports exposed will allow ingress traffic. This
     * replaces any previously-set exposed ports. Note that these values are added to the base image
     * values.
     *
     * <p>Use {@link Port#tcp} to expose a port for TCP traffic and {@link Port#udp} to expose a
     * port for UDP traffic.
     *
     * <p>This is similar to <a
     * href="https://docs.docker.com/engine/reference/builder/#expose">{@code EXPOSE} in
     * Dockerfiles</a> or {@code ports} in the <a
     * href="https://kubernetes.io/docs/reference/generated/kubernetes-api/v1.11/#container-v1-core">Kubernetes
     * Container spec</a>.
     *
     * @param exposedPorts the ports to expose
     * @return this
     */
    public Builder setExposedPorts(Set<Port> exposedPorts) {
      this.exposedPorts = new HashSet<>(exposedPorts);
      return this;
    }

    /**
     * Adds a port to expose from the container.
     *
     * @param exposedPort the port to expose
     * @return this
     * @see #setExposedPorts(Set)
     */
    public Builder addExposedPort(Port exposedPort) {
      exposedPorts.add(exposedPort);
      return this;
    }

    /**
     * Sets the user and group to run the container as. {@code user} can be a username or UID along
     * with an optional groupname or GID. {@code null} signals to use the base image value.
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
    public Builder setUser(@Nullable String user) {
      this.user = user;
      return this;
    }

    /**
     * Sets the working directory in the container. {@code null} signals to use the base image
     * value.
     *
     * @param workingDirectory the working directory
     * @return this
     */
    public Builder setWorkingDirectory(@Nullable AbsoluteUnixPath workingDirectory) {
      this.workingDirectory = workingDirectory;
      return this;
    }

    /**
     * Sets the container entrypoint. This is the beginning of the command that is run when the
     * container starts. {@link #setCmd} sets additional tokens. {@code null} signals to use the
     * base image value.
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
    public Builder setEntrypoint(@Nullable List<String> entrypoint) {
      if (entrypoint == null) {
        this.entrypoint = null;
      } else {
        this.entrypoint = new ArrayList<>(entrypoint);
      }
      return this;
    }

    /**
     * Sets the container entrypoint program arguments. These are additional tokens added to the end
     * of the entrypoint command. {@code null} signals to use the base image value (only when
     * entrypoint is also {@code null}).
     *
     * <p>This is similar to <a href="https://docs.docker.com/engine/reference/builder/#cmd">{@code
     * CMD} in Dockerfiles</a> or {@code args} in the <a
     * href="https://kubernetes.io/docs/reference/generated/kubernetes-api/v1.11/#container-v1-core">Kubernetes
     * Container spec</a>.
     *
     * <p>For example, if the entrypoint was {@code myprogram --flag subcommand} and program
     * arguments were {@code hello world}, then the command that run when the container starts is
     * {@code myprogram --flag subcommand hello world}.
     *
     * @param cmd a list of program argument tokens
     * @return this
     */
    public Builder setCmd(@Nullable List<String> cmd) {
      if (cmd == null) {
        this.cmd = null;
      } else {
        this.cmd = new ArrayList<>(cmd);
      }
      return this;
    }

    public Builder addLayer(LayerObject layer) {
      layers.add(layer);
      return this;
    }

    public Builder setLayers(List<? extends LayerObject> layer) {
      layers = new ArrayList<>(layer);
      return this;
    }

    /**
     * Returns the built {@link ContainerBuildPlan}.
     *
     * @return container build plan
     */
    public ContainerBuildPlan build() {
      return new ContainerBuildPlan(
          baseImage,
          platforms,
          creationTime,
          format,
          compression,
          environment,
          labels,
          volumes,
          exposedPorts,
          user,
          workingDirectory,
          entrypoint,
          cmd,
          layers);
    }
  }

  public static Builder builder() {
    return new Builder();
  }

  private final String baseImage;
  private final Set<Platform> platforms;
  private final Instant creationTime;
  private final ImageFormat format;
  private final CompressionAlgorithm compressionAlgorithm;

  // image execution parameters
  private final Map<String, String> environment;
  private final Map<String, String> labels;
  private final Set<AbsoluteUnixPath> volumes;
  private final Set<Port> exposedPorts;
  @Nullable private final String user;
  @Nullable private final AbsoluteUnixPath workingDirectory;
  @Nullable private final List<String> entrypoint;
  @Nullable private final List<String> cmd;

  private final List<LayerObject> layers;

  private ContainerBuildPlan(
      String baseImage,
      Set<Platform> platforms,
      Instant creationTime,
      ImageFormat format,
      CompressionAlgorithm compressionAlgorithm,
      Map<String, String> environment,
      Map<String, String> labels,
      Set<AbsoluteUnixPath> volumes,
      Set<Port> exposedPorts,
      @Nullable String user,
      @Nullable AbsoluteUnixPath workingDirectory,
      @Nullable List<String> entrypoint,
      @Nullable List<String> cmd,
      List<LayerObject> layers) {
    this.baseImage = baseImage;
    this.platforms = platforms;
    this.creationTime = creationTime;
    this.format = format;
    this.compressionAlgorithm = compressionAlgorithm;
    this.environment = environment;
    this.labels = labels;
    this.volumes = volumes;
    this.exposedPorts = exposedPorts;
    this.user = user;
    this.workingDirectory = workingDirectory;
    this.entrypoint = entrypoint;
    this.cmd = cmd;
    this.layers = layers;
  }

  public String getBaseImage() {
    return baseImage;
  }

  /**
   * Creates and returns a default platform if the user hasn't added or set any platforms ,else
   * returns a list of user specified platforms .
   *
   * @return platforms a list of user specified platforms.
   */
  public Set<Platform> getPlatforms() {
    return new LinkedHashSet<>(platforms);
  }

  public ImageFormat getFormat() {
    return format;
  }

  public CompressionAlgorithm getCompressionAlgorithm() {
    return compressionAlgorithm;
  }

  public Instant getCreationTime() {
    return creationTime;
  }

  public Map<String, String> getEnvironment() {
    return new HashMap<>(environment);
  }

  public Set<AbsoluteUnixPath> getVolumes() {
    return new HashSet<>(volumes);
  }

  public Map<String, String> getLabels() {
    return new HashMap<>(labels);
  }

  public Set<Port> getExposedPorts() {
    return new HashSet<>(exposedPorts);
  }

  @Nullable
  public String getUser() {
    return user;
  }

  @Nullable
  public AbsoluteUnixPath getWorkingDirectory() {
    return workingDirectory;
  }

  @Nullable
  public List<String> getEntrypoint() {
    return entrypoint == null ? null : new ArrayList<>(entrypoint);
  }

  @Nullable
  public List<String> getCmd() {
    return cmd == null ? null : new ArrayList<>(cmd);
  }

  public List<? extends LayerObject> getLayers() {
    return new ArrayList<>(layers);
  }

  /**
   * Creates a builder configured with the current values.
   *
   * @return {@link Builder} configured with the current values.
   */
  public Builder toBuilder() {
    return builder()
        .setBaseImage(baseImage)
        .setPlatforms(platforms)
        .setCreationTime(creationTime)
        .setFormat(format)
        .setCompression(compressionAlgorithm)
        .setEnvironment(environment)
        .setLabels(labels)
        .setVolumes(volumes)
        .setExposedPorts(exposedPorts)
        .setUser(user)
        .setWorkingDirectory(workingDirectory)
        .setEntrypoint(entrypoint)
        .setCmd(cmd)
        .setLayers(layers);
  }
}
