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

package com.google.cloud.tools.jib.maven;

import com.google.common.io.CharStreams;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/** Integration tests for {@link BuildImageMojo}. */
public class BuildImageMojoIT {

  @ClassRule public static final TestPlugin testPlugin = new TestPlugin();

  @Rule public final TestProject testProject = new TestProject(testPlugin);

  @Test
  public void testExecute() throws VerificationException, IOException, InterruptedException {
    Verifier verifier = new Verifier(testProject.getProjectRoot().toString());
    verifier.setAutoclean(false);
    verifier.executeGoal("package");

    // Builds twice, and checks if the second build took less time.
    long lastTime = System.nanoTime();
    verifier.executeGoal("jib:build");
    long timeOne = System.nanoTime() - lastTime;
    lastTime = System.nanoTime();

    verifier.executeGoal("jib:build");
    long timeTwo = System.nanoTime() - lastTime;

    verifier.verifyErrorFreeLog();

    Assert.assertTrue(timeOne > timeTwo);

    // Checks that the built image outputs what was intended.
    runCommand("docker", "pull", "gcr.io/qingyangc-sandbox/jibtestimage:built-with-jib");

    // TODO: Put this in a utility function.
    Process process =
        Runtime.getRuntime()
            .exec("docker run gcr.io/qingyangc-sandbox/jibtestimage:built-with-jib");
    try (InputStreamReader inputStreamReader =
        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)) {
      String output = CharStreams.toString(inputStreamReader);
      Assert.assertEquals("Hello, world\n", output);
    }
    process.waitFor();
  }

  /** Runs a command with naive tokenization by whitespace. */
  private void runCommand(String... command) throws IOException, InterruptedException {
    if (new ProcessBuilder(Arrays.asList(command)).start().waitFor() != 0) {
      throw new IOException("Command '" + String.join(" ", command) + "' failed");
    }
  }
}
