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

import com.google.cloud.tools.jib.blob.Blob;
import com.google.cloud.tools.jib.image.ImageReference;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.io.CharStreams;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Calls out to the {@code docker} CLI. */
public class DockerClient {

  private static final String DEFAULT_DOCKER_CLIENT = "docker";
  private static final Map<String, String> DEFAULT_DOCKER_ENVIRONMENT = Collections.emptyMap();

  /**
   * Instantiates with the default {@code docker} executable.
   *
   * @return a new {@link DockerClient}
   */
  public static DockerClient newClient() {
    return new DockerClient(
        defaultProcessBuilderFactory(DEFAULT_DOCKER_CLIENT, DEFAULT_DOCKER_ENVIRONMENT));
  }

  /**
   * Instantiates with a custom {@code docker} executable.
   *
   * @param dockerExecutable path to {@code docker}
   * @return a new {@link DockerClient}
   */
  public static DockerClient newClient(Path dockerExecutable) {
    return new DockerClient(
        defaultProcessBuilderFactory(dockerExecutable.toString(), DEFAULT_DOCKER_ENVIRONMENT));
  }

  /**
   * Instantiates with a custom {@code docker} executable.
   *
   * @param dockerExecutable path to {@code docker}
   * @param dockerEnvironment environment variables for {@code docker}
   * @return a new {@link DockerClient}
   */
  public static DockerClient newClient(
      Path dockerExecutable, Map<String, String> dockerEnvironment) {
    return new DockerClient(
        defaultProcessBuilderFactory(dockerExecutable.toString(), dockerEnvironment));
  }

  /**
   * Gets a function that takes a {@code docker} subcommand and gives back a {@link ProcessBuilder}
   * for that {@code docker} command.
   *
   * @param dockerExecutable path to {@code docker}
   * @return the default {@link ProcessBuilder} factory for running a {@code docker} subcommand
   */
  private static Function<List<String>, ProcessBuilder> defaultProcessBuilderFactory(
      String dockerExecutable, Map<String, String> dockerEnvironment) {
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

  /** Factory for generating the {@link ProcessBuilder} for running {@code docker} commands. */
  private final Function<List<String>, ProcessBuilder> processBuilderFactory;

  @VisibleForTesting
  DockerClient(Function<List<String>, ProcessBuilder> processBuilderFactory) {
    this.processBuilderFactory = processBuilderFactory;
  }

  @VisibleForTesting
  public Function<List<String>, ProcessBuilder> getProcessBuilderFactory() {
    return processBuilderFactory;
  }

  /**
   * @return {@code true} if Docker is installed on the user's system and accessible as {@code
   *     docker}
   */
  public boolean isDockerInstalled() {
    try {
      docker();
      return true;

    } catch (IOException ex) {
      return false;
    }
  }

  /**
   * Loads an image tarball into the Docker daemon.
   *
   * @param imageTarballBlob the built container tarball.
   * @return stdout from {@code docker}.
   * @throws InterruptedException if the 'docker load' process is interrupted.
   * @throws IOException if streaming the blob to 'docker load' fails.
   * @see <a
   *     href="https://docs.docker.com/engine/reference/commandline/load/">https://docs.docker.com/engine/reference/commandline/load</a>
   */
  public String load(Blob imageTarballBlob) throws InterruptedException, IOException {
    // Runs 'docker load'.
    Process dockerProcess = docker("load");

    try (OutputStream stdin = dockerProcess.getOutputStream()) {
      try {
        imageTarballBlob.writeTo(stdin);

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
   * @throws InterruptedException if the 'docker tag' process is interrupted.
   * @throws IOException if an I/O exception occurs or {@code docker tag} failed
   * @see <a
   *     href="https://docs.docker.com/engine/reference/commandline/tag/">https://docs.docker.com/engine/reference/commandline/tag/</a>
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
