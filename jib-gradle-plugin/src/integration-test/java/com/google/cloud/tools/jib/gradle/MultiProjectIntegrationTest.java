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

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.BuildTask;
import org.gradle.testkit.runner.TaskOutcome;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;

/** Integration tests for building multi-project images. */
public class MultiProjectIntegrationTest {

  @ClassRule
  public static final TestProject multiprojectTestProject = new TestProject("multiproject");

  @Test
  public void testMultiProject() {
    BuildResult buildResult =
        multiprojectTestProject.build("clean", ":a_packaged:jibExportDockerContext", "--info");

    BuildTask classesTask = buildResult.task(":a_packaged:classes");
    BuildTask jibTask = buildResult.task(":a_packaged:jibExportDockerContext");
    Assert.assertNotNull(classesTask);
    Assert.assertEquals(TaskOutcome.SUCCESS, classesTask.getOutcome());
    Assert.assertNotNull(jibTask);
    Assert.assertEquals(TaskOutcome.SUCCESS, jibTask.getOutcome());
    Assert.assertThat(
        buildResult.getOutput(), CoreMatchers.containsString("Created Docker context at "));
  }
}
