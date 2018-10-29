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

package com.google.cloud.tools.jib.gradle;

import com.google.cloud.tools.jib.Command;
import java.io.IOException;
import java.security.DigestException;
import org.gradle.testkit.runner.UnexpectedBuildFailure;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;

/** Integration tests for building "default-target" project images. */
public class DefaultTargetProjectIntegrationTest {

  @ClassRule
  public static final TestProject defaultTargetTestProject = new TestProject("default-target");

  /**
   * Asserts that the test project has the required exposed ports and labels.
   *
   * @param imageReference the image to test
   * @throws IOException if the {@code docker inspect} command fails to run
   * @throws InterruptedException if the {@code docker inspect} command is interrupted
   */
  private static void assertDockerInspect(String imageReference)
      throws IOException, InterruptedException {
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
  }

  @Test
  public void testBuild_defaultTarget() {
    // Test error when 'to' is missing
    try {
      defaultTargetTestProject.build("clean", "jib", "-x=classes");
      Assert.fail();
    } catch (UnexpectedBuildFailure ex) {
      Assert.assertThat(
          ex.getMessage(),
          CoreMatchers.containsString(
              "Missing target image parameter, perhaps you should add a 'jib.to.image' "
                  + "configuration parameter to your build.gradle or set the parameter via the "
                  + "commandline (e.g. 'gradle jib --image <your image name>')."));
    }
  }

  @Test
  public void testDockerDaemon_defaultTarget()
      throws IOException, InterruptedException, DigestException {
    Assert.assertEquals(
        "Hello, world. An argument.\n",
        JibRunHelper.buildToDockerDaemonAndRun(
            defaultTargetTestProject, "default-target-name:default-target-version"));
    assertDockerInspect("default-target-name:default-target-version");
  }
}
