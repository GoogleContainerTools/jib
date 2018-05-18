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
import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
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

  /**
   * Builds and runs jib:buildDocker on a project at {@code projectRoot} pushing to {@code
   * imageReference}.
   */
  private static String buildToDockerDaemonAndRun(Path projectRoot, String imageReference)
      throws VerificationException, IOException, InterruptedException {
    Verifier verifier = new Verifier(projectRoot.toString());
    verifier.setAutoclean(false);
    verifier.executeGoal("package");

    // Builds twice, and checks if the second build took less time.
    verifier.executeGoal("jib:buildDocker");
    verifier.verifyErrorFreeLog();

    return new Command("docker", "run", imageReference).run();
  }

  @Test
  public void testExecute_simple() throws VerificationException, IOException, InterruptedException {
    Assert.assertEquals(
        "Hello, world\n",
        buildToDockerDaemonAndRun(
            simpleTestProject.getProjectRoot(),
            "gcr.io/jib-integration-testing/simpleimage:maven"));
  }

  @Test
  public void testExecute_empty() throws InterruptedException, IOException, VerificationException {
    Assert.assertEquals(
        "",
        buildToDockerDaemonAndRun(
            emptyTestProject.getProjectRoot(), "gcr.io/jib-integration-testing/emptyimage:maven"));
  }
}
