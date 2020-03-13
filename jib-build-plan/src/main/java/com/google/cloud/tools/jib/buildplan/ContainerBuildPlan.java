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

package com.google.cloud.tools.jib.buildplan;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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
    private String architecture = "amd64";
    private String os = "linux";
    private Instant creationTime = Instant.EPOCH;
    private ImageFormat format = ImageFormat.Docker;

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

    /**
     * Container image reference to base the build.
     *
     * @param baseImage a
     * @return this
     */
    public Builder setBaseImage(String baseImage) {
      this.baseImage = baseImage;
      return this;
    }

    public Builder setArchitecture(String architecture) {
      this.architecture = architecture;
      return this;
    }

    public Builder setOs(String os) {
      this.os = os;
      return this;
    }

    public Builder setCreationTime(Instant creationTime) {
      this.creationTime = creationTime;
      return this;
    }

    public Builder setFormat(ImageFormat format) {
      this.format = format;
      return this;
    }

    public Builder setEnvironment(Map<String, String> environment) {
      this.environment = new HashMap<>(environment);
      return this;
    }

    public Builder addEnvironmentVariable(String name, String value) {
      environment.put(name, value);
      return this;
    }

    public Builder setVolumes(Set<AbsoluteUnixPath> volumes) {
      this.volumes = new HashSet<>(volumes);
      return this;
    }

    public Builder addVolume(AbsoluteUnixPath volume) {
      volumes.add(volume);
      return this;
    }

    public Builder setLabels(Map<String, String> labels) {
      this.labels = new HashMap<>(labels);
      return this;
    }

    public Builder addLabel(String key, String value) {
      labels.put(key, value);
      return this;
    }

    public Builder setExposedPorts(Set<Port> exposedPorts) {
      this.exposedPorts = new HashSet<>(exposedPorts);
      return this;
    }

    public Builder addExposedPort(Port exposedPort) {
      exposedPorts.add(exposedPort);
      return this;
    }

    public Builder setUser(@Nullable String user) {
      this.user = user;
      return this;
    }

    public Builder setWorkingDirectory(@Nullable AbsoluteUnixPath workingDirectory) {
      this.workingDirectory = workingDirectory;
      return this;
    }

    /**
     * Sets the container entrypoint. This is the beginning of the command that is run when the
     * container starts. {@link #setCmd} sets additional tokens. {@code null} signals to use the
     * entrypoint of the base image.
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

    public Builder setLayers(List<LayerObject> layer) {
      layers = new ArrayList<>(layer);
      return this;
    }

    public ContainerBuildPlan build() {
      return new ContainerBuildPlan(
          baseImage,
          architecture,
          os,
          creationTime,
          format,
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

  private final String baseImage;
  private final String architecture;
  private final String os;
  private final Instant creationTime;
  private final ImageFormat format;

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

  public ContainerBuildPlan(
      String baseImage,
      String architecture,
      String os,
      Instant creationTime,
      ImageFormat format,
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
    this.architecture = architecture;
    this.os = os;
    this.creationTime = creationTime;
    this.format = format;
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

  public String getArchitecture() {
    return architecture;
  }

  public String getOs() {
    return os;
  }

  public ImageFormat getFormat() {
    return format;
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
    return labels;
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
    return entrypoint;
  }

  @Nullable
  public List<String> getCmd() {
    return cmd;
  }

  public List<LayerObject> getLayers() {
    return layers;
  }

  public Builder toBuilder() {
    return new Builder()
        .setBaseImage(baseImage)
        .setArchitecture(architecture)
        .setOs(os)
        .setCreationTime(creationTime)
        .setFormat(format)
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
