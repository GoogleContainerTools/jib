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

package com.google.cloud.tools.jib.cli.jar;

import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.buildplan.ImageFormat;
import com.google.cloud.tools.jib.api.buildplan.LayerObject;
import com.google.cloud.tools.jib.api.buildplan.Port;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

/** Class for storing image parameters derived from {@link JarProcessor}. */
public class JarInfo {

  /** Builder for {@link JarInfo}. */
  public static class Builder {

    private String baseImage = "scratch";
    private ImageFormat format = ImageFormat.Docker;

    private Map<String, String> environment = new HashMap<>();
    private Map<String, String> labels = new HashMap<>();
    private Set<AbsoluteUnixPath> volumes = new HashSet<>();
    private Set<Port> exposedPorts = new HashSet<>();
    @Nullable private String user;
    @Nullable private List<String> entrypoint;
    @Nullable private List<String> programArguments;

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
     * Sets the container environment.
     *
     * @param environment a map of environment variable names to values.
     * @return this
     */
    public Builder setEnvironment(Map<String, String> environment) {
      this.environment = new HashMap<String, String>(environment);
      return this;
    }

    /**
     * Sets the directories that may hold externally mounted volumes. Note that these values are
     * added to the base image values.
     *
     * @param volumes the directory paths on the container filesystem to set as volumes.
     * @return this
     */
    public Builder setVolumes(Set<AbsoluteUnixPath> volumes) {
      this.volumes = new HashSet<>(volumes);
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
     * Sets the container entrypoint. This is the beginning of the command that is run when the
     * container starts. {@link #setProgramArguments(List)} sets additional tokens. {@code null}
     * signals to use the base image value. builder
     *
     * <p>This is similar to <a
     * href="https://docs.docker.com/engine/reference//#exec-form-entrypoint-example">{@code
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
     * @param programArguments a list of program argument tokens
     * @return this
     */
    public Builder setProgramArguments(@Nullable List<String> programArguments) {
      if (programArguments == null) {
        this.programArguments = null;
      } else {
        this.programArguments = new ArrayList<>(programArguments);
      }
      return this;
    }

    public Builder setLayers(List<? extends LayerObject> layers) {
      this.layers = new ArrayList<>(layers);
      return this;
    }

    /**
     * Returns the built {@link JarInfo}.
     *
     * @return container build plan
     */
    public JarInfo build() {
      return new JarInfo(
          baseImage,
          format,
          environment,
          labels,
          volumes,
          exposedPorts,
          user,
          entrypoint,
          programArguments,
          layers);
    }
  }

  public static Builder builder() {
    return new Builder();
  }

  private final String baseImage;
  private final ImageFormat format;

  // image execution parameters
  private final Map<String, String> environment;
  private final Map<String, String> labels;
  private final Set<AbsoluteUnixPath> volumes;
  private final Set<Port> exposedPorts;
  @Nullable private final String user;
  @Nullable private final List<String> entrypoint;
  @Nullable private final List<String> programArguments;

  private final List<LayerObject> layers;

  private JarInfo(
      String baseImage,
      ImageFormat format,
      Map<String, String> environment,
      Map<String, String> labels,
      Set<AbsoluteUnixPath> volumes,
      Set<Port> exposedPorts,
      @Nullable String user,
      @Nullable List<String> entrypoint,
      @Nullable List<String> programArguments,
      List<LayerObject> layers) {
    this.baseImage = baseImage;
    this.format = format;
    this.environment = environment;
    this.labels = labels;
    this.volumes = volumes;
    this.exposedPorts = exposedPorts;
    this.user = user;
    this.entrypoint = entrypoint;
    this.programArguments = programArguments;
    this.layers = layers;
  }

  public String getBaseImage() {
    return baseImage;
  }

  public ImageFormat getFormat() {
    return format;
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
  public List<String> getEntrypoint() {
    return entrypoint == null ? null : new ArrayList<>(entrypoint);
  }

  @Nullable
  public List<String> getProgramArguments() {
    return programArguments == null ? null : new ArrayList<>(programArguments);
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
        .setFormat(format)
        .setEnvironment(environment)
        .setLabels(labels)
        .setVolumes(volumes)
        .setExposedPorts(exposedPorts)
        .setUser(user)
        .setEntrypoint(entrypoint)
        .setProgramArguments(programArguments)
        .setLayers(layers);
  }
}
