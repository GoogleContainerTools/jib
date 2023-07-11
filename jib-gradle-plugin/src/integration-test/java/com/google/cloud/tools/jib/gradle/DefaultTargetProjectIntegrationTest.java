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
import java.nio.file.Path;
import java.security.DigestException;
import org.gradle.testkit.runner.UnexpectedBuildFailure;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Integration tests for building "default-target" project images. */
class DefaultTargetProjectIntegrationTest {

  @TempDir Path tempDir;

  @ClassRule
  public final TestProject defaultTargetTestProject = new TestProject("default-target", tempDir);

  /**
   * Asserts that the test project has the required exposed ports, labels and volumes.
   *
   * @param imageReference the image to test
   * @throws IOException if the {@code docker inspect} command fails to run
   * @throws InterruptedException if the {@code docker inspect} command is interrupted
   */
  private static void assertDockerInspect(String imageReference)
      throws IOException, InterruptedException {
    String dockerInspectExposedPorts =
        new Command("docker", "inspect", "-f", "'{{json .Config.ExposedPorts}}'", imageReference)
            .run();
    String dockerInspectLabels =
        new Command("docker", "inspect", "-f", "'{{json .Config.Labels}}'", imageReference).run();

    MatcherAssert.assertThat(
        dockerInspectExposedPorts,
        CoreMatchers.containsString(
            "\"1000/tcp\":{},\"2000/udp\":{},\"2001/udp\":{},\"2002/udp\":{},\"2003/udp\":{}"));
    MatcherAssert.assertThat(
        dockerInspectLabels,
        CoreMatchers.containsString("\"key1\":\"value1\",\"key2\":\"value2\""));
  }

  @Test
  void testBuild_defaultTarget() {
    // Test error when 'to' is missing
    try {
      defaultTargetTestProject.build(
          "clean", "jib", "-Djib.useOnlyProjectCache=true", "-x=classes");
      Assert.fail();
    } catch (UnexpectedBuildFailure ex) {
      MatcherAssert.assertThat(
          ex.getMessage(),
          CoreMatchers.containsString(
              "Missing target image parameter, perhaps you should add a 'jib.to.image' "
                  + "configuration parameter to your build.gradle or set the parameter via the "
                  + "commandline (e.g. 'gradle jib --image <your image name>')."));
    }
  }

  @Test
  void testDockerDaemon_defaultTarget() throws IOException, InterruptedException, DigestException {
    Assert.assertEquals(
        "Hello, world. An argument.\n",
        JibRunHelper.buildToDockerDaemonAndRun(
            defaultTargetTestProject,
            "default-target-name:default-target-version",
            "build.gradle"));
    assertDockerInspect("default-target-name:default-target-version");
  }
}
