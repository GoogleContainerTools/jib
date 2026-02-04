/*
 * Copyright 2019 Google LLC.
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

package com.google.cloud.tools.jib.gradle.skaffold;

import static com.google.common.truth.Truth.assertThat;

import com.google.cloud.tools.jib.gradle.JibPlugin;
import com.google.cloud.tools.jib.gradle.TestProject;
import com.google.cloud.tools.jib.plugins.common.SkaffoldFilesOutput;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import javax.annotation.Nullable;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.BuildTask;
import org.gradle.testkit.runner.TaskOutcome;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;

/** Tests for {@link FilesTaskV2}. */
public class FilesTaskV2Test {

  @ClassRule public static final TestProject simpleTestProject = new TestProject("simple");

  @ClassRule
  public static final TestProject skaffoldTestProject = new TestProject("skaffold-config");

  @ClassRule
 public static final TestProject multiTestProject =
      org.gradle.util.GradleVersion.current().compareTo(org.gradle.util.GradleVersion.version("9.0")) >= 0
          ? new TestProject("multi-service").withGradleVersion("9.0")
          : new TestProject("multi-service");

  @ClassRule
  public static final TestProject platformProject =
      new TestProject("platform").withGradleVersion("5.2");

  /**
   * Verifies that the files task succeeded and returns the list of paths it prints out.
   *
   * @param project the project to run the task on
   * @param moduleName the name of the sub-project, or {@code null} if no sub-project
   * @return the JSON string printed by the task
   */
  private static String verifyTaskSuccess(TestProject project, @Nullable String moduleName) {
    String taskName =
        ":" + (moduleName == null ? "" : moduleName + ":") + JibPlugin.SKAFFOLD_FILES_TASK_V2_NAME;
    BuildResult buildResult = project.build(taskName, "-q", "-D_TARGET_IMAGE=ignored");
    BuildTask jibTask = buildResult.task(taskName);
    Assert.assertNotNull(jibTask);
    Assert.assertEquals(TaskOutcome.SUCCESS, jibTask.getOutcome());
    String output = buildResult.getOutput().trim();
    MatcherAssert.assertThat(output, CoreMatchers.startsWith("BEGIN JIB JSON"));

    // Return task output with header removed
    return output.replace("BEGIN JIB JSON", "").trim();
  }

  /**
   * Asserts that two lists contain the same paths. Required to avoid Mac's /var/ vs. /private/var/
   * symlink issue.
   *
   * @param expected the expected list of paths
   * @param actual the actual list of paths
   * @throws IOException if checking if two files are the same fails
   */
  private static void assertPathListsAreEqual(List<Path> expected, List<String> actual)
      throws IOException {
    Assert.assertEquals(expected.size(), actual.size());
    for (int index = 0; index < expected.size(); index++) {
      Assert.assertEquals(
          expected.get(index).toRealPath(), Paths.get(actual.get(index)).toRealPath());
    }
  }

  @Test
  public void testFilesTask_singleProject() throws IOException {
    Path projectRoot = simpleTestProject.getProjectRoot();
    SkaffoldFilesOutput result =
        new SkaffoldFilesOutput(verifyTaskSuccess(simpleTestProject, null));
    assertPathListsAreEqual(
        ImmutableList.of(projectRoot.resolve("build.gradle")), result.getBuild());
    assertPathListsAreEqual(
        ImmutableList.of(
            projectRoot.resolve("src/main/resources"),
            projectRoot.resolve("src/main/java"),
            projectRoot.resolve("src/main/custom-extra-dir")),
        result.getInputs());
    assertThat(result.getIgnore()).isEmpty();
  }

