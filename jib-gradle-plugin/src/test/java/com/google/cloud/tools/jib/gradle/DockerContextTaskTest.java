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

import java.util.Arrays;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link DockerContextTask}. */
public class DockerContextTaskTest {

  private Project fakeProject;
  private DockerContextTask testDockerContextTask;
  private JibExtension fakeJibExtension;

  @Before
  public void setUp() {
    fakeProject = ProjectBuilder.builder().build();
    testDockerContextTask = fakeProject.getTasks().create("task", DockerContextTask.class);
    fakeJibExtension = fakeProject.getExtensions().create("jib", JibExtension.class, fakeProject);
  }

  @Test
  public void testApplyExtension() {
    fakeJibExtension.from(
        from -> {
          from.setImage("some image");
        });
    fakeJibExtension.setJvmFlags(Arrays.asList("flag1", "flag2"));
    fakeJibExtension.setMainClass("some main class");

    testDockerContextTask.applyExtension(fakeJibExtension);

    Assert.assertEquals("some image", testDockerContextTask.getBaseImage());
    Assert.assertEquals(Arrays.asList("flag1", "flag2"), testDockerContextTask.getJvmFlags());
    Assert.assertEquals("some main class", testDockerContextTask.getMainClass());
    Assert.assertEquals(
        fakeProject.getBuildDir().toPath().resolve("jib-dockercontext").toString(),
        testDockerContextTask.getTargetDir());
  }
}
