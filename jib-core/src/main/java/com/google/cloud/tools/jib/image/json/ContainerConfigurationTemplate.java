/*
 * Copyright 2017 Google Inc.
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
import javax.annotation.Nullable;

/**
 * JSON Template for Docker Container Configuration referenced in Docker Manifest Schema V2.2
 *
 * <p>Example container config JSON:
 *
 * <pre>{@code
 * {
 *   "architecture": "amd64",
 *   "os": "linux",
 *   "config": {
 *     "Env": ["/usr/bin/java"],
 *     "Entrypoint": ["PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"]
 *   },
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

  /** The CPU architecture to run the binaries in this container. */
  private String architecture = "amd64";

  /** The operating system to run the container on. */
  private String os = "linux";

  /** Execution parameters that should be used as a base when running the container. */
  private final ConfigurationObjectTemplate config = new ConfigurationObjectTemplate();

  /** Layer content digests that are used to build the container filesystem. */
  private final RootFilesystemObjectTemplate rootfs = new RootFilesystemObjectTemplate();

  /** Template for inner JSON object representing the configuration for running the container. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  private static class ConfigurationObjectTemplate implements JsonTemplate {

    /** Environment variables in the format {@code VARNAME=VARVALUE}. */
    @Nullable private List<String> Env;

    /** Command to run when container starts. */
    @Nullable private List<String> Entrypoint;
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

  public void setContainerEnvironment(List<String> environment) {
    config.Env = environment;
  }

  public void setContainerEntrypoint(List<String> command) {
    config.Entrypoint = command;
  }

  public void addLayerDiffId(DescriptorDigest diffId) {
    rootfs.diff_ids.add(diffId);
  }

  List<DescriptorDigest> getDiffIds() {
    return rootfs.diff_ids;
  }

  @Nullable
  List<String> getContainerEnvironment() {
    return config.Env;
  }

  @Nullable
  List<String> getContainerEntrypoint() {
    return config.Entrypoint;
  }

  @VisibleForTesting
  DescriptorDigest getLayerDiffId(int index) {
    return rootfs.diff_ids.get(index);
  }
}