  @Test
  public void testFilesTask_multiProjectSimpleService() throws IOException {
    Path projectRoot = multiTestProject.getProjectRoot();
    Path simpleServiceRoot = projectRoot.resolve("simple-service");
    SkaffoldFilesOutput result =
        new SkaffoldFilesOutput(verifyTaskSuccess(multiTestProject, "simple-service"));
    assertPathListsAreEqual(
        ImmutableList.of(
            projectRoot.resolve("build.gradle"),
            projectRoot.resolve("settings.gradle"),
            projectRoot.resolve("gradle.properties"),
            simpleServiceRoot.resolve("build.gradle")),
        result.getBuild());
    assertPathListsAreEqual(
        ImmutableList.of(simpleServiceRoot.resolve("src/main/java")), result.getInputs());
    assertThat(result.getIgnore()).isEmpty();
  }

  @Test
  public void testFilesTask_multiProjectComplexService() throws IOException {
    System.out.println("Running testFilesTask_multiProjectComplexService" + multiTestProject.getGradleVersion());
    Path projectRoot = multiTestProject.getProjectRoot();
    Path complexServiceRoot = projectRoot.resolve("complex-service");
    Path libRoot = projectRoot.resolve("lib");
    SkaffoldFilesOutput result =
        new SkaffoldFilesOutput(verifyTaskSuccess(multiTestProject, "complex-service"));
    assertPathListsAreEqual(
        ImmutableList.of(
            projectRoot.resolve("build.gradle"),
            projectRoot.resolve("settings.gradle"),
            projectRoot.resolve("gradle.properties"),
            complexServiceRoot.resolve("build.gradle"),
            libRoot.resolve("build.gradle")),
        result.getBuild());
    assertPathListsAreEqual(
        ImmutableList.of(
            complexServiceRoot.resolve("src/main/extra-resources-1"),
            complexServiceRoot.resolve("src/main/extra-resources-2"),
            complexServiceRoot.resolve("src/main/java"),
            complexServiceRoot.resolve("src/main/other-jib"),
            libRoot.resolve("src/main/resources"),
            libRoot.resolve("src/main/java"),
            complexServiceRoot.resolve(
                "local-m2-repo/com/google/cloud/tools/tiny-test-lib/0.0.1-SNAPSHOT/tiny-test-lib-0.0.1-SNAPSHOT.jar")),
        result.getInputs());
    assertThat(result.getIgnore()).isEmpty();
  }

  @Test
  public void testFilesTask_platformProject() throws IOException {
    Path projectRoot = platformProject.getProjectRoot();
    Path platformRoot = projectRoot.resolve("platform");
    Path serviceRoot = projectRoot.resolve("service");
    SkaffoldFilesOutput result =
        new SkaffoldFilesOutput(verifyTaskSuccess(platformProject, "service"));
    assertPathListsAreEqual(
        ImmutableList.of(
            projectRoot.resolve("build.gradle"),
            projectRoot.resolve("settings.gradle"),
            serviceRoot.resolve("build.gradle"),
            platformRoot.resolve("build.gradle")),
        result.getBuild());
    assertPathListsAreEqual(
        ImmutableList.of(serviceRoot.resolve("src/main/java")), result.getInputs());
    assertThat(result.getIgnore()).isEmpty();
  }

  @Test
  public void testFilesTast_withConfigModifiers() throws IOException {
    Path projectRoot = skaffoldTestProject.getProjectRoot();
    SkaffoldFilesOutput result =
        new SkaffoldFilesOutput(verifyTaskSuccess(skaffoldTestProject, null));
    assertPathListsAreEqual(
        ImmutableList.of(projectRoot.resolve("build.gradle"), projectRoot.resolve("script.gradle")),
        result.getBuild());
    assertPathListsAreEqual(
        ImmutableList.of(
            projectRoot.resolve("src/main/resources"),
            projectRoot.resolve("src/main/java"),
            projectRoot.resolve("src/main/jib"),
            projectRoot.resolve("other/file.txt")),
        result.getInputs());
    assertPathListsAreEqual(
        ImmutableList.of(projectRoot.resolve("src/main/jib/bar")), result.getIgnore());
  }
}

