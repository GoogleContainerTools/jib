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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.BuildTask;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;

public class FilesTaskTest {

  @ClassRule public static final TestProject simpleTestProject = new TestProject("simple");

  @ClassRule public static final TestProject multiTestProject = new TestProject("multi-service");

  @Test
  public void testFilesTask_singleProject() {
    Path projectRoot = simpleTestProject.getProjectRoot();
    verifyFiles(
        simpleTestProject,
        null,
        ImmutableList.of(
            projectRoot.resolve("build.gradle"),
            projectRoot.resolve("src/main/java"),
            projectRoot.resolve("src/main/resources"),
            projectRoot.resolve("src/main/custom-extra-dir")));
  }

  @Test
  public void testFilesTask_multiProjectSimpleService() {
    Path projectRoot = multiTestProject.getProjectRoot();
    Path simpleServiceRoot = projectRoot.resolve("simple-service");
    verifyFiles(
        multiTestProject,
        "simple-service",
        ImmutableList.of(
            projectRoot.resolve("build.gradle"),
            simpleServiceRoot.resolve("build.gradle"),
            simpleServiceRoot.resolve("src/main/java"),
            simpleServiceRoot.resolve("src/main/resources"),
            simpleServiceRoot.resolve("src/main/jib")));
  }

  @Test
  public void testFilesTask_multiProjectComplexService() {
    Path projectRoot = multiTestProject.getProjectRoot();
    Path complexServiceRoot = projectRoot.resolve("complex-service");
    Path libRoot = projectRoot.resolve("lib");

    verifyFiles(
        multiTestProject,
        "complex-service",
        ImmutableList.of(
            projectRoot.resolve("build.gradle"),
            complexServiceRoot.resolve("build.gradle"),
            complexServiceRoot.resolve("src/main/java"),
            complexServiceRoot.resolve("src/main/resources"),
            complexServiceRoot.resolve("src/main/extra-resources-1"),
            complexServiceRoot.resolve("src/main/extra-resources-2"),
            complexServiceRoot.resolve("src/main/other-jib"),
            libRoot.resolve("build.gradle"),
            libRoot.resolve("src/main/java"),
            libRoot.resolve("src/main/resources"),
            Paths.get(System.getProperty("user.home"))
                .resolve(
                    ".gradle/caches/modules-2/files-2.1/com.google.guava/guava/HEAD-jre-SNAPSHOT/fc6cc9b34c2173771a38aec49f27258d892ceee9/guava-HEAD-jre-SNAPSHOT.jar")));
  }

  private static void verifyFiles(
      TestProject project, @Nullable String moduleName, List<Path> files) {
    String taskName =
        ":" + (moduleName == null ? "" : moduleName + ":") + JibPlugin.FILES_TASK_NAME;
    BuildResult buildResult = project.build(taskName, "-q");
    BuildTask jibTask = buildResult.task(taskName);
    Assert.assertNotNull(jibTask);
    Assert.assertEquals(TaskOutcome.SUCCESS, jibTask.getOutcome());

    List<String> expectedResult =
        files.stream().map(Path::toAbsolutePath).map(Path::toString).collect(Collectors.toList());
    Assert.assertEquals(
        expectedResult,
        Splitter.on(System.lineSeparator())
            .omitEmptyStrings()
            .splitToList(buildResult.getOutput()));
  }
}
