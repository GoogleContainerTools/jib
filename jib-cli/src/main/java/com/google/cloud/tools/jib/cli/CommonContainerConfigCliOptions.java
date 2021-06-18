/*
 * Copyright 2021 Google LLC.
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

package com.google.cloud.tools.jib.cli;

import com.google.cloud.tools.jib.api.ImageReference;
import com.google.cloud.tools.jib.api.InvalidImageReferenceException;
import com.google.cloud.tools.jib.api.Ports;
import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.buildplan.ImageFormat;
import com.google.cloud.tools.jib.api.buildplan.Port;
import com.google.common.collect.ImmutableSet;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import picocli.CommandLine;

/** Common command line options shared between jar and war command. */
public class CommonContainerConfigCliOptions {

  @CommandLine.Option(
      names = "--from",
      paramLabel = "<base-image>",
      description = "The base image to use.")
  @SuppressWarnings("NullAway.Init") // initialized by picocli
  private String from;

  @CommandLine.Option(
      names = "--expose",
      paramLabel = "<port>",
      split = ",",
      description = "Ports to expose on container, example: --expose=5000,7/udp.")
  private List<String> exposedPorts = Collections.emptyList();

  @CommandLine.Option(
      names = "--volumes",
      paramLabel = "<volume>",
      split = ",",
      description =
          "Directories on container to hold extra volumes,  example: --volumes=/var/log,/var/log2.")
  private List<String> volumes = Collections.emptyList();

  @CommandLine.Option(
      names = "--environment-variables",
      paramLabel = "<key>=<value>",
      split = ",",
      description =
          "Environment variables to write into container, example: --environment-variables env1=env_value1,env2=env_value2.")
  private Map<String, String> environment = Collections.emptyMap();

  @CommandLine.Option(
      names = "--labels",
      paramLabel = "<key>=<value>",
      split = ",",
      description =
          "Labels to write into container metadata, example: --labels=label1=value1,label2=value2.")
  private Map<String, String> labels = Collections.emptyMap();

  @CommandLine.Option(
      names = {"-u", "--user"},
      paramLabel = "<user>",
      description = "The user to run the container as, example: --user=myuser:mygroup.")
  @SuppressWarnings("NullAway.Init") // initialized by picocli
  private String user;

  @CommandLine.Option(
      names = {"--image-format"},
      defaultValue = "Docker",
      paramLabel = "<image-format>",
      description =
          "Format of container, candidates: ${COMPLETION-CANDIDATES}, default: ${DEFAULT-VALUE}.")
  @SuppressWarnings("NullAway.Init") // initialized by picocli
  private ImageFormat format;

  @CommandLine.Option(
      names = "--program-args",
      paramLabel = "<program-argument>",
      split = ",",
      description = "Program arguments for container entrypoint.")
  private List<String> programArguments = Collections.emptyList();

  @CommandLine.Option(
      names = "--entrypoint",
      paramLabel = "<entrypoint>",
      split = "\\s+",
      description =
          "Entrypoint for container. Overrides the default entrypoint, example: --entrypoint='custom entrypoint'")
  private List<String> entrypoint = Collections.emptyList();

  @CommandLine.Option(
      names = "--creation-time",
      paramLabel = "<creation-time>",
      description =
          "The creation time of the container in milliseconds since epoch or iso8601 format. Overrides the default (1970-01-01T00:00:00Z)")
  @SuppressWarnings("NullAway.Init") // initialized by picocli
  private String creationTime;

  /**
   * Returns the user-specified base image.
   *
   * @return an optional base image
   */
  public Optional<String> getFrom() {
    return Optional.ofNullable(from);
  }

  /**
   * Returns set of {@link Port} representing ports to be exposed on container (if specified).
   *
   * @return set of exposed ports
   */
  public Set<Port> getExposedPorts() {
    return (exposedPorts == null) ? ImmutableSet.of() : Ports.parse(exposedPorts);
  }

  /**
   * Returns a set of {@link AbsoluteUnixPath} representing directories on container to hold volumes
   * (if specified).
   *
   * @return set of volumes
   */
  public Set<AbsoluteUnixPath> getVolumes() {
    if (volumes == null) {
      return ImmutableSet.of();
    }
    return volumes.stream().map(AbsoluteUnixPath::get).collect(Collectors.toSet());
  }

  public Map<String, String> getEnvironment() {
    return environment;
  }

  public Map<String, String> getLabels() {
    return labels;
  }

  public Optional<String> getUser() {
    return Optional.ofNullable(user);
  }

  public Optional<ImageFormat> getFormat() {
    return Optional.ofNullable(format);
  }

  public List<String> getProgramArguments() {
    return programArguments;
  }

  public List<String> getEntrypoint() {
    return entrypoint;
  }

  /**
   * Returns {@link Instant} representing creation time of container.
   *
   * @return an optional creation time
   */
  public Optional<Instant> getCreationTime() {
    if (creationTime != null) {
      return Optional.of(Instants.fromMillisOrIso8601(creationTime, "creationTime"));
    }
    return Optional.empty();
  }

  /**
   * Returns {@code true} if the user-specified base image is jetty or if a custom base image is not
   * specified.
   *
   * @return a boolean
   * @throws InvalidImageReferenceException if image reference is invalid
   */
  public Boolean isJetty() throws InvalidImageReferenceException {
    if (from != null) {
      ImageReference baseImageReference = ImageReference.parse(from);
      return baseImageReference.getRegistry().equals("registry-1.docker.io")
          && baseImageReference.getRepository().equals("library/jetty");
    }
    return true;
  }
}
