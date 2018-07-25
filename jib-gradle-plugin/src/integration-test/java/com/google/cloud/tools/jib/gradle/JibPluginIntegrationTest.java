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
import java.nio.file.Files;
import java.time.Instant;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.BuildTask;
import org.gradle.testkit.runner.TaskOutcome;
import org.gradle.testkit.runner.UnexpectedBuildFailure;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;

/** Integration tests for {@link JibPlugin}. */
public class JibPluginIntegrationTest {

  @ClassRule public static final TestProject emptyTestProject = new TestProject("empty");

  @ClassRule public static final TestProject simpleTestProject = new TestProject("simple");

  @ClassRule
  public static final TestProject defaultTargetTestProject = new TestProject("default-target");

  private static String buildAndRun(TestProject testProject, String imageReference)
      throws IOException, InterruptedException {
    BuildResult buildResult = testProject.build("clean", JibPlugin.BUILD_IMAGE_TASK_NAME);

    BuildTask classesTask = buildResult.task(":classes");
    BuildTask jibTask = buildResult.task(":" + JibPlugin.BUILD_IMAGE_TASK_NAME);

    Assert.assertNotNull(classesTask);
    Assert.assertEquals(TaskOutcome.SUCCESS, classesTask.getOutcome());
    Assert.assertNotNull(jibTask);
    Assert.assertEquals(TaskOutcome.SUCCESS, jibTask.getOutcome());
    Assert.assertThat(
        buildResult.getOutput(), CoreMatchers.containsString("Built and pushed image as "));
    Assert.assertThat(buildResult.getOutput(), CoreMatchers.containsString(imageReference));

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

  private static String buildToDockerDaemonAndRun(TestProject testProject, String imageReference)
      throws IOException, InterruptedException {
    BuildResult buildResult = testProject.build("clean", JibPlugin.BUILD_DOCKER_TASK_NAME);

    BuildTask classesTask = buildResult.task(":classes");
    BuildTask jibBuildDockerTask = buildResult.task(":" + JibPlugin.BUILD_DOCKER_TASK_NAME);

    Assert.assertNotNull(classesTask);
    Assert.assertEquals(TaskOutcome.SUCCESS, classesTask.getOutcome());
    Assert.assertNotNull(jibBuildDockerTask);
    Assert.assertEquals(TaskOutcome.SUCCESS, jibBuildDockerTask.getOutcome());
    Assert.assertThat(
        buildResult.getOutput(), CoreMatchers.containsString("Built image to Docker daemon as "));
    Assert.assertThat(buildResult.getOutput(), CoreMatchers.containsString(imageReference));

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
  public void testBuild_empty() throws IOException, InterruptedException {
    Assert.assertEquals(
        "", buildAndRun(emptyTestProject, "gcr.io/jib-integration-testing/emptyimage:gradle"));
    Assert.assertEquals(
        "1970-01-01T00:00:00Z",
        new Command(
                "docker",
                "inspect",
                "-f",
                "{{.Created}}",
                "gcr.io/jib-integration-testing/emptyimage:gradle")
            .run()
            .trim());
  }

  @Test
  public void testBuild_simple() throws IOException, InterruptedException {
    // Test empty output error
    try {
      simpleTestProject.build("clean", JibPlugin.BUILD_IMAGE_TASK_NAME, "-x=classes");
      Assert.fail();
    } catch (UnexpectedBuildFailure ex) {
      Assert.assertThat(
          ex.getMessage(),
          CoreMatchers.containsString(
              "No classes files were found - did you compile your project?"));
    }

    Instant beforeBuild = Instant.now();
    Assert.assertEquals(
        "Hello, world. An argument.\nfoo\ncat\n",
        buildAndRun(simpleTestProject, "gcr.io/jib-integration-testing/simpleimage:gradle"));
    assertSimpleCreationTimeIsAfter(beforeBuild);
  }

  @Test
  public void testBuild_defaultTarget() {
    // Test error when 'to' is missing
    try {
      defaultTargetTestProject.build("clean", JibPlugin.BUILD_IMAGE_TASK_NAME, "-x=classes");
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
  public void testDockerDaemon_empty() throws IOException, InterruptedException {
    Assert.assertEquals(
        "",
        buildToDockerDaemonAndRun(
            emptyTestProject, "gcr.io/jib-integration-testing/emptyimage:gradle"));
    Assert.assertEquals(
        "1970-01-01T00:00:00Z",
        new Command(
                "docker",
                "inspect",
                "-f",
                "{{.Created}}",
                "gcr.io/jib-integration-testing/emptyimage:gradle")
            .run()
            .trim());
  }

  @Test
  public void testDockerDaemon_simple() throws IOException, InterruptedException {
    Instant beforeBuild = Instant.now();
    Assert.assertEquals(
        "Hello, world. An argument.\nfoo\ncat\n",
        buildToDockerDaemonAndRun(
            simpleTestProject, "gcr.io/jib-integration-testing/simpleimage:gradle"));
    assertSimpleCreationTimeIsAfter(beforeBuild);
  }

  @Test
  public void testDockerDaemon_defaultTarget() throws IOException, InterruptedException {
    Assert.assertEquals(
        "Hello, world. An argument.\n",
        buildToDockerDaemonAndRun(
            defaultTargetTestProject, "default-target-name:default-target-version"));
  }

  @Test
  public void testBuildTar_simple() throws IOException, InterruptedException {
    String imageReference = "gcr.io/jib-integration-testing/simpleimage:gradle";
    String outputPath =
        simpleTestProject.getProjectRoot().resolve("build").resolve("jib-image.tar").toString();
    Instant beforeBuild = Instant.now();
    BuildResult buildResult = simpleTestProject.build("clean", JibPlugin.BUILD_TAR_TASK_NAME);

    BuildTask classesTask = buildResult.task(":classes");
    BuildTask jibBuildTarTask = buildResult.task(":" + JibPlugin.BUILD_TAR_TASK_NAME);

    Assert.assertNotNull(classesTask);
    Assert.assertEquals(TaskOutcome.SUCCESS, classesTask.getOutcome());
    Assert.assertNotNull(jibBuildTarTask);
    Assert.assertEquals(TaskOutcome.SUCCESS, jibBuildTarTask.getOutcome());
    Assert.assertThat(
        buildResult.getOutput(), CoreMatchers.containsString("Built image tarball at "));
    Assert.assertThat(buildResult.getOutput(), CoreMatchers.containsString(outputPath));

    new Command("docker", "load", "--input", outputPath).run();
    Assert.assertEquals(
        "Hello, world. An argument.\nfoo\ncat\n",
        new Command("docker", "run", imageReference).run());
    assertSimpleCreationTimeIsAfter(beforeBuild);
  }

  @Test
  public void testDockerContext() throws IOException, InterruptedException {
    BuildResult buildResult =
        simpleTestProject.build("clean", JibPlugin.DOCKER_CONTEXT_TASK_NAME, "--info");

    BuildTask classesTask = buildResult.task(":classes");
    BuildTask jibDockerContextTask = buildResult.task(":" + JibPlugin.DOCKER_CONTEXT_TASK_NAME);

    Assert.assertNotNull(classesTask);
    Assert.assertEquals(TaskOutcome.SUCCESS, classesTask.getOutcome());
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
                .resolve("jib-docker-context")
                .toString())
        .run();

    Assert.assertThat(
        new Command("docker", "inspect", imageName).run(),
        CoreMatchers.containsString(
            "            \"ExposedPorts\": {\n"
                + "                \"1000/tcp\": {},\n"
                + "                \"2000/udp\": {},\n"
                + "                \"2001/udp\": {},\n"
                + "                \"2002/udp\": {},\n"
                + "                \"2003/udp\": {}"));
    Assert.assertEquals(
        "Hello, world. An argument.\nfoo\ncat\n", new Command("docker", "run", imageName).run());

    // Checks that generating the Docker context again is skipped.
    BuildTask upToDateJibDockerContextTask =
        simpleTestProject
            .build(JibPlugin.DOCKER_CONTEXT_TASK_NAME)
            .task(":" + JibPlugin.DOCKER_CONTEXT_TASK_NAME);
    Assert.assertNotNull(upToDateJibDockerContextTask);
    Assert.assertEquals(TaskOutcome.UP_TO_DATE, upToDateJibDockerContextTask.getOutcome());

    // Checks that adding a new file generates the Docker context again.
    Files.createFile(
        simpleTestProject
            .getProjectRoot()
            .resolve("src")
            .resolve("main")
            .resolve("resources")
            .resolve("newfile"));
    try {
      BuildTask reexecutedJibDockerContextTask =
          simpleTestProject
              .build(JibPlugin.DOCKER_CONTEXT_TASK_NAME)
              .task(":" + JibPlugin.DOCKER_CONTEXT_TASK_NAME);
      Assert.assertNotNull(reexecutedJibDockerContextTask);
      Assert.assertEquals(TaskOutcome.SUCCESS, reexecutedJibDockerContextTask.getOutcome());

    } catch (UnexpectedBuildFailure ex) {
      // This might happen on systems without SecureDirectoryStream, so we just ignore it.
      // See com.google.common.io.MoreFiles#deleteDirectoryContents.
      Assert.assertThat(
          ex.getMessage(),
          CoreMatchers.containsString(
              "Export Docker context failed because cannot clear directory"));
    }
  }

  /**
   * Asserts that the creation time of the simple test project is set. If the time parsed from the
   * {@code docker inspect} command occurs before the specified time (i.e. if it is 1970), then the
   * assertion will fail.
   *
   * @param before the specified time to compare the resulting image's creation time to
   * @throws IOException if the {@code docker inspect} command fails to run
   * @throws InterruptedException if the {@code docker inspect} command is interrupted
   */
  private static void assertSimpleCreationTimeIsAfter(Instant before)
      throws IOException, InterruptedException {
    String inspect =
        new Command(
                "docker",
                "inspect",
                "-f",
                "{{.Created}}",
                "gcr.io/jib-integration-testing/simpleimage:gradle")
            .run()
            .trim();
    Instant parsed = Instant.parse(inspect);
    Assert.assertTrue(parsed.isAfter(before) || parsed.equals(before));
  }
}
