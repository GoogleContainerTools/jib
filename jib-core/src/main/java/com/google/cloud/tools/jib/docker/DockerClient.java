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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

/** Calls out to the {@code docker} CLI. */
public class DockerClient {

  /**
   * @param dockerSubCommand the subcommand to run after {@code docker}
   * @return the default {@link ProcessBuilder} factory for running a {@code docker} subcommand
   */
  private static ProcessBuilder defaultProcessBuilder(List<String> dockerSubCommand) {
    List<String> dockerCommand = new ArrayList<>(1 + dockerSubCommand.size());
    dockerCommand.add("docker");
    dockerCommand.addAll(dockerSubCommand);
    return new ProcessBuilder(dockerCommand);
  }

  /** Factory for generating the {@link ProcessBuilder} for running {@code docker} commands. */
  private final Function<List<String>, ProcessBuilder> processBuilderFactory;

  public DockerClient() {
    this(DockerClient::defaultProcessBuilder);
  }

  @VisibleForTesting
  DockerClient(Function<List<String>, ProcessBuilder> processBuilderFactory) {
    this.processBuilderFactory = processBuilderFactory;
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
   * @see <a
   *     href="https://docs.docker.com/engine/reference/commandline/load/">https://docs.docker.com/engine/reference/commandline/load</a>
   * @param imageTarballBlob the built container tarball.
   * @return stdout from {@code docker}.
   * @throws InterruptedException if the 'docker load' process is interrupted.
   * @throws IOException if streaming the blob to 'docker load' fails.
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
        throw new IOException("'docker load' command failed with output: " + output);
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
