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

import com.google.cloud.tools.jib.Command;
import java.io.IOException;
import java.nio.file.Path;
import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;

/** Integration tests for {@link BuildImageMojo}. */
public class BuildImageMojoIT {

  @ClassRule public static final TestPlugin testPlugin = new TestPlugin();

  @ClassRule
  public static final TestProject simpleTestProject = new TestProject(testPlugin, "simple");

  @ClassRule
  public static final TestProject emptyTestProject = new TestProject(testPlugin, "empty");

  @Test
  public void testExecute_simple() throws VerificationException, IOException, InterruptedException {
    Assert.assertEquals(
        "Hello, world\n",
        buildAndRun(
            simpleTestProject.getProjectRoot(),
            "gcr.io/jib-integration-testing/jibtestimage:built-with-jib"));
  }

  @Test
  public void testExecute_empty() throws InterruptedException, IOException, VerificationException {
    Assert.assertEquals(
        "",
        buildAndRun(
            emptyTestProject.getProjectRoot(), "gcr.io/jib-integration-testing/emptyimage"));
  }

  private String buildAndRun(Path projectRoot, String imageReference)
      throws VerificationException, IOException, InterruptedException {
    Verifier verifier = new Verifier(projectRoot.toString());
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

    new Command("docker", "pull", imageReference).run();
    return new Command("docker", "run", imageReference).run();
  }
}
