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

import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import com.google.cloud.tools.jib.gradle.JibPlugin;
import com.google.cloud.tools.jib.gradle.TestProject;
import com.google.cloud.tools.jib.plugins.common.SkaffoldSyncMapTemplate;
import com.google.cloud.tools.jib.plugins.common.SkaffoldSyncMapTemplate.FileTemplate;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.BuildTask;
import org.gradle.testkit.runner.TaskOutcome;
import org.gradle.testkit.runner.UnexpectedBuildFailure;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;

/** Tests for {@link SyncMapTask}. */
public class SyncMapTaskTest {

  @ClassRule public static final TestProject simpleTestProject = new TestProject("simple");
  @ClassRule public static final TestProject skaffoldProject = new TestProject("skaffold-config");
  @ClassRule public static final TestProject multiTestProject = new TestProject("multi-service");
  @ClassRule public static final TestProject warProject = new TestProject("war_servlet25");

  /**
   * Verifies that the sync map task succeeded and returns the parsed json.
   *
   * @param project the project to run the task on
   * @param moduleName the name of the sub-project, or {@code null} if no sub-project
   * @param params extra gradle cli params to use during the build
   * @return the list of paths printed by the task
   * @throws IOException if the json parser fails
   */
  private static SkaffoldSyncMapTemplate generateTemplate(
      TestProject project, @Nullable String moduleName, @Nullable List<String> params)
      throws IOException {
    String taskName =
        ":" + (moduleName == null ? "" : moduleName + ":") + JibPlugin.SKAFFOLD_SYNC_MAP_TASK_NAME;
    List<String> buildParams = new ArrayList<>();
    buildParams.add(taskName);
    buildParams.add("-q");
    buildParams.add("-D_TARGET_IMAGE=ignored");
    buildParams.add("--stacktrace");
    if (params != null) {
      buildParams.addAll(params);
    }
    BuildResult buildResult = project.build(buildParams);
    BuildTask jibTask = buildResult.task(taskName);
    Assert.assertNotNull(jibTask);
    Assert.assertEquals(TaskOutcome.SUCCESS, jibTask.getOutcome());

    List<String> outputLines =
        Splitter.on(System.lineSeparator()).omitEmptyStrings().splitToList(buildResult.getOutput());
    Assert.assertEquals(2, outputLines.size());
    Assert.assertEquals("BEGIN JIB JSON: SYNCMAP/1", outputLines.get(0));
    return SkaffoldSyncMapTemplate.from(outputLines.get(1));
  }

  private static void assertFilePaths(Path src, AbsoluteUnixPath dest, FileTemplate template)
      throws IOException {
    Assert.assertEquals(src.toRealPath().toString(), template.getSrc());
    Assert.assertEquals(dest.toString(), template.getDest());
  }

  @Test
  public void testSyncMapTask_singleProject() throws IOException {
    Path projectRoot = simpleTestProject.getProjectRoot();
    SkaffoldSyncMapTemplate parsed = generateTemplate(simpleTestProject, null, null);

    List<FileTemplate> generated = parsed.getGenerated();
    Assert.assertEquals(2, generated.size());
    assertFilePaths(
        projectRoot.resolve("build/resources/main/world"),
        AbsoluteUnixPath.get("/app/resources/world"),
        generated.get(0));
    assertFilePaths(
        projectRoot.resolve("build/classes/java/main/com/test/HelloWorld.class"),
        AbsoluteUnixPath.get("/app/classes/com/test/HelloWorld.class"),
        generated.get(1));

    List<FileTemplate> direct = parsed.getDirect();
    Assert.assertEquals(2, direct.size());
    assertFilePaths(
        projectRoot.resolve("src/main/custom-extra-dir/bar/cat"),
        AbsoluteUnixPath.get("/bar/cat"),
        direct.get(0));
    assertFilePaths(
        projectRoot.resolve("src/main/custom-extra-dir/foo"),
        AbsoluteUnixPath.get("/foo"),
        direct.get(1));
  }

