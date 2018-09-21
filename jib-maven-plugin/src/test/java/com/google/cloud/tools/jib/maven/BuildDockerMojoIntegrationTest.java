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
import java.nio.file.Path;
import java.time.Instant;
import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;

/** Integration tests for {@link BuildDockerMojo}. */
public class BuildDockerMojoIntegrationTest {

  @ClassRule public static final TestPlugin testPlugin = new TestPlugin();

  @ClassRule
  public static final TestProject simpleTestProject = new TestProject(testPlugin, "simple");

  @ClassRule
  public static final TestProject emptyTestProject = new TestProject(testPlugin, "empty");

  @ClassRule
  public static final TestProject defaultTargetTestProject =
      new TestProject(testPlugin, "default-target");

  @ClassRule
  public static final TestProject skippedTestProject = new TestProject(testPlugin, "empty");

  /**
   * Builds and runs jib:buildDocker on a project at {@code projectRoot} pushing to {@code
   * imageReference}.
   */
  private static String buildToDockerDaemonAndRun(Path projectRoot, String imageReference)
      throws VerificationException, IOException, InterruptedException {
    Verifier verifier = new Verifier(projectRoot.toString());
    verifier.setSystemProperty("_TARGET_IMAGE", imageReference);
    verifier.setAutoclean(false);
    verifier.executeGoal("package");

    verifier.executeGoal("jib:" + BuildDockerMojo.GOAL_NAME);
    verifier.verifyErrorFreeLog();

    String dockerInspect = new Command("docker", "inspect", imageReference).run();
    Assert.assertThat(
        dockerInspect,
        CoreMatchers.containsString(
            "            \"ExposedPorts\": {\n"
                + "                \"1000/tcp\": {},\n"
                + "                \"2000/udp\": {},\n"
                + "                \"2001/udp\": {},\n"
                + "                \"2002/udp\": {},\n"
                + "                \"2003/udp\": {}"));
    Assert.assertThat(
        dockerInspect,
        CoreMatchers.containsString(
            "            \"Labels\": {\n"
                + "                \"key1\": \"value1\",\n"
                + "                \"key2\": \"value2\"\n"
                + "            }"));
    return new Command("docker", "run", "--rm", imageReference).run();
  }

  @Test
  public void testExecute_simple() throws VerificationException, IOException, InterruptedException {
    String targetImage = "simpleimage:maven" + System.nanoTime();

    Instant before = Instant.now();
    Assert.assertEquals(
        "Hello, world. An argument.\nfoo\ncat\n",
        buildToDockerDaemonAndRun(simpleTestProject.getProjectRoot(), targetImage));
    Instant buildTime =
        Instant.parse(
            new Command("docker", "inspect", "-f", "{{.Created}}", targetImage).run().trim());
    Assert.assertTrue(buildTime.isAfter(before) || buildTime.equals(before));
  }

  @Test
  public void testExecute_empty() throws InterruptedException, IOException, VerificationException {
    String targetImage = "emptyimage:maven" + System.nanoTime();

    Assert.assertEquals(
        "", buildToDockerDaemonAndRun(emptyTestProject.getProjectRoot(), targetImage));
    Assert.assertEquals(
        "1970-01-01T00:00:00Z",
        new Command("docker", "inspect", "-f", "{{.Created}}", targetImage).run().trim());
  }

  @Test
  public void testExecute_defaultTarget()
      throws VerificationException, IOException, InterruptedException {
    Assert.assertEquals(
        "Hello, world. An argument.\n",
        buildToDockerDaemonAndRun(
            defaultTargetTestProject.getProjectRoot(),
            "default-target-name:default-target-version"));
  }

  @Test
  public void testExecute_skipJibGoal() throws VerificationException, IOException {
    SkippedGoalVerifier.verifyGoalIsSkipped(skippedTestProject, BuildDockerMojo.GOAL_NAME);
  }
}
