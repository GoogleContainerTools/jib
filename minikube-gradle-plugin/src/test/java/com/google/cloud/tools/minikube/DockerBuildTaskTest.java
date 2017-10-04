/*
 * Copyright 2017 Google Inc.
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

package com.google.cloud.tools.minikube;

import java.util.Arrays;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Tests for DockerBuildTask */
public class DockerBuildTaskTest {

  @Rule public TemporaryFolder tmp = new TemporaryFolder();

  @Test
  public void testBuildCommand() {
    Project project = ProjectBuilder.builder().withProjectDir(tmp.getRoot()).build();
    DockerBuildTask testTask =
        project
            .getTasks()
            .create(
                "dockerBuildTestTask",
                DockerBuildTask.class,
                dockerBuildTask -> {
                  dockerBuildTask.setMinikube("/test/path/to/minikube");
                  dockerBuildTask.setDocker("/test/path/to/docker");
                  dockerBuildTask.setFlags(new String[] {"testFlag1", "testFlag2"});
                  dockerBuildTask.setContext("some_build_context");
                });

    Assert.assertEquals(
        Arrays.asList(
            "/test/path/to/docker", "build", "testFlag1", "testFlag2", "some_build_context"),
        testTask.buildDockerBuildCommand());
  }
}
