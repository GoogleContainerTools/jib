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

package com.google.cloud.tools.jib.gradle;

import com.google.cloud.tools.jib.Command;
import java.io.IOException;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.BuildTask;
import org.gradle.testkit.runner.TaskOutcome;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;

/** Integration tests for {@link JibPlugin}. */
public class JibPluginIntegrationTest {

  @ClassRule public static final TestProject emptyTestProject = new TestProject("empty");

  @ClassRule public static final TestProject simpleTestProject = new TestProject("simple");

  private static String buildAndRun(TestProject testProject, String imageReference)
      throws IOException, InterruptedException {
    BuildResult buildResult = testProject.build("build", "jib");

    BuildTask jibTask = buildResult.task(":jib");

    Assert.assertNotNull(jibTask);
    Assert.assertEquals(TaskOutcome.SUCCESS, jibTask.getOutcome());
    Assert.assertThat(
        buildResult.getOutput(), CoreMatchers.containsString("Built and pushed image as "));
    Assert.assertThat(buildResult.getOutput(), CoreMatchers.containsString(imageReference));

    new Command("docker", "pull", imageReference).run();
    return new Command("docker", "run", imageReference).run();
  }

  private static String buildToDockerDaemonAndRun(TestProject testProject, String imageReference)
      throws IOException, InterruptedException {
    BuildResult buildResult = testProject.build("build", "jibBuildDocker");

    BuildTask jibBuildDockerTask = buildResult.task(":jibBuildDocker");

    Assert.assertNotNull(jibBuildDockerTask);
    Assert.assertEquals(TaskOutcome.SUCCESS, jibBuildDockerTask.getOutcome());
    Assert.assertThat(
        buildResult.getOutput(), CoreMatchers.containsString("Built image to Docker daemon as "));
    Assert.assertThat(buildResult.getOutput(), CoreMatchers.containsString(imageReference));

    return new Command("docker", "run", imageReference).run();
  }

  @Test
  public void testBuild_empty() throws IOException, InterruptedException {
    Assert.assertEquals(
        "", buildAndRun(emptyTestProject, "gcr.io/jib-integration-testing/emptyimage:gradle"));
  }

  @Test
  public void testBuild_simple() throws IOException, InterruptedException {
    Assert.assertEquals(
        "Hello, world\n",
        buildAndRun(simpleTestProject, "gcr.io/jib-integration-testing/simpleimage:gradle"));
  }

  @Test
  public void testDockerDaemon_empty() throws IOException, InterruptedException {
    Assert.assertEquals(
        "",
        buildToDockerDaemonAndRun(
            emptyTestProject, "gcr.io/jib-integration-testing/emptyimage:gradle"));
  }

  @Test
  public void testDockerDaemon_simple() throws IOException, InterruptedException {
    Assert.assertEquals(
        "Hello, world\n",
        buildToDockerDaemonAndRun(
            simpleTestProject, "gcr.io/jib-integration-testing/simpleimage:gradle"));
  }

  @Test
  public void testDockerContext() throws IOException, InterruptedException {
    BuildResult buildResult = simpleTestProject.build("build", "jibDockerContext", "--info");

    BuildTask jibDockerContextTask = buildResult.task(":jibDockerContext");

    Assert.assertNotNull(jibDockerContextTask);
    Assert.assertEquals(TaskOutcome.SUCCESS, jibDockerContextTask.getOutcome());
    Assert.assertThat(
        buildResult.getOutput(), CoreMatchers.containsString("Created Docker context at "));

    String imageName = "jib-gradle-plugin/integration-test";
    new Command(
            "docker",
            "build",
            "-t",
            imageName,
            simpleTestProject
                .getProjectRoot()
                .resolve("build")
                .resolve("jib-dockercontext")
                .toString())
        .run();
    Assert.assertEquals("Hello, world\n", new Command("docker", "run", imageName).run());

    // Checks that generating the Docker context again is skipped.
    BuildTask upToDateJibDockerContextTask =
        simpleTestProject.build("build", "jibDockerContext").task(":jibDockerContext");
    Assert.assertNotNull(upToDateJibDockerContextTask);
    Assert.assertEquals(TaskOutcome.UP_TO_DATE, upToDateJibDockerContextTask.getOutcome());
  }
}
