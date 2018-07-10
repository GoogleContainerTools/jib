/*
 * Copyright 2017 Google LLC. All rights reserved.
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
import com.google.cloud.tools.jib.configuration.Port;
import com.google.cloud.tools.jib.configuration.Port.Protocol;
import com.google.cloud.tools.jib.image.DescriptorDigest;
import com.google.cloud.tools.jib.json.JsonTemplate;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
 *     "Cmd": ["arg1", "arg2"]
 *     "ExposedPorts": { "6000/tcp":{}, "8000/tcp":{}, "9000/tcp":{} }
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

  /**
   * Pattern used for parsing information out of exposed port configurations. Only accepts single
   * ports with protocol.
   *
   * <p>Example matches: 100, 1000/tcp, 2000/udp
   */
  private static final Pattern portPattern = Pattern.compile("(\\d+)(?:/(tcp|udp))?");

  /**
   * A combined date and time at which the image was created. Constant to maintain reproducibility
   * and avoid Docker's weird "292 years old" bug.
   *
   * @see <a
   *     href="https://github.com/GoogleContainerTools/jib/issues/341">https://github.com/GoogleContainerTools/jib/issues/341</a>
   */
  private String created = "1970-01-01T00:00:00Z";

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

    /** Arguments to pass into main. */
    @Nullable private List<String> Cmd;

    /** Network ports the container exposes. */
    @Nullable private SortedMap<String, Map<?, ?>> ExposedPorts;
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

  public void setContainerCmd(List<String> cmd) {
    config.Cmd = cmd;
  }

  public void setContainerExposedPorts(List<Port> exposedPorts) {
    // TODO: Do this conversion somewhere else
    ImmutableSortedMap.Builder<String, Map<?, ?>> result =
        new ImmutableSortedMap.Builder<>(String::compareTo);
    for (Port port : exposedPorts) {
      result.put(port.getPort() + "/" + port.getProtocol(), Collections.emptyMap());
    }
    config.ExposedPorts = result.build();
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

  @Nullable
  List<String> getContainerCmd() {
    return config.Cmd;
  }

  @Nullable
  ImmutableList<Port> getContainerExposedPorts() {
    // TODO: Do this conversion somewhere else
    if (config.ExposedPorts == null) {
      return null;
    }
    ImmutableList.Builder<Port> ports = new ImmutableList.Builder<>();
    for (Map.Entry<String, Map<?, ?>> entry : config.ExposedPorts.entrySet()) {
      String port = entry.getKey();
      Matcher matcher = portPattern.matcher(port);
      if (!matcher.matches()) {
        throw new NumberFormatException("Invalid port configuration: '" + port + "'.");
      }

      int portNumber = Integer.parseInt(matcher.group(1));
      String protocol = matcher.group(2);
      ports.add(new Port(portNumber, "udp".equals(protocol) ? Protocol.UDP : Protocol.TCP));
    }
    return ports.build();
  }

  @VisibleForTesting
  DescriptorDigest getLayerDiffId(int index) {
    return rootfs.diff_ids.get(index);
  }
}
