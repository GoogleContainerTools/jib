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

package com.google.cloud.tools.jib.maven;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;

/** A simple verifier utility to test goal skipping across all our jib goals. */
class SkippedGoalVerifier {

  /** Verifies that a Jib goal is skipped with {@code jib.skip=true}. */
  static void verifyJibSkip(TestProject testProject, String goal)
      throws VerificationException, IOException {
    Verifier verifier = new Verifier(testProject.getProjectRoot().toString());
    verifier.setAutoclean(false);
    verifier.setSystemProperty("jib.skip", "true");

    verifier.executeGoal("jib:" + goal);

    Path logFile = Paths.get(verifier.getBasedir(), verifier.getLogFileName());
    MatcherAssert.assertThat(
        new String(Files.readAllBytes(logFile), StandardCharsets.UTF_8),
        CoreMatchers.containsString(
            "[INFO] Skipping containerization because jib-maven-plugin: skip = true\n"
                + "[INFO] ------------------------------------------------------------------------\n"
                + "[INFO] BUILD SUCCESS"));
  }

  /** Verifies that a Jib goal is skipped with {@code jib.containerize=noGroup:noArtifact}. */
  static void verifyJibContainerizeSkips(TestProject testProject, String goal)
      throws VerificationException, IOException {
    Verifier verifier = new Verifier(testProject.getProjectRoot().toString());
    verifier.setAutoclean(false);
    // noGroup:noArtifact should never match
    verifier.setSystemProperty("jib.containerize", "noGroup:noArtifact");

    verifier.executeGoal("jib:" + goal);

    Path logFile = Paths.get(verifier.getBasedir(), verifier.getLogFileName());
    MatcherAssert.assertThat(
        new String(Files.readAllBytes(logFile), StandardCharsets.UTF_8),
        CoreMatchers.containsString(
            "[INFO] Skipping containerization of this module (not specified in jib.containerize)\n"
                + "[INFO] ------------------------------------------------------------------------\n"
                + "[INFO] BUILD SUCCESS"));
  }

  private SkippedGoalVerifier() {}
}
