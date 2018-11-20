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

package com.google.cloud.tools.jib.configuration;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;

/** Configuration information for performing healthchecks on a Docker container. */
public class DockerHealthCheck {

  /** Builds the immutable {@link DockerHealthCheck}. */
  public static class Builder {

    @Nullable private final ImmutableList<String> command;
    @Nullable private Duration interval;
    @Nullable private Duration timeout;
    @Nullable private Duration startPeriod;
    @Nullable private Integer retries;

    private Builder(@Nullable ImmutableList<String> command) {
      this.command = command;
    }

    /**
     * Sets the time between healthchecks.
     *
     * @param interval the duration to wait between healthchecks.
     * @return this
     */
    public Builder setInterval(Duration interval) {
      this.interval = interval;
      return this;
    }

    /**
     * Sets the time until a healthcheck is considered hung.
     *
     * @param timeout the duration to wait until considering the healthcheck to be hung.
     * @return this
     */
    public Builder setTimeout(Duration timeout) {
      this.timeout = timeout;
      return this;
    }

    /**
     * Sets the initialization time to wait before using healthchecks.
     *
     * @param startPeriod the duration to wait before using healthchecks
     * @return this
     */
    public Builder setStartPeriod(Duration startPeriod) {
      this.startPeriod = startPeriod;
      return this;
    }

    /**
     * Sets the number of times to retry the healthcheck before the container is considered to be
     * unhealthy.
     *
     * @param retries the number of retries before the container is considered to be unhealthy
     * @return this
     */
    public Builder setRetries(int retries) {
      this.retries = retries;
      return this;
    }

    public DockerHealthCheck build() {
      return new DockerHealthCheck(command, interval, timeout, startPeriod, retries);
    }
  }

  /**
   * Creates a disabled {@link DockerHealthCheck} (corresponds to "NONE" in container config).
   *
   * @return the new {@link DockerHealthCheck}
   */
  public static DockerHealthCheck disabled() {
    return new DockerHealthCheck(ImmutableList.of("NONE"), null, null, null, null);
  }

  /**
   * Creates a new {@link DockerHealthCheck.Builder} with the command set to be inherited from the
   * base image (corresponds to empty list in container config).
   *
   * @return the new {@link DockerHealthCheck.Builder}
   */
  public static Builder builderWithInheritedCommand() {
    return new Builder(null);
  }

  /**
   * Creates a new {@link DockerHealthCheck.Builder} with the specified healthcheck command to be
   * directly executed (corresponds to "CMD" in container config).
   *
   * @param command the healthcheck command to execute
   * @return the new {@link DockerHealthCheck.Builder}
   */
  public static Builder builderWithExecCommand(List<String> command) {
    Preconditions.checkArgument(command.size() > 0, "command must not be empty");
    return new Builder(ImmutableList.<String>builder().add("CMD").addAll(command).build());
  }

  /**
   * Creates a new {@link DockerHealthCheck.Builder} with the specified healthcheck command to be
   * directly executed (corresponds to "CMD" in container config).
   *
   * @param command the healthcheck command to execute
   * @return the new {@link DockerHealthCheck.Builder}
   */
  public static Builder builderWithExecCommand(String... command) {
    Preconditions.checkArgument(command.length > 0, "command must not be empty");
    return new Builder(
        ImmutableList.<String>builder().add("CMD").addAll(Arrays.asList(command)).build());
  }

  /**
   * Creates a new {@link DockerHealthCheck.Builder} with the specified healthcheck command to be
   * run by the container's default shell (corresponds to "CMD-SHELL" in container config). This
   * command cannot be run on containers with no default shell.
   *
   * @param command the shell command to run
   * @return the new {@link DockerHealthCheck.Builder}
   */
  public static Builder builderWithShellCommand(String command) {
    Preconditions.checkArgument(command.length() > 0, "command must not be empty");
    return new Builder(ImmutableList.of("CMD-SHELL", command));
  }

  @Nullable private final ImmutableList<String> command;
  @Nullable private final Duration interval;
  @Nullable private final Duration timeout;
  @Nullable private final Duration startPeriod;
  @Nullable private final Integer retries;

  private DockerHealthCheck(
      @Nullable ImmutableList<String> command,
      @Nullable Duration interval,
      @Nullable Duration timeout,
      @Nullable Duration startPeriod,
      @Nullable Integer retries) {
    this.command = command;
    this.interval = interval;
    this.timeout = timeout;
    this.startPeriod = startPeriod;
    this.retries = retries;
  }

  /**
   * Gets the optional healthcheck command. A missing command means that it will be inherited from
   * the base image.
   *
   * @return the healthcheck command
   */
  public Optional<List<String>> getCommand() {
    return Optional.ofNullable(command);
  }

  /**
   * Gets the optional healthcheck interval. A missing command means that it will be inherited from
   * the base image.
   *
   * @return the healthcheck interval
   */
  public Optional<Duration> getInterval() {
    return Optional.ofNullable(interval);
  }

  /**
   * Gets the optional healthcheck timeout. A missing command means that it will be inherited from
   * the base image.
   *
   * @return the healthcheck timeout
   */
  public Optional<Duration> getTimeout() {
    return Optional.ofNullable(timeout);
  }

  /**
   * Gets the optional healthcheck start period. A missing command means that it will be inherited
   * from the base image.
   *
   * @return the healthcheck start period
   */
  public Optional<Duration> getStartPeriod() {
    return Optional.ofNullable(startPeriod);
  }

  /**
   * Gets the optional healthcheck retry count. A missing command means that it will be inherited
   * from the base image.
   *
   * @return the healthcheck retry count
   */
  public Optional<Integer> getRetries() {
    return Optional.ofNullable(retries);
  }

  /**
   * Returns {@code true} if the health check command is set (i.e. intended to be inherited), {@code
   * false} if not.
   *
   * @return {@code true} if the health check command is set, {@code false} if not
   */
  public boolean hasCommand() {
    return command != null && !command.isEmpty();
  }
}
