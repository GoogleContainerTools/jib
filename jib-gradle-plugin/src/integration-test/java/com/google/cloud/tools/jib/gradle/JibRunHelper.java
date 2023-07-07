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

import static com.google.common.truth.Truth.assertThat;

import com.google.cloud.tools.jib.Command;
import com.google.cloud.tools.jib.api.DescriptorDigest;
import com.google.cloud.tools.jib.api.ImageReference;
import com.google.cloud.tools.jib.api.InvalidImageReferenceException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.BuildTask;
import org.gradle.testkit.runner.TaskOutcome;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Assert;

/** Helper class to run integration tests. */
public class JibRunHelper {

  static String buildAndRun(TestProject testProject, String imageReference)
      throws IOException, InterruptedException, DigestException {
    return buildAndRun(testProject, imageReference, "build.gradle");
  }

  static String buildAndRun(
      TestProject testProject,
      String imageReference,
      String gradleBuildFile,
      String... extraRunArguments)
      throws IOException, InterruptedException, DigestException {
    BuildResult buildResult =
        testProject.build(
            "clean",
            "jib",
            "-Djib.useOnlyProjectCache=true",
            "-Djib.console=plain",
            "-D_TARGET_IMAGE=" + imageReference,
            "-Djib.allowInsecureRegistries=" + imageReference.startsWith("localhost"),
            "-b=" + gradleBuildFile);
    assertBuildSuccess(buildResult, "jib", "Built and pushed image as ");
    assertThatExpectedImageDigestAndIdReturned(testProject.getProjectRoot());
    MatcherAssert.assertThat(buildResult.getOutput(), CoreMatchers.containsString(imageReference));

    return pullAndRunBuiltImage(imageReference, extraRunArguments);
  }

  static String buildAndRunFromLocalBase(String target, String base)
      throws IOException, InterruptedException, DigestException {
    BuildResult buildResult =
        SingleProjectIntegrationTest.simpleTestProject.build(
            "clean",
            "jib",
            "-Djib.useOnlyProjectCache=true",
            "-Djib.console=plain",
            "-D_TARGET_IMAGE=" + target,
            "-D_BASE_IMAGE=" + base,
            "-Djib.allowInsecureRegistries=" + target.startsWith("localhost"),
            "-b=" + "build-local-base.gradle");
    assertBuildSuccess(buildResult, "jib", "Built and pushed image as ");
    assertThatExpectedImageDigestAndIdReturned(
        SingleProjectIntegrationTest.simpleTestProject.getProjectRoot());
    MatcherAssert.assertThat(buildResult.getOutput(), CoreMatchers.containsString(target));
    return pullAndRunBuiltImage(target);
  }

  static void buildAndRunAdditionalTag(
      TestProject testProject, String imageReference, String additionalTag, String expectedOutput)
      throws InvalidImageReferenceException, IOException, InterruptedException, DigestException {
    BuildResult buildResult =
        testProject.build(
            "clean",
            "jib",
            "-Djib.useOnlyProjectCache=true",
            "-Djib.console=plain",
            "-D_TARGET_IMAGE=" + imageReference,
            "-Djib.allowInsecureRegistries=" + imageReference.startsWith("localhost"),
            "-D_ADDITIONAL_TAG=" + additionalTag);
    assertBuildSuccess(buildResult, "jib", "Built and pushed image as ");
    assertThatExpectedImageDigestAndIdReturned(testProject.getProjectRoot());
    MatcherAssert.assertThat(buildResult.getOutput(), CoreMatchers.containsString(imageReference));

    String additionalImageReference =
        ImageReference.parse(imageReference).withQualifier(additionalTag).toString();
    MatcherAssert.assertThat(
        buildResult.getOutput(), CoreMatchers.containsString(additionalImageReference));

    Assert.assertEquals(expectedOutput, pullAndRunBuiltImage(imageReference));
    Assert.assertEquals(expectedOutput, pullAndRunBuiltImage(additionalImageReference));
    assertThat(getCreationTime(imageReference)).isEqualTo(Instant.EPOCH);
    assertThat(getCreationTime(additionalImageReference)).isEqualTo(Instant.EPOCH);
  }

