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

package com.google.cloud.tools.jib.cli.buildfile;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.cloud.tools.jib.api.Ports;
import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.buildplan.ImageFormat;
import com.google.cloud.tools.jib.api.buildplan.Port;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/**
 * A yaml block for specifying a jib cli buildfile.
 *
 * <p>Example use of this yaml.
 *
 * <pre>{@code
 * apiVersion: v1alpha1
 * kind: BuildFile
 * from: see {@link BaseImageSpec}
 * creationTime: 100
 * format: docker
 * environment:
 *   env_key: env_value
 * labels:
 *   label_key: label_value
 * volumes:
 *   - /my/volume
 * exposedPorts:
 *   - 8080
 * user: username
 * workingDirectory: /workspace
 * entrypoint:
 *   - java
 *   - -jar
 * cmd:
 *   - myjar.jar
 * layers: see {@link LayersSpec}
 * }</pre>
 */
public class BuildFileSpec {
  private String apiVersion;
  private String kind;
  @Nullable private BaseImageSpec from;
  @Nullable private Instant creationTime;
  @Nullable private ImageFormat format;
  private Map<String, String> environment;
  private Map<String, String> labels;
  private Set<AbsoluteUnixPath> volumes;
  private Set<Port> exposedPorts;
  @Nullable private String user;
  @Nullable private AbsoluteUnixPath workingDirectory;
  // these two specific collection can be null as they can be set to empty to override
  // the base image
  @Nullable private List<String> entrypoint;
  @Nullable private List<String> cmd;

  @Nullable private LayersSpec layers;

  /**
   * Constructor for use by jackson to populate this object.
   *
   * @param apiVersion the api version of this buildfile
   * @param kind the type of configuration file (always BuildFile)
   * @param from a {@link BaseImageSpec} for specifying the base image
   * @param creationTime in milliseconds since epoch or ISO 8601 datetime
   * @param format of the container, valid values in {@link ImageFormat}
   * @param environment to write into container
   * @param labels to write into container metadata
   * @param volumes directories on container that may hold external volumes
   * @param exposedPorts a set of ports to expose on the container
   * @param user the username or id to run the container
   * @param workingDirectory an absolute path to the default working directory
   * @param entrypoint the container entry point
   * @param cmd the container entrypoint command arguments
   * @param layers a list of {@link LayerSpec} that define the container filesystem
   */
  @JsonCreator
  public BuildFileSpec(
      @JsonProperty(value = "apiVersion", required = true) String apiVersion,
      @JsonProperty(value = "kind", required = true) String kind,
      @JsonProperty("from") BaseImageSpec from,
      @JsonProperty("creationTime") String creationTime,
      @JsonProperty("format") String format,
      @JsonProperty("environment") Map<String, String> environment,
      @JsonProperty("labels") Map<String, String> labels,
      @JsonProperty("volumes") Set<String> volumes,
      @JsonProperty("exposedPorts") List<String> exposedPorts,
      @JsonProperty("user") String user,
      @JsonProperty("workingDirectory") String workingDirectory,
      @JsonProperty("entrypoint") List<String> entrypoint,
      @JsonProperty("cmd") List<String> cmd,
      @JsonProperty("layers") LayersSpec layers) {
    Validator.checkNotEmpty(apiVersion, "apiVersion");
    Validator.checkEquals(kind, "kind", "BuildFile");
    this.apiVersion = apiVersion;
    Preconditions.checkArgument(
        "BuildFile".equals(kind), "Field 'kind' must be BuildFile but is " + kind);
    this.kind = kind;
    this.from = from;
    if (creationTime != null) {
      this.creationTime = Instants.fromMillisOrIso8601(creationTime, "creationTime");
    }
    if (format != null) {
      this.format = ImageFormat.valueOf(format);
    }
    this.environment = (environment == null) ? ImmutableMap.of() : environment;
    this.labels = (labels == null) ? ImmutableMap.of() : labels;
    this.volumes =
        (volumes == null)
            ? ImmutableSet.of()
            : volumes.stream().map(AbsoluteUnixPath::get).collect(Collectors.toSet());
    this.exposedPorts = (exposedPorts == null) ? ImmutableSet.of() : Ports.parse(exposedPorts);
    this.user = user;
    if (workingDirectory != null) {
      this.workingDirectory = AbsoluteUnixPath.get(workingDirectory);
    }
    this.entrypoint = entrypoint;
    this.cmd = cmd;
    this.layers = layers;
  }

  public String getApiVersion() {
    return apiVersion;
  }

  public String getKind() {
    return kind;
  }

  public Optional<BaseImageSpec> getFrom() {
    return Optional.ofNullable(from);
  }

  public Optional<Instant> getCreationTime() {
    return Optional.ofNullable(creationTime);
  }

  public Optional<ImageFormat> getFormat() {
    return Optional.ofNullable(format);
  }

  public Map<String, String> getEnvironment() {
    return environment;
  }

  public Map<String, String> getLabels() {
    return labels;
  }

  public Set<AbsoluteUnixPath> getVolumes() {
    return volumes;
  }

  public Set<Port> getExposedPorts() {
    return exposedPorts;
  }

  public Optional<String> getUser() {
    return Optional.ofNullable(user);
  }

  public Optional<AbsoluteUnixPath> getWorkingDirectory() {
    return Optional.ofNullable(workingDirectory);
  }

  public Optional<List<String>> getEntrypoint() {
    return Optional.ofNullable(entrypoint);
  }

  public Optional<List<String>> getCmd() {
    return Optional.ofNullable(cmd);
  }

  public Optional<LayersSpec> getLayers() {
    return Optional.ofNullable(layers);
  }
}
