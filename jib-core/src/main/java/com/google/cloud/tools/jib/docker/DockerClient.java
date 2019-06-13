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

package com.google.cloud.tools.jib.docker;

import com.google.cloud.tools.jib.api.ImageReference;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.CharStreams;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Calls out to the {@code docker} CLI. */
public class DockerClient {

  /** Builds a {@link DockerClient}. */
  public static class Builder {

    private Path dockerExecutable = DEFAULT_DOCKER_CLIENT;
    private ImmutableMap<String, String> dockerEnvironment = ImmutableMap.of();

    private Builder() {}

    /**
     * Sets a path for a {@code docker} executable.
     *
     * @param dockerExecutable path to {@code docker}
     * @return this
     */
    public Builder setDockerExecutable(Path dockerExecutable) {
      this.dockerExecutable = dockerExecutable;
      return this;
    }

    /**
     * Sets environment variables to use when executing the {@code docker} executable.
     *
     * @param dockerEnvironment environment variables for {@code docker}
     * @return this
     */
    public Builder setDockerEnvironment(ImmutableMap<String, String> dockerEnvironment) {
      this.dockerEnvironment = dockerEnvironment;
      return this;
    }

    public DockerClient build() {
      return new DockerClient(dockerExecutable, dockerEnvironment);
    }
  }

  /**
   * Gets a new {@link Builder} for {@link DockerClient} with defaults.
   *
   * @return a new {@link Builder}
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Instantiates with the default {@code docker} executable.
   *
   * @return a new {@link DockerClient}
   */
  public static DockerClient newDefaultClient() {
    return builder().build();
  }

  /**
   * Gets a function that takes a {@code docker} subcommand and gives back a {@link ProcessBuilder}
   * for that {@code docker} command.
   *
   * @param dockerExecutable path to {@code docker}
   * @return the default {@link ProcessBuilder} factory for running a {@code docker} subcommand
   */
  @VisibleForTesting
  static Function<List<String>, ProcessBuilder> defaultProcessBuilderFactory(
      String dockerExecutable, ImmutableMap<String, String> dockerEnvironment) {
    return dockerSubCommand -> {
      List<String> dockerCommand = new ArrayList<>(1 + dockerSubCommand.size());
      dockerCommand.add(dockerExecutable);
      dockerCommand.addAll(dockerSubCommand);

      ProcessBuilder processBuilder = new ProcessBuilder(dockerCommand);
      Map<String, String> environment = processBuilder.environment();
      environment.putAll(dockerEnvironment);

      return processBuilder;
    };
  }

  private static final Path DEFAULT_DOCKER_CLIENT = Paths.get("docker");

  /** Factory for generating the {@link ProcessBuilder} for running {@code docker} commands. */
  private final Function<List<String>, ProcessBuilder> processBuilderFactory;

  @VisibleForTesting
  DockerClient(Function<List<String>, ProcessBuilder> processBuilderFactory) {
    this.processBuilderFactory = processBuilderFactory;
  }

  /**
   * Instantiates with a {@code docker} executable and environment variables.
   *
   * @param dockerExecutable path to {@code docker}
   * @param dockerEnvironment environment variables for {@code docker}
   */
  private DockerClient(Path dockerExecutable, ImmutableMap<String, String> dockerEnvironment) {
    this(defaultProcessBuilderFactory(dockerExecutable.toString(), dockerEnvironment));
  }

  /**
   * Checks if Docker is installed on the user's system and accessible by running the default {@code
   * docker} command.
   *
   * @return {@code true} if Docker is installed on the user's system and accessible
   */
  public static boolean isDefaultDockerInstalled() {
    return isDockerInstalled(DEFAULT_DOCKER_CLIENT);
  }

  /**
   * Checks if Docker is installed on the user's system and accessible by running the given {@code
   * docker} executable.
   *
   * @param dockerExecutable path to the executable to test running
   * @return {@code true} if Docker is installed on the user's system and accessible
   */
  public static boolean isDockerInstalled(Path dockerExecutable) {
    try {
      new ProcessBuilder(dockerExecutable.toString()).start();
      return true;

    } catch (IOException ex) {
      return false;
    }
  }

  /**
   * Loads an image tarball into the Docker daemon.
   *
   * @see <a
   *     href="https://docs.docker.com/engine/reference/commandline/load/">https://docs.docker.com/engine/reference/commandline/load</a>
   * @param imageTarball the built container tarball.
   * @return stdout from {@code docker}.
   * @throws InterruptedException if the 'docker load' process is interrupted.
   * @throws IOException if streaming the blob to 'docker load' fails.
   */
  public String load(ImageTarball imageTarball) throws InterruptedException, IOException {
    // Runs 'docker load'.
    Process dockerProcess = docker("load");

    try (OutputStream stdin = dockerProcess.getOutputStream()) {
      try {
        imageTarball.writeTo(stdin);

      } catch (IOException ex) {
        // Tries to read from stderr.
        String error;
        try (InputStreamReader stderr =
            new InputStreamReader(dockerProcess.getErrorStream(), StandardCharsets.UTF_8)) {
          error = CharStreams.toString(stderr);

        } catch (IOException ignored) {
          // This ignores exceptions from reading stderr and throws the original exception from
          // writing to stdin.
          throw ex;
        }
        throw new IOException("'docker load' command failed with error: " + error, ex);
      }
    }

    try (InputStreamReader stdout =
        new InputStreamReader(dockerProcess.getInputStream(), StandardCharsets.UTF_8)) {
      String output = CharStreams.toString(stdout);

      if (dockerProcess.waitFor() != 0) {
        try (InputStreamReader stderr =
            new InputStreamReader(dockerProcess.getErrorStream(), StandardCharsets.UTF_8)) {
          throw new IOException(
              "'docker load' command failed with output: " + CharStreams.toString(stderr));
        }
      }

      return output;
    }
  }

  /**
   * Tags the image referenced by {@code originalImageReference} with a new image reference {@code
   * newImageReference}.
   *
   * @param originalImageReference the existing image reference on the Docker daemon
   * @param newImageReference the new image reference
   * @see <a
   *     href="https://docs.docker.com/engine/reference/commandline/tag/">https://docs.docker.com/engine/reference/commandline/tag/</a>
   * @throws InterruptedException if the 'docker tag' process is interrupted.
   * @throws IOException if an I/O exception occurs or {@code docker tag} failed
   */
  public void tag(ImageReference originalImageReference, ImageReference newImageReference)
      throws IOException, InterruptedException {
    // Runs 'docker tag'.
    Process dockerProcess =
        docker("tag", originalImageReference.toString(), newImageReference.toString());

    if (dockerProcess.waitFor() != 0) {
      try (InputStreamReader stderr =
          new InputStreamReader(dockerProcess.getErrorStream(), StandardCharsets.UTF_8)) {
        throw new IOException(
            "'docker tag' command failed with error: " + CharStreams.toString(stderr));
      }
    }
  }

  /** Runs a {@code docker} command. */
  private Process docker(String... subCommand) throws IOException {
    return processBuilderFactory.apply(Arrays.asList(subCommand)).start();
  }
}
