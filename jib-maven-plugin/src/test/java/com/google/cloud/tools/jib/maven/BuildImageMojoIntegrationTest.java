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

package com.google.cloud.tools.jib.maven;

import com.google.cloud.tools.jib.Command;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;

/** Integration tests for {@link BuildImageMojo}. */
public class BuildImageMojoIntegrationTest {

  @ClassRule public static final TestPlugin testPlugin = new TestPlugin();

  @ClassRule
  public static final TestProject simpleTestProject = new TestProject(testPlugin, "simple");

  @ClassRule
  public static final TestProject emptyTestProject = new TestProject(testPlugin, "empty");

  @ClassRule
  public static final TestProject defaultTargetTestProject =
      new TestProject(testPlugin, "default-target");

  /**
   * Builds and runs jib:build on a project at {@code projectRoot} pushing to {@code
   * imageReference}.
   */
  private static String buildAndRun(Path projectRoot, String imageReference)
      throws VerificationException, IOException, InterruptedException {
    Verifier verifier = new Verifier(projectRoot.toString());
    verifier.setAutoclean(false);
    verifier.executeGoal("package");

    // Builds twice, and checks if the second build took less time.
    long lastTime = System.nanoTime();
    verifier.executeGoal("jib:" + BuildImageMojo.GOAL_NAME);
    long timeOne = System.nanoTime() - lastTime;
    lastTime = System.nanoTime();

    verifier.executeGoal("jib:" + BuildImageMojo.GOAL_NAME);
    long timeTwo = System.nanoTime() - lastTime;

    verifier.verifyErrorFreeLog();

    Assert.assertTrue(
        "First build time ("
            + timeOne
            + ") is not greater than second build time ("
            + timeTwo
            + ")",
        timeOne > timeTwo);

    new Command("docker", "pull", imageReference).run();
    Assert.assertThat(
        new Command("docker", "inspect", imageReference).run(),
        CoreMatchers.containsString(
            "            \"ExposedPorts\": {\n"
                + "                \"1000/tcp\": {},\n"
                + "                \"2000/udp\": {},\n"
                + "                \"2001/udp\": {},\n"
                + "                \"2002/udp\": {},\n"
                + "                \"2003/udp\": {}"));
    return new Command("docker", "run", imageReference).run();
  }

  @Test
  public void testExecute_simple() throws VerificationException, IOException, InterruptedException {
    // Test empty output error
    try {
      Verifier verifier = new Verifier(simpleTestProject.getProjectRoot().toString());
      verifier.setAutoclean(false);
      verifier.executeGoals(Arrays.asList("clean", "jib:" + BuildImageMojo.GOAL_NAME));
      Assert.fail();
    } catch (VerificationException ex) {
      Assert.assertThat(
          ex.getMessage(),
          CoreMatchers.containsString(
              "Obtaining project build output files failed; make sure you have compiled your "
                  + "project before trying to build the image. (Did you accidentally run \"mvn "
                  + "clean jib:build\" instead of \"mvn clean compile jib:build\"?)"));
    }

    Instant before = Instant.now();
    Assert.assertEquals(
        "Hello, world. An argument.\nfoo\ncat\n",
        buildAndRun(
            simpleTestProject.getProjectRoot(),
            "gcr.io/jib-integration-testing/simpleimage:maven"));

    Instant buildTime =
        Instant.parse(
            new Command(
                    "docker",
                    "inspect",
                    "-f",
                    "{{.Created}}",
                    "gcr.io/jib-integration-testing/simpleimage:maven")
                .run()
                .trim());
    Assert.assertTrue(buildTime.isAfter(before) || buildTime.equals(before));
  }

  @Test
  public void testExecute_empty() throws InterruptedException, IOException, VerificationException {
    Assert.assertEquals(
        "",
        buildAndRun(
            emptyTestProject.getProjectRoot(), "gcr.io/jib-integration-testing/emptyimage:maven"));
    Assert.assertEquals(
        "1970-01-01T00:00:00Z",
        new Command(
                "docker",
                "inspect",
                "-f",
                "{{.Created}}",
                "gcr.io/jib-integration-testing/emptyimage:maven")
            .run()
            .trim());
  }

  @Test
  public void testExecute_defaultTarget() {
    // Test error when 'to' is missing
    try {
      Verifier verifier = new Verifier(defaultTargetTestProject.getProjectRoot().toString());
      verifier.setAutoclean(false);
      verifier.executeGoals(Arrays.asList("clean", "jib:" + BuildImageMojo.GOAL_NAME));
      Assert.fail();
    } catch (VerificationException ex) {
      Assert.assertThat(
          ex.getMessage(),
          CoreMatchers.containsString(
              "Missing target image parameter, perhaps you should add a <to><image> configuration "
                  + "parameter to your pom.xml or set the parameter via the commandline (e.g. 'mvn "
                  + "compile jib:build -Dimage=<your image name>')."));
    }
  }
}
