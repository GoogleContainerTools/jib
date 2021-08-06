/*
 * Copyright 2017 Google LLC.
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

package com.google.cloud.tools.jib.image.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.cloud.tools.jib.api.DescriptorDigest;
import com.google.cloud.tools.jib.json.JsonTemplate;
import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * JSON Template for Docker Container Configuration referenced in Docker Manifest Schema V2.2
 *
 * <p>Example container config JSON:
 *
 * <pre>{@code
 * {
 *   "created": "1970-01-01T00:00:00Z",
 *   "architecture": "amd64",
 *   "os": "linux",
 *   "config": {
 *     "Env": ["/usr/bin/java"],
 *     "Entrypoint": ["PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"],
 *     "Cmd": ["arg1", "arg2"],
 *     "Healthcheck": {
 *       "Test": ["CMD-SHELL", "/usr/bin/check-health localhost"],
 *       "Interval": 30000000000,
 *       "Timeout": 10000000000,
 *       "StartPeriod": 0,
 *       "Retries": 3
 *     }
 *     "ExposedPorts": { "6000/tcp":{}, "8000/tcp":{}, "9000/tcp":{} },
 *     "Volumes":{"/var/job-result-data":{},"/var/log/my-app-logs":{}}},
 *     "Labels": { "com.example.label": "value" },
 *     "WorkingDir": "/home/user/workspace",
 *     "User": "me"
 *   },
 *   "history": [
 *     {
 *       "author": "Jib",
 *       "created": "1970-01-01T00:00:00Z",
 *       "created_by": "jib"
 *     },
 *     {
 *       "author": "Jib",
 *       "created": "1970-01-01T00:00:00Z",
 *       "created_by": "jib"
 *     }
 *   ]
 *   "rootfs": {
 *     "diff_ids": [
 *       "sha256:2aebd096e0e237b447781353379722157e6c2d434b9ec5a0d63f2a6f07cf90c2",
 *       "sha256:5f70bf18a086007016e948b04aed3b82103a36bea41755b6cddfaf10ace3c6ef",
 *     ],
 *     "type": "layers"
 *   }
 * }
 * }</pre>
 *
 * @see <a href="https://docs.docker.com/registry/spec/manifest-v2-2/">Image Manifest Version 2,
 *     Schema 2</a>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ContainerConfigurationTemplate implements JsonTemplate {

  /** ISO-8601 formatted combined date and time at which the image was created. */
  @Nullable private String created;

  /** The CPU architecture to run the binaries in this container. */
  private String architecture = "amd64";

  /** The operating system to run the container on. */
  private String os = "linux";

  /** Execution parameters that should be used as a base when running the container. */
  private final ConfigurationObjectTemplate config = new ConfigurationObjectTemplate();

  /** Describes the history of each layer. */
  private final List<HistoryEntry> history = new ArrayList<>();

  /** Layer content digests that are used to build the container filesystem. */
  private final RootFilesystemObjectTemplate rootfs = new RootFilesystemObjectTemplate();

  /** Template for inner JSON object representing the configuration for running the container. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  private static class ConfigurationObjectTemplate implements JsonTemplate {

    /** Environment variables in the format {@code VARNAME=VARVALUE}. */
    @Nullable private List<String> Env;

    /** Command to run when container starts. */
    @Nullable private List<String> Entrypoint;

    /** Arguments to pass into main. */
    @Nullable private List<String> Cmd;

    /** Healthcheck. */
    @Nullable private HealthCheckObjectTemplate Healthcheck;

    /** Network ports the container exposes. */
    @Nullable private Map<String, Map<String, String>> ExposedPorts;

    /** Labels. */
    @Nullable private Map<String, String> Labels;

    /** Working directory. */
    @Nullable private String WorkingDir;

    /** User. */
    @Nullable private String User;

    /** Volumes. */
    @Nullable private Map<String, Map<String, String>> Volumes;
  }

  /** Template for inner JSON object representing the healthcheck configuration. */
  private static class HealthCheckObjectTemplate implements JsonTemplate {

    /** The test to perform to check that the container is healthy. */
    @Nullable private List<String> Test;

    /** Number of nanoseconds to wait between probe attempts. */
    @Nullable private Long Interval;

    /** Number of nanoseconds to wait before considering the check to have hung. */
    @Nullable private Long Timeout;

    /**
     * Number of nanoseconds to wait for the container to initialize before starting health-retries.
     */
    @Nullable private Long StartPeriod;

    /** The number of consecutive failures needed to consider the container as unhealthy. */
    @Nullable private Integer Retries;
  }

  /**
   * Template for inner JSON object representing the filesystem changesets used to build the
   * container filesystem.
   */
  private static class RootFilesystemObjectTemplate implements JsonTemplate {

    /** The type must always be {@code "layers"}. */
    @SuppressWarnings("unused")
    private final String type = "layers";

    /**
     * The in-order list of layer content digests (hashes of the uncompressed partial filesystem
     * changeset).
     */
    private final List<DescriptorDigest> diff_ids = new ArrayList<>();
  }

  public void setCreated(@Nullable String created) {
    this.created = created;
  }

  /**
   * Sets the architecture for which this container was built. See the <a
   * href="https://github.com/opencontainers/image-spec/blob/master/config.md#properties">OCI Image
   * Configuration specification</a> for acceptable values.
   *
   * @param architecture value for the {@code architecture} field
   */
  public void setArchitecture(String architecture) {
    this.architecture = architecture;
  }

  /**
   * Sets the operating system for which this container was built. See the <a
   * href="https://github.com/opencontainers/image-spec/blob/master/config.md#properties">OCI Image
   * Configuration specification</a> for acceptable values.
   *
   * @param os value for the {@code os} field
   */
  public void setOs(String os) {
    this.os = os;
  }

  public void setContainerEnvironment(@Nullable List<String> environment) {
    config.Env = environment;
  }

  public void setContainerEntrypoint(@Nullable List<String> command) {
    config.Entrypoint = command;
  }

  public void setContainerCmd(@Nullable List<String> cmd) {
    config.Cmd = cmd;
  }

  /**
   * Sets test on HealthCheck, creates an empty HealthCheck object if necessary.
   *
   * @param test the list of tests to set
   */
  public void setContainerHealthCheckTest(List<String> test) {
    if (config.Healthcheck == null) {
      config.Healthcheck = new HealthCheckObjectTemplate();
    }
    Preconditions.checkNotNull(config.Healthcheck).Test = test;
  }

  /**
   * Sets interval on HealthCheck, creates an empty HealthCheck object if necessary.
   *
   * @param interval the interval to set
   */
  public void setContainerHealthCheckInterval(@Nullable Long interval) {
    if (config.Healthcheck == null) {
      config.Healthcheck = new HealthCheckObjectTemplate();
    }
    Preconditions.checkNotNull(config.Healthcheck).Interval = interval;
  }

  /**
   * Sets timeout on HealthCheck, creates an empty HealthCheck object if necessary.
   *
   * @param timeout the timeout to configure
   */
  public void setContainerHealthCheckTimeout(@Nullable Long timeout) {
    if (config.Healthcheck == null) {
      config.Healthcheck = new HealthCheckObjectTemplate();
    }
    Preconditions.checkNotNull(config.Healthcheck).Timeout = timeout;
  }

  /**
   * Sets startPeriod on HealthCheck, creates an empty HealthCheck object if necessary.
   *
   * @param startPeriod the start period to configure
   */
  public void setContainerHealthCheckStartPeriod(@Nullable Long startPeriod) {
    if (config.Healthcheck == null) {
      config.Healthcheck = new HealthCheckObjectTemplate();
    }
    Preconditions.checkNotNull(config.Healthcheck).StartPeriod = startPeriod;
  }

  /**
   * Sets retries on HealthCheck, creates an empty HealthCheck object if necessary.
   *
   * @param retries the number of retries to configure
   */
  public void setContainerHealthCheckRetries(@Nullable Integer retries) {
    if (config.Healthcheck == null) {
      config.Healthcheck = new HealthCheckObjectTemplate();
    }
    Preconditions.checkNotNull(config.Healthcheck).Retries = retries;
  }

  public void setContainerExposedPorts(@Nullable Map<String, Map<String, String>> exposedPorts) {
    config.ExposedPorts = exposedPorts;
  }

  public void setContainerLabels(@Nullable Map<String, String> labels) {
    config.Labels = labels;
  }

  public void setContainerWorkingDir(@Nullable String workingDirectory) {
    config.WorkingDir = workingDirectory;
  }

  public void setContainerUser(@Nullable String user) {
    config.User = user;
  }

  public void setContainerVolumes(@Nullable Map<String, Map<String, String>> volumes) {
    config.Volumes = volumes;
  }

  public void addLayerDiffId(DescriptorDigest diffId) {
    rootfs.diff_ids.add(diffId);
  }

  public void addHistoryEntry(HistoryEntry historyEntry) {
    history.add(historyEntry);
  }

  List<DescriptorDigest> getDiffIds() {
    return rootfs.diff_ids;
  }

  List<HistoryEntry> getHistory() {
    return history;
  }

  @Nullable
  String getCreated() {
    return created;
  }

  /**
   * Returns the architecture for which this container was built. See the <a
   * href="https://github.com/opencontainers/image-spec/blob/master/config.md#properties">OCI Image
   * Configuration specification</a> for acceptable values.
   *
   * @return the {@code architecture} field
   */
  public String getArchitecture() {
    return architecture;
  }

  /**
   * Returns the operating system for which this container was built. See the <a
   * href="https://github.com/opencontainers/image-spec/blob/master/config.md#properties">OCI Image
   * Configuration specification</a> for acceptable values.
   *
   * @return the {@code os} field
   */
  public String getOs() {
    return os;
  }

  @Nullable
  List<String> getContainerEnvironment() {
    return config.Env;
  }

  @Nullable
  List<String> getContainerEntrypoint() {
    return config.Entrypoint;
  }

  @Nullable
  List<String> getContainerCmd() {
    return config.Cmd;
  }

  @Nullable
  List<String> getContainerHealthTest() {
    return config.Healthcheck == null ? null : config.Healthcheck.Test;
  }

  @Nullable
  Long getContainerHealthInterval() {
    return config.Healthcheck == null ? null : config.Healthcheck.Interval;
  }

  @Nullable
  Long getContainerHealthTimeout() {
    return config.Healthcheck == null ? null : config.Healthcheck.Timeout;
  }

  @Nullable
  Long getContainerHealthStartPeriod() {
    return config.Healthcheck == null ? null : config.Healthcheck.StartPeriod;
  }

  @Nullable
  Integer getContainerHealthRetries() {
    return config.Healthcheck == null ? null : config.Healthcheck.Retries;
  }

  @Nullable
  Map<String, Map<String, String>> getContainerExposedPorts() {
    return config.ExposedPorts;
  }

  @Nullable
  Map<String, String> getContainerLabels() {
    return config.Labels;
  }

  @Nullable
  String getContainerWorkingDir() {
    return config.WorkingDir;
  }

  @Nullable
  String getContainerUser() {
    return config.User;
  }

  @Nullable
  Map<String, Map<String, String>> getContainerVolumes() {
    return config.Volumes;
  }

  public DescriptorDigest getLayerDiffId(int index) {
    return rootfs.diff_ids.get(index);
  }

  public int getLayerCount() {
    return rootfs.diff_ids.size();
  }
}