  @Test
  public void testSyncMapTask_multiProjectOutput() throws IOException {
    Path projectRoot = multiTestProject.getProjectRoot();
    Path complexServiceRoot = projectRoot.resolve("complex-service");
    Path libRoot = projectRoot.resolve("lib");
    SkaffoldSyncMapTemplate parsed = generateTemplate(multiTestProject, "complex-service", null);

    List<FileTemplate> generated = parsed.getGenerated();
    Assert.assertEquals(4, generated.size());
    assertFilePaths(
        libRoot.resolve("build/libs/lib.jar"),
        AbsoluteUnixPath.get("/app/libs/lib.jar"),
        generated.get(0));
    assertFilePaths(
        complexServiceRoot.resolve("build/resources/main/resource1.txt"),
        AbsoluteUnixPath.get("/app/resources/resource1.txt"),
        generated.get(1));
    assertFilePaths(
        complexServiceRoot.resolve("build/resources/main/resource2.txt"),
        AbsoluteUnixPath.get("/app/resources/resource2.txt"),
        generated.get(2));
    assertFilePaths(
        complexServiceRoot.resolve("build/classes/java/main/com/test/HelloWorld.class"),
        AbsoluteUnixPath.get("/app/classes/com/test/HelloWorld.class"),
        generated.get(3));

    List<FileTemplate> direct = parsed.getDirect();
    Assert.assertEquals(2, direct.size());
    assertFilePaths(
        complexServiceRoot.resolve(
            "local-m2-repo/com/google/cloud/tools/tiny-test-lib/0.0.1-SNAPSHOT/tiny-test-lib-0.0.1-SNAPSHOT.jar"),
        AbsoluteUnixPath.get("/app/libs/tiny-test-lib-0.0.1-SNAPSHOT.jar"),
        direct.get(0));
    assertFilePaths(
        complexServiceRoot.resolve("src/main/other-jib/extra-file"),
        AbsoluteUnixPath.get("/extra-file"),
        direct.get(1));
  }

  @Test
  public void testSyncMapTask_withSkaffoldConfig() throws IOException {
    Path projectRoot = skaffoldProject.getProjectRoot();
    SkaffoldSyncMapTemplate parsed = generateTemplate(skaffoldProject, null, null);

    List<FileTemplate> generated = parsed.getGenerated();
    Assert.assertEquals(2, generated.size());
    assertFilePaths(
        projectRoot.resolve("build/resources/main/world"),
        AbsoluteUnixPath.get("/app/resources/world"),
        generated.get(0));
    assertFilePaths(
        projectRoot.resolve("build/classes/java/main/com/test2/GoodbyeWorld.class"),
        AbsoluteUnixPath.get("/app/classes/com/test2/GoodbyeWorld.class"),
        generated.get(1));
    // classes/java/main/com/test is ignored

    List<FileTemplate> direct = parsed.getDirect();
    Assert.assertEquals(1, direct.size());
    assertFilePaths(
        projectRoot.resolve("src/main/jib/bar/cat"),
        AbsoluteUnixPath.get("/bar/cat"),
        direct.get(0));
    // src/main/custom-extra-dir/foo is ignored
  }

  @Test
  public void testSyncMapTask_failIfWar() throws IOException {
    Path projectRoot = warProject.getProjectRoot();
    try {
      generateTemplate(warProject, null, null);
      Assert.fail();
    } catch (UnexpectedBuildFailure ex) {
      Assert.assertTrue(
          ex.getMessage()
              .contains(
                  "org.gradle.api.GradleException: Skaffold sync is currently only available for 'jar' style Jib projects, but the project "
                      + projectRoot.getFileName()
                      + " is configured to generate a 'war'"));
    }
  }

  @Test
  public void testSyncMapTask_failIfJarContainerizationMode() throws IOException {
    Path projectRoot = simpleTestProject.getProjectRoot();
    try {
      generateTemplate(
          simpleTestProject, null, ImmutableList.of("-Djib.containerizingMode=packaged"));
      Assert.fail();
    } catch (UnexpectedBuildFailure ex) {
      Assert.assertTrue(
          ex.getMessage()
              .contains(
                  "Skaffold sync is currently only available for Jib projects in 'exploded' containerizing mode, but the containerizing mode of "
                      + projectRoot.getFileName()
                      + " is 'packaged'"));
    }
  }
}
