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

package com.google.cloud.tools.jib.builder;

import com.google.common.io.CharStreams;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Test utility for running an image with Docker. */
public class DockerImageRunner {

  /** Runs a command with naive tokenization by whitespace. */
  private static String runCommand(String... command) throws IOException, InterruptedException {
    Process process = new ProcessBuilder(Arrays.asList(command)).start();

    try (InputStreamReader inputStreamReader =
        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)) {
      String output = CharStreams.toString(inputStreamReader);

      if (process.waitFor() != 0) {
        throw new IOException("Command '" + String.join(" ", command) + "' failed");
      }

      return output;
    }
  }

  private final String imageReference;

  public DockerImageRunner(String imageReference) {
    this.imageReference = imageReference;
  }

  /** Pulls and runs the image and returns the output. */
  public String run() throws IOException, InterruptedException {
    runCommand("docker", "pull", imageReference);
    return runCommand("docker", "run", imageReference);
  }
}
