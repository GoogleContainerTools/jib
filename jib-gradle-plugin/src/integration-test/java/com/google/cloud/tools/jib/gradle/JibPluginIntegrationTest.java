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
import com.google.cloud.tools.jib.IntegrationTestingConfiguration;
import com.google.cloud.tools.jib.registry.LocalRegistry;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.BuildTask;
import org.gradle.testkit.runner.TaskOutcome;
import org.gradle.testkit.runner.UnexpectedBuildFailure;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

/** Integration tests for {@link JibPlugin}. */
public class JibPluginIntegrationTest {

  @ClassRule
  public static final LocalRegistry localRegistry1 =
      new LocalRegistry(5000, "testuser", "testpassword");

  @ClassRule
  public static final LocalRegistry localRegistry2 =
      new LocalRegistry(6000, "testuser2", "testpassword2");

  @ClassRule public static final TestProject emptyTestProject = new TestProject("empty");

  @ClassRule public static final TestProject simpleTestProject = new TestProject("simple");

  @ClassRule
  public static final TestProject multiprojectTestProject = new TestProject("multiproject");

  @ClassRule
  public static final TestProject defaultTargetTestProject = new TestProject("default-target");

  private static String buildAndRun(TestProject testProject, String imageReference)
      throws IOException, InterruptedException {
    BuildResult buildResult =
        testProject.build(
            "clean", JibPlugin.BUILD_IMAGE_TASK_NAME, "-D_TARGET_IMAGE=" + imageReference);
    assertBuildSuccess(buildResult, JibPlugin.BUILD_IMAGE_TASK_NAME, "Built and pushed image as ");
    Assert.assertThat(buildResult.getOutput(), CoreMatchers.containsString(imageReference));

    new Command("docker", "pull", imageReference).run();
    assertDockerInspect(imageReference);
    String history = new Command("docker", "history", imageReference).run();
    Assert.assertThat(history, CoreMatchers.containsString("jib-gradle-plugin"));
    return new Command("docker", "run", imageReference).run();
  }

  private static String buildAndRunComplex(
      String imageReference, String username, String password, LocalRegistry targetRegistry)
      throws IOException, InterruptedException {
    BuildResult buildResult =
        simpleTestProject.build(
            "clean",
            JibPlugin.BUILD_IMAGE_TASK_NAME,
            "-D_TARGET_IMAGE=" + imageReference,
            "-D_TARGET_USERNAME=" + username,
            "-D_TARGET_PASSWORD=" + password,
            "-DsendCredentialsOverHttp=true",
            "-b=complex-build.gradle");

    assertBuildSuccess(buildResult, JibPlugin.BUILD_IMAGE_TASK_NAME, "Built and pushed image as ");
    Assert.assertThat(buildResult.getOutput(), CoreMatchers.containsString(imageReference));

    targetRegistry.pull(imageReference);
    assertDockerInspect(imageReference);
    String history = new Command("docker", "history", imageReference).run();
    Assert.assertThat(history, CoreMatchers.containsString("jib-gradle-plugin"));
    return new Command("docker", "run", imageReference).run();
  }

  private static String buildToDockerDaemonAndRun(TestProject testProject, String imageReference)
      throws IOException, InterruptedException {
    BuildResult buildResult =
        testProject.build(
            "clean", JibPlugin.BUILD_DOCKER_TASK_NAME, "-D_TARGET_IMAGE=" + imageReference);
    assertBuildSuccess(
        buildResult, JibPlugin.BUILD_DOCKER_TASK_NAME, "Built image to Docker daemon as ");
    Assert.assertThat(buildResult.getOutput(), CoreMatchers.containsString(imageReference));

    assertDockerInspect(imageReference);
    String history = new Command("docker", "history", imageReference).run();
    Assert.assertThat(history, CoreMatchers.containsString("jib-gradle-plugin"));
    return new Command("docker", "run", imageReference).run();
  }

  /**
   * Asserts that the test project build output indicates a success.
   *
   * @param buildResult the builds results of the project under test
   * @param taskName the name of the Jib task that was run
   * @param successMessage a Jib-specific success message to check for
   */
  private static void assertBuildSuccess(
      BuildResult buildResult, String taskName, String successMessage) {
    BuildTask classesTask = buildResult.task(":classes");
    BuildTask jibTask = buildResult.task(":" + taskName);

    Assert.assertNotNull(classesTask);
    Assert.assertEquals(TaskOutcome.SUCCESS, classesTask.getOutcome());
    Assert.assertNotNull(jibTask);
    Assert.assertEquals(TaskOutcome.SUCCESS, jibTask.getOutcome());
    Assert.assertThat(buildResult.getOutput(), CoreMatchers.containsString(successMessage));
  }

  /**
   * Asserts that the creation time of the simple test project is set. If the time parsed from the
   * {@code docker inspect} command occurs before the specified time (i.e. if it is 1970), then the
   * assertion will fail.
   *
   * @param before the specified time to compare the resulting image's creation time to
   * @param imageReference the image to test
   * @throws IOException if the {@code docker inspect} command fails to run
   * @throws InterruptedException if the {@code docker inspect} command is interrupted
   */
  private static void assertSimpleCreationTimeIsAfter(Instant before, String imageReference)
      throws IOException, InterruptedException {
    String inspect =
        new Command("docker", "inspect", "-f", "{{.Created}}", imageReference).run().trim();
    Instant parsed = Instant.parse(inspect);
    Assert.assertTrue(parsed.isAfter(before) || parsed.equals(before));
  }

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

  @Before
  public void setup() throws IOException, InterruptedException {
    // Pull distroless and push to local registry so we can test 'from' credentials
    localRegistry1.pullAndPushToLocal("gcr.io/distroless/java:latest", "distroless/java");
  }

