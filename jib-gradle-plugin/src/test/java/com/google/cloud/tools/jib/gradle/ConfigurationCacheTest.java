/*
 * Copyright 2026 Google LLC.
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

import com.google.common.base.Splitter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.BuildTask;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.ClassRule;
import org.junit.Test;

/** Tests that the Jib tasks are compatible with the Gradle configuration cache. */
public class ConfigurationCacheTest {

  // Gradle 9 requires Java 17 to run, but this build's tests also run on Java 8 and 11, so the
  // latest Gradle 8 is used. Note that Gradle 9 is stricter: it also fails when two Jib tasks share
  // the JibExtension and one of them is recorded as its owner (see the final getJib() getters).
  private static final String GRADLE_VERSION = "8.14.3";

  @ClassRule
  public static final TestProject testProject =
      new TestProject("configuration-cache").withGradleVersion(GRADLE_VERSION);

  private static BuildResult build(String taskName) {
    BuildResult buildResult = testProject.build(":app:" + taskName, "--configuration-cache");
    BuildTask task = buildResult.task(":app:" + taskName);
    assertThat(task).isNotNull();
    assertThat(task.getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
    return buildResult;
  }

  private static String getJson(BuildResult buildResult) {
    String output = buildResult.getOutput();
    int start = output.indexOf("BEGIN JIB JSON");
    assertThat(start).isAtLeast(0);
    // The JSON is printed on the line following the header.
    return Splitter.onPattern("\r?\n").splitToList(output.substring(start)).get(1);
  }

  @Test
  public void testBuildTar() throws IOException {
    Path tar = testProject.getProjectRoot().resolve("app/build/custom/jib-image.tar");

    BuildResult firstResult = build(JibPlugin.BUILD_TAR_TASK_NAME);
    assertThat(firstResult.getOutput()).contains("Configuration cache entry stored");
    assertThat(Files.exists(tar)).isTrue();

    // Run again from the configuration cache.
    Files.delete(tar);
    BuildResult secondResult = build(JibPlugin.BUILD_TAR_TASK_NAME);
    assertThat(secondResult.getOutput()).contains("Reusing configuration cache");
    assertThat(Files.exists(tar)).isTrue();
  }

  @Test
  public void testMultipleTasks() {
    // The JibExtension is shared by all tasks, so it must not reference any of them.
    String[] taskPaths = {
      ":app:" + JibPlugin.BUILD_TAR_TASK_NAME,
      ":app:" + JibPlugin.SKAFFOLD_FILES_TASK_V2_NAME,
      ":app:" + JibPlugin.SKAFFOLD_INIT_TASK_NAME,
      ":app:" + JibPlugin.SKAFFOLD_SYNC_MAP_TASK_NAME
    };
    List<String> arguments = new ArrayList<>(Arrays.asList(taskPaths));
    arguments.add("--configuration-cache");

    testProject.build(arguments);
    BuildResult secondResult = testProject.build(arguments);
    assertThat(secondResult.getOutput()).contains("Reusing configuration cache");
    for (String taskPath : taskPaths) {
      assertThat(secondResult.task(taskPath)).isNotNull();
    }
  }

  @Test
  public void testSkaffoldFilesTask() {
    String firstJson = getJson(build(JibPlugin.SKAFFOLD_FILES_TASK_V2_NAME));
    assertThat(firstJson).contains("lib");
    assertThat(firstJson).contains("custom-extra-dir");
    assertThat(firstJson).contains("snapshot-dependency-1.0.0-SNAPSHOT.jar");
    assertThat(firstJson).contains("watch-include");

    BuildResult secondResult = build(JibPlugin.SKAFFOLD_FILES_TASK_V2_NAME);
    assertThat(secondResult.getOutput()).contains("Reusing configuration cache");
    assertThat(getJson(secondResult)).isEqualTo(firstJson);
  }

  @Test
  public void testSkaffoldInitTask() {
    String firstJson = getJson(build(JibPlugin.SKAFFOLD_INIT_TASK_NAME));
    assertThat(firstJson).contains("configuration-cache-test");

    BuildResult secondResult = build(JibPlugin.SKAFFOLD_INIT_TASK_NAME);
    assertThat(secondResult.getOutput()).contains("Reusing configuration cache");
    assertThat(getJson(secondResult)).isEqualTo(firstJson);
  }

  @Test
  public void testSkaffoldSyncMapTask() {
    String firstJson = getJson(build(JibPlugin.SKAFFOLD_SYNC_MAP_TASK_NAME));
    assertThat(firstJson).contains("HelloWorld.class");

    BuildResult secondResult = build(JibPlugin.SKAFFOLD_SYNC_MAP_TASK_NAME);
    assertThat(secondResult.getOutput()).contains("Reusing configuration cache");
    assertThat(getJson(secondResult)).isEqualTo(firstJson);
  }
}