  static BuildResult buildToDockerDaemon(
      TestProject testProject, String imageReference, String gradleBuildFile)
      throws IOException, InterruptedException, DigestException {
    BuildResult buildResult =
        testProject.build(
            "clean",
            "jibDockerBuild",
            "-Djib.useOnlyProjectCache=true",
            "-Djib.console=plain",
            "-D_TARGET_IMAGE=" + imageReference,
            "-Djib.allowInsecureRegistries=" + imageReference.startsWith("localhost"),
            "-b=" + gradleBuildFile);
    assertBuildSuccess(buildResult, "jibDockerBuild", "Built image to Docker daemon as ");
    assertThatExpectedImageDigestAndIdReturned(testProject.getProjectRoot());
    MatcherAssert.assertThat(buildResult.getOutput(), CoreMatchers.containsString(imageReference));

    String history = new Command("docker", "history", imageReference).run();
    MatcherAssert.assertThat(history, CoreMatchers.containsString("jib-gradle-plugin"));

    return buildResult;
  }

  static String buildToDockerDaemonAndRun(
      TestProject testProject, String imageReference, String gradleBuildFile)
      throws IOException, InterruptedException, DigestException {
    buildToDockerDaemon(testProject, imageReference, gradleBuildFile);
    return new Command("docker", "run", "--rm", imageReference).run();
  }

  /**
   * Asserts that the test project build output indicates a success.
   *
   * @param buildResult the builds results of the project under test
   * @param taskName the name of the Jib task that was run
   * @param successMessage a Jib-specific success message to check for
   */
  static void assertBuildSuccess(BuildResult buildResult, String taskName, String successMessage) {
    BuildTask classesTask = buildResult.task(":classes");
    BuildTask jibTask = buildResult.task(":" + taskName);

    Assert.assertNotNull(classesTask);
    Assert.assertEquals(TaskOutcome.SUCCESS, classesTask.getOutcome());
    Assert.assertNotNull(jibTask);
    Assert.assertEquals(TaskOutcome.SUCCESS, jibTask.getOutcome());
    MatcherAssert.assertThat(buildResult.getOutput(), CoreMatchers.containsString(successMessage));
  }

  static Instant getCreationTime(String imageReference) throws IOException, InterruptedException {
    String inspect =
        new Command("docker", "inspect", "-f", "{{.Created}}", imageReference).run().trim();
    return Instant.parse(inspect);
  }

  static void assertThatExpectedImageDigestAndIdReturned(Path projectRoot)
      throws IOException, DigestException {
    Path digestPath = projectRoot.resolve("build/jib-image.digest");
    Assert.assertTrue(Files.exists(digestPath));
    String digest = new String(Files.readAllBytes(digestPath), StandardCharsets.UTF_8);
    DescriptorDigest digest1 = DescriptorDigest.fromDigest(digest);

    Path idPath = projectRoot.resolve("build/jib-image.id");
    Assert.assertTrue(Files.exists(idPath));
    String id = new String(Files.readAllBytes(idPath), StandardCharsets.UTF_8);
    DescriptorDigest digest2 = DescriptorDigest.fromDigest(id);
    Assert.assertNotEquals(digest1, digest2);
  }

  /**
   * Pulls a built image and attempts to run it. Also verifies the container configuration and
   * history of the built image.
   *
   * @param imageReference the image reference of the built image
   * @param extraRunArguments extra arguments passed to {@code docker run}
   * @return the container output
   * @throws IOException if an I/O exception occurs
   * @throws InterruptedException if the process was interrupted
   */
  static String pullAndRunBuiltImage(String imageReference, String... extraRunArguments)
      throws IOException, InterruptedException {
    new Command("docker", "pull", imageReference).run();
    String history = new Command("docker", "history", imageReference).run();
    MatcherAssert.assertThat(history, CoreMatchers.containsString("jib-gradle-plugin"));

    List<String> command = new ArrayList<>(Arrays.asList("docker", "run", "--rm"));
    command.addAll(Arrays.asList(extraRunArguments));
    command.add(imageReference);
    return new Command(command).run();
  }
}
