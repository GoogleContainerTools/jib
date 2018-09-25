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

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.BuildTask;
import org.gradle.testkit.runner.TaskOutcome;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;

/** Tests for {@link FilesTask}. */
public class FilesTaskTest {

  @ClassRule public static final TestProject simpleTestProject = new TestProject("simple");

  @ClassRule public static final TestProject multiTestProject = new TestProject("multi-service");

  /**
   * Verifies that the files task succeeded and returns the list of paths it prints out.
   *
   * @param project the project to run the task on
   * @param moduleName the name of the sub-project, or {@code null} if no sub-project
   * @return the list of paths printed by the task
   */
  private static List<Path> verifyTaskSuccess(TestProject project, @Nullable String moduleName) {
    String taskName =
        ":" + (moduleName == null ? "" : moduleName + ":") + JibPlugin.FILES_TASK_NAME;
    BuildResult buildResult = project.build(taskName, "-q");
    BuildTask jibTask = buildResult.task(taskName);
    Assert.assertNotNull(jibTask);
    Assert.assertEquals(TaskOutcome.SUCCESS, jibTask.getOutcome());

    return Splitter.on(System.lineSeparator())
        .omitEmptyStrings()
        .splitToList(buildResult.getOutput())
        .stream()
        .map(Paths::get)
        .collect(Collectors.toList());
  }

  /**
   * Asserts that two lists contain the same paths. Required to avoid Mac's /var/ vs. /private/var/
   * symlink issue.
   *
   * @param expected the expected list of paths
   * @param actual the actual list of paths
   * @throws IOException if checking if two files are the same fails
   */
  private static void assertPathListsAreEqual(List<Path> expected, List<Path> actual)
      throws IOException {
    Assert.assertEquals(expected.size(), actual.size());
    for (int index = 0; index < expected.size(); index++) {
      Assert.assertEquals(expected.get(index).toRealPath(), actual.get(index).toRealPath());
    }
  }

  @Test
  public void testFilesTask_singleProject() throws IOException {
    Path projectRoot = simpleTestProject.getProjectRoot();
    List<Path> result = verifyTaskSuccess(simpleTestProject, null);
    List<Path> expected =
        ImmutableList.of(
            projectRoot.resolve("build.gradle"),
            projectRoot.resolve("src/main/resources"),
            projectRoot.resolve("src/main/java"),
            projectRoot.resolve("src/main/custom-extra-dir"));
    assertPathListsAreEqual(expected, result);
  }

  @Test
  public void testFilesTask_multiProjectSimpleService() throws IOException {
    Path projectRoot = multiTestProject.getProjectRoot();
    Path simpleServiceRoot = projectRoot.resolve("simple-service");
    List<Path> result = verifyTaskSuccess(multiTestProject, "simple-service");
    List<Path> expected =
        ImmutableList.of(
            projectRoot.resolve("build.gradle"),
            projectRoot.resolve("settings.gradle"),
            projectRoot.resolve("gradle.properties"),
            simpleServiceRoot.resolve("build.gradle"),
            simpleServiceRoot.resolve("src/main/java"));
    assertPathListsAreEqual(expected, result);
  }

  @Test
  public void testFilesTask_multiProjectComplexService() throws IOException {
    Path projectRoot = multiTestProject.getProjectRoot();
    Path complexServiceRoot = projectRoot.resolve("complex-service");
    Path libRoot = projectRoot.resolve("lib");
    List<Path> result = verifyTaskSuccess(multiTestProject, "complex-service");
    List<Path> expected =
        ImmutableList.of(
            projectRoot.resolve("build.gradle"),
            projectRoot.resolve("settings.gradle"),
            projectRoot.resolve("gradle.properties"),
            complexServiceRoot.resolve("build.gradle"),
            complexServiceRoot.resolve("src/main/extra-resources-1"),
            complexServiceRoot.resolve("src/main/extra-resources-2"),
            complexServiceRoot.resolve("src/main/java"),
            complexServiceRoot.resolve("src/main/other-jib"),
            libRoot.resolve("build.gradle"),
            libRoot.resolve("src/main/resources"),
            libRoot.resolve("src/main/java"));
    assertPathListsAreEqual(expected, result.subList(0, 11));

    // guava jar is in a temporary-looking directory, so don't do a full match here
    Assert.assertThat(
        result.get(result.size() - 1).toString(),
        CoreMatchers.endsWith("guava-HEAD-jre-SNAPSHOT.jar"));
    Assert.assertEquals(12, result.size());
  }
}
