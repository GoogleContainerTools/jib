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

  /**
   * Builds and runs jib:buildDocker on a project at {@code projectRoot} pushing to {@code
   * imageReference}.
   */
  private static String buildToDockerDaemonAndRun(Path projectRoot, String imageReference)
      throws VerificationException, IOException, InterruptedException {
    Verifier verifier = new Verifier(projectRoot.toString());
    verifier.setAutoclean(false);
    verifier.executeGoal("package");

    verifier.executeGoal("jib:" + BuildDockerMojo.GOAL_NAME);
    verifier.verifyErrorFreeLog();

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
    Instant before = Instant.now();
    Assert.assertEquals(
        "Hello, world. An argument.\nfoo\ncat\n",
        buildToDockerDaemonAndRun(
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
        buildToDockerDaemonAndRun(
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
  public void testExecute_defaultTarget()
      throws VerificationException, IOException, InterruptedException {
    Assert.assertEquals(
        "Hello, world. An argument.\n",
        buildToDockerDaemonAndRun(
            defaultTargetTestProject.getProjectRoot(),
            "default-target-name:default-target-version"));
  }
}
