/*
 * Copyright 2018 Google LLC. All rights reserved.
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
   * @return {@code true} if Docker is installed on the user's system and accessible as {@code
   *     docker}
   */
  public static boolean isDockerInstalled() {
    try {
      new ProcessBuilder("docker").start();
      return true;

    } catch (IOException ex) {
      return false;
    }
  }

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
   * Loads an image tarball into the Docker daemon.
   *
   * @see <a
   *     href="https://docs.docker.com/engine/reference/commandline/load/">https://docs.docker.com/engine/reference/commandline/load/</a>
   * @return stdout from {@code docker}
   */
  public String load(Blob imageTarballBlob) throws IOException, InterruptedException {
    Process dockerProcess = docker("load");

    try (OutputStream stdin = dockerProcess.getOutputStream()) {
      imageTarballBlob.writeTo(stdin);
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

  /** Runs a {@code docker} command. */
  private Process docker(String... subCommand) throws IOException {
    return processBuilderFactory.apply(Arrays.asList(subCommand)).start();
  }
}
