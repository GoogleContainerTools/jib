/*
 * Copyright 2018 Google Inc.
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

package com.google.cloud.tools.jib.registry;

import com.google.common.io.CharStreams;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;
import org.junit.rules.ExternalResource;
import org.junit.rules.TestRule;

/** {@link TestRule} that runs a local registry. */
public class LocalRegistry extends ExternalResource {

  private final int port;

  /** The name for the container running the registry. */
  private final String containerName = "registry-" + UUID.randomUUID();

  public LocalRegistry(int port) {
    this.port = port;
  }

  /** Starts the local registry. */
  @Override
  protected void before() throws Throwable {
    // Runs the Docker registry.
    runCommand(
        "docker",
        "run",
        "-d",
        "-p",
        port + ":5000",
        "--restart=always",
        "--name",
        containerName,
        "registry:2");

    // Pulls 'busybox'.
    runCommand("docker", "pull", "busybox");

    // Tags 'busybox' to push to our local registry.
    runCommand("docker", "tag", "busybox", "localhost:" + port + "/busybox");

    // Pushes 'busybox' to our local registry.
    runCommand("docker", "push", "localhost:" + port + "/busybox");
  }

  /** Stops the local registry. */
  @Override
  protected void after() {
    try {
      // Stops the registry.
      runCommand("docker", "stop", containerName);

      // Removes the container.
      runCommand("docker", "rm", "-v", containerName);

    } catch (InterruptedException | IOException ex) {
      throw new RuntimeException("Could not stop local registry fully: " + containerName, ex);
    }
  }

  /** Runs a command with naive tokenization by whitespace. */
  private void runCommand(String... command) throws IOException, InterruptedException {
    if (new ProcessBuilder(Arrays.asList(command)).start().waitFor() != 0) {
      throw new IOException("Command '" + String.join(" ", command) + "' failed");
    }
  }

  private void printLogs() throws IOException, InterruptedException {
    Process process = Runtime.getRuntime().exec("docker logs " + containerName);
    try (InputStreamReader inputStreamReader =
        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)) {
      System.out.println(CharStreams.toString(inputStreamReader));
    }
    try (InputStreamReader inputStreamReader =
        new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8)) {
      System.err.println(CharStreams.toString(inputStreamReader));
    }
    process.waitFor();
  }
}
