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

import com.google.cloud.tools.jib.gradle.JibPlugin;
import com.google.cloud.tools.jib.gradle.TestProject;
import com.google.cloud.tools.jib.plugins.common.SkaffoldInitOutput;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.BuildTask;
import org.gradle.testkit.runner.TaskOutcome;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Assert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

/** Tests for {@link InitTask}. */
class InitTaskTest {

  @TempDir Path tempDir;

  @TempDir Path tempDirMulti;

  @RegisterExtension public TestProject simpleTestProject = new TestProject("simple", tempDir);

  @RegisterExtension
  public final TestProject multiTestProject = new TestProject("multi-service", tempDirMulti);

  /**
   * Verifies that the files task succeeded and returns the list of JSON strings printed by the
   * task.
   *
   * @param project the project to run the task on
   * @return the JSON strings printed by the task
   */
  private static List<String> getJsons(TestProject project) {
    BuildResult buildResult =
        project.build(JibPlugin.SKAFFOLD_INIT_TASK_NAME, "-q", "-D_TARGET_IMAGE=testimage");
    BuildTask jibTask = buildResult.task(":" + JibPlugin.SKAFFOLD_INIT_TASK_NAME);
    Assert.assertNotNull(jibTask);
    Assert.assertEquals(TaskOutcome.SUCCESS, jibTask.getOutcome());
    String output = buildResult.getOutput().trim();
    MatcherAssert.assertThat(output, CoreMatchers.startsWith("BEGIN JIB JSON"));

    Pattern pattern = Pattern.compile("BEGIN JIB JSON\r?\n(\\{.*})");
    Matcher matcher = pattern.matcher(output);
    List<String> jsons = new ArrayList<>();
    while (matcher.find()) {
      jsons.add(matcher.group(1));
    }

    // Return task output with header removed
    return jsons;
  }

  @Test
  void testFilesTask_singleProject() throws IOException {
    List<String> outputs = getJsons(simpleTestProject);
    Assert.assertEquals(1, outputs.size());

    SkaffoldInitOutput skaffoldInitOutput = new SkaffoldInitOutput(outputs.get(0));
    Assert.assertEquals("testimage", skaffoldInitOutput.getImage());
    Assert.assertNull(skaffoldInitOutput.getProject());
  }

  @Test
  void testFilesTask_multiProject() throws IOException {
    List<String> outputs = getJsons(multiTestProject);
    Assert.assertEquals(2, outputs.size());

    SkaffoldInitOutput skaffoldInitOutput = new SkaffoldInitOutput(outputs.get(0));
    Assert.assertEquals("testimage", skaffoldInitOutput.getImage());
    Assert.assertEquals("complex-service", skaffoldInitOutput.getProject());

    skaffoldInitOutput = new SkaffoldInitOutput(outputs.get(1));
    Assert.assertEquals("testimage", skaffoldInitOutput.getImage());
    Assert.assertEquals("simple-service", skaffoldInitOutput.getProject());
  }
}
