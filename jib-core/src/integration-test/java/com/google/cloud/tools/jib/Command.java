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

package com.google.cloud.tools.jib;

import com.google.common.io.CharStreams;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Nullable;

/** Test utility to run shell commands for integration tests. */
public class Command {

  private final List<String> command;
  private Path workingDir = null;

  /** Instantiate with a command. */
  public Command(String... command) {
    this.command = Arrays.asList(command);
  }

  /** Instantiate with a command. */
  public Command(List<String> command) {
    this.command = command;
  }

  public Command setWorkingDir(Path workingDir) {
    this.workingDir = workingDir;
    return this;
  }

  /** Runs the command. */
  public String run() throws IOException, InterruptedException {
    return run(null);
  }

  /** Runs the command and pipes in {@code stdin}. */
  public String run(@Nullable byte[] stdin) throws IOException, InterruptedException {
    System.out.println("Running command: " + command);
    ProcessBuilder processBuilder = new ProcessBuilder(command);
    if (workingDir != null) {
      processBuilder.directory(workingDir.toFile());
    }
    Process process = processBuilder.start();

    if (stdin != null) {
      // Write out stdin.
      try (OutputStream outputStream = process.getOutputStream()) {
        outputStream.write(stdin);
      }
    }

    // Read in stdout.
    try (InputStreamReader inputStreamReader =
        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)) {
      String output = CharStreams.toString(inputStreamReader);

      if (process.waitFor() != 0) {
        String stderr =
            CharStreams.toString(
                new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8));
        throw new RuntimeException("Command '" + String.join(" ", command) + "' failed: " + stderr);
      }

      //      System.out.println("Command: " + command + ", output: " + output);
      return output;
    }
  }
}
