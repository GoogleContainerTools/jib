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
import com.google.cloud.tools.jib.image.DescriptorDigest;
import com.google.cloud.tools.jib.json.JsonTemplate;
import com.google.common.annotations.VisibleForTesting;
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
 *     "ExposedPorts": { "6000/tcp":{}, "8000/tcp":{}, "9000/tcp":{} },
 *     "Labels": { "com.example.label": "value" },
 *     "WorkingDir": "/home/user/workspace"
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

    /** Network ports the container exposes. */
    @Nullable private Map<String, Map<?, ?>> ExposedPorts;

    /** Labels. */
    @Nullable private Map<String, String> Labels;

    /** Working directory. */
    @Nullable private String WorkingDir;
  }

  /**
   * Template for inner JSON object representing the filesystem changesets used to build the
   * container filesystem.
   */
  private static class RootFilesystemObjectTemplate implements JsonTemplate {

    /** The type must always be {@code "layers"}. */
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

  public void setContainerEnvironment(@Nullable List<String> environment) {
    config.Env = environment;
  }

  public void setContainerEntrypoint(@Nullable List<String> command) {
    config.Entrypoint = command;
  }

  public void setContainerCmd(@Nullable List<String> cmd) {
    config.Cmd = cmd;
  }

  public void setContainerExposedPorts(@Nullable Map<String, Map<?, ?>> exposedPorts) {
    config.ExposedPorts = exposedPorts;
  }

  public void setContainerLabels(@Nullable Map<String, String> labels) {
    config.Labels = labels;
  }

  public void setContainerWorkingDir(@Nullable String workingDirectory) {
    config.WorkingDir = workingDirectory;
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
  Map<String, Map<?, ?>> getContainerExposedPorts() {
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

  @VisibleForTesting
  DescriptorDigest getLayerDiffId(int index) {
    return rootfs.diff_ids.get(index);
  }
}
