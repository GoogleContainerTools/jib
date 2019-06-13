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

import com.google.cloud.tools.jib.Command;
import java.io.IOException;
import java.security.DigestException;
import java.time.Instant;
import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;

public class BuildTarMojoIntegrationTest {

  @ClassRule public static final TestPlugin testPlugin = new TestPlugin();

  @ClassRule
  public static final TestProject simpleTestProject = new TestProject(testPlugin, "simple");

  @ClassRule
  public static final TestProject skippedTestProject = new TestProject(testPlugin, "empty");

  /**
   * Builds and runs jib:buildTar on a project at {@code projectRoot} pushing to {@code
   * imageReference}.
   *
   * @throws DigestException
   */
  @Test
  public void testExecute_simple()
      throws VerificationException, IOException, InterruptedException, DigestException {
    String targetImage = "simpleimage:maven" + System.nanoTime();

    Instant before = Instant.now();
    Verifier verifier = new Verifier(simpleTestProject.getProjectRoot().toString());
    verifier.setSystemProperty("jib.useOnlyProjectCache", "true");
    verifier.setSystemProperty("_TARGET_IMAGE", targetImage);
    verifier.setAutoclean(false);
    verifier.executeGoal("package");

    verifier.executeGoal("jib:" + BuildTarMojo.GOAL_NAME);
    verifier.verifyErrorFreeLog();

    BuildImageMojoIntegrationTest.assertImageDigest(simpleTestProject.getProjectRoot());

    new Command(
            "docker",
            "load",
            "--input",
            simpleTestProject
                .getProjectRoot()
                .resolve("target")
                .resolve("jib-image.tar")
                .toString())
        .run();
    Assert.assertEquals(
        "Hello, world. An argument.\nrw-r--r--\nrw-r--r--\nfoo\ncat\n",
        new Command("docker", "run", "--rm", targetImage).run());

    Instant buildTime =
        Instant.parse(
            new Command("docker", "inspect", "-f", "{{.Created}}", targetImage).run().trim());
    Assert.assertTrue(buildTime.isAfter(before) || buildTime.equals(before));
  }

  @Test
  public void testExecute_jibSkip() throws VerificationException, IOException {
    SkippedGoalVerifier.verifyJibSkip(skippedTestProject, BuildTarMojo.GOAL_NAME);
  }

  @Test
  public void testExecute_jibContainerizeSkips() throws VerificationException, IOException {
    SkippedGoalVerifier.verifyJibContainerizeSkips(simpleTestProject, BuildDockerMojo.GOAL_NAME);
  }
}