  @Test
  public void testBuild_empty() throws IOException, InterruptedException {
    String targetImage =
        "gcr.io/"
            + IntegrationTestingConfiguration.getGCPProject()
            + "/emptyimage:gradle"
            + System.nanoTime();
    Assert.assertEquals("", buildAndRun(emptyTestProject, targetImage));
    Assert.assertEquals(
        "1970-01-01T00:00:00Z",
        new Command("docker", "inspect", "-f", "{{.Created}}", targetImage).run().trim());
  }

  @Test
  public void testBuild_simple() throws IOException, InterruptedException {
    String targetImage =
        "gcr.io/"
            + IntegrationTestingConfiguration.getGCPProject()
            + "/simpleimage:gradle"
            + System.nanoTime();

    // Test empty output error
    try {
      simpleTestProject.build(
          "clean", JibPlugin.BUILD_IMAGE_TASK_NAME, "-x=classes", "-D_TARGET_IMAGE=" + targetImage);
      Assert.fail();

    } catch (UnexpectedBuildFailure ex) {
      Assert.assertThat(
          ex.getMessage(),
          CoreMatchers.containsString(
              "No classes files were found - did you compile your project?"));
    }

    Instant beforeBuild = Instant.now();
    Assert.assertEquals(
        "Hello, world. An argument.\nfoo\ncat\n", buildAndRun(simpleTestProject, targetImage));
    assertSimpleCreationTimeIsAfter(beforeBuild, targetImage);
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
  public void testBuild_complex() throws IOException, InterruptedException {
    String targetImage = "localhost:6000/compleximage:gradle" + System.nanoTime();
    Instant beforeBuild = Instant.now();
    Assert.assertEquals(
        "Hello, world. An argument.\nfoo\ncat\n-Xms512m\n-Xdebug\nenvvalue1\nenvvalue2\n",
        buildAndRunComplex(targetImage, "testuser2", "testpassword2", localRegistry2));
    assertSimpleCreationTimeIsAfter(beforeBuild, targetImage);
  }

  @Test
  public void testBuild_complex_sameFromAndToRegistry() throws IOException, InterruptedException {
    String targetImage = "localhost:5000/compleximage:gradle" + System.nanoTime();
    Instant beforeBuild = Instant.now();
    Assert.assertEquals(
        "Hello, world. An argument.\nfoo\ncat\n-Xms512m\n-Xdebug\nenvvalue1\nenvvalue2\n",
        buildAndRunComplex(targetImage, "testuser", "testpassword", localRegistry1));
    assertSimpleCreationTimeIsAfter(beforeBuild, targetImage);
  }

  @Test
  public void testDockerDaemon_empty() throws IOException, InterruptedException {
    String targetImage = "emptyimage:gradle" + System.nanoTime();
    Assert.assertEquals("", buildToDockerDaemonAndRun(emptyTestProject, targetImage));
    Assert.assertEquals(
        "1970-01-01T00:00:00Z",
        new Command("docker", "inspect", "-f", "{{.Created}}", targetImage).run().trim());
  }

  @Test
  public void testDockerDaemon_simple() throws IOException, InterruptedException {
    String targetImage = "simpleimage:gradle" + System.nanoTime();
    Instant beforeBuild = Instant.now();
    Assert.assertEquals(
        "Hello, world. An argument.\nfoo\ncat\n",
        buildToDockerDaemonAndRun(simpleTestProject, targetImage));
    assertSimpleCreationTimeIsAfter(beforeBuild, targetImage);
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
    String targetImage = "simpleimage:gradle" + System.nanoTime();

    String outputPath =
        simpleTestProject.getProjectRoot().resolve("build").resolve("jib-image.tar").toString();
    Instant beforeBuild = Instant.now();
    BuildResult buildResult =
        simpleTestProject.build(
            "clean", JibPlugin.BUILD_TAR_TASK_NAME, "-D_TARGET_IMAGE=" + targetImage);

    assertBuildSuccess(buildResult, JibPlugin.BUILD_TAR_TASK_NAME, "Built image tarball at ");
    Assert.assertThat(buildResult.getOutput(), CoreMatchers.containsString(outputPath));

    new Command("docker", "load", "--input", outputPath).run();
    Assert.assertEquals(
        "Hello, world. An argument.\nfoo\ncat\n", new Command("docker", "run", targetImage).run());
    assertDockerInspect(targetImage);
    assertSimpleCreationTimeIsAfter(beforeBuild, targetImage);
  }

  @Test
  public void testDockerContext() throws IOException, InterruptedException {
    BuildResult buildResult =
        simpleTestProject.build("clean", JibPlugin.DOCKER_CONTEXT_TASK_NAME, "--info");

    assertBuildSuccess(
        buildResult, JibPlugin.DOCKER_CONTEXT_TASK_NAME, "Created Docker context at ");

    String imageName = "jib-gradle-plugin/integration-test" + System.nanoTime();
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

    assertDockerInspect(imageName);
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

  @Test
  public void testMultiProject() {
    BuildResult buildResult =
        multiprojectTestProject.build(
            "clean", ":a_packaged:" + JibPlugin.DOCKER_CONTEXT_TASK_NAME, "--info");

    BuildTask classesTask = buildResult.task(":a_packaged:classes");
    BuildTask jibTask = buildResult.task(":a_packaged:" + JibPlugin.DOCKER_CONTEXT_TASK_NAME);
    Assert.assertNotNull(classesTask);
    Assert.assertEquals(TaskOutcome.SUCCESS, classesTask.getOutcome());
    Assert.assertNotNull(jibTask);
    Assert.assertEquals(TaskOutcome.SUCCESS, jibTask.getOutcome());
    Assert.assertThat(
        buildResult.getOutput(), CoreMatchers.containsString("Created Docker context at "));
  }
}
