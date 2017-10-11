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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.cloud.tools.minikube.util.CommandExecutor;
import com.google.cloud.tools.minikube.util.CommandExecutorFactory;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.Assert;
import org.junit.Test;

/** Tests for MinikubeExtension */
public class MinikubeExtensionTest {

  @Test
  public void testGetDockerEnv() throws IOException, InterruptedException {
    Project project = ProjectBuilder.builder().build();

    // Mocks the CommandExecutor.
    CommandExecutor commandExecutorMock = mock(CommandExecutor.class);
    CommandExecutorFactory commandExecutorFactoryMock = mock(CommandExecutorFactory.class);
    when(commandExecutorFactoryMock.newCommandExecutor()).thenReturn(commandExecutorMock);

    // Creates an extension to test on.
    MinikubeExtension minikube = new MinikubeExtension(project, commandExecutorFactoryMock);
    minikube.setMinikube("/test/path/to/minikube");

    // Defined the expected command to run, its output, and the resulting docker-env map.
    List<String> expectedCommand =
        Arrays.asList("/test/path/to/minikube", "docker-env", "--shell=none");
    List<String> dockerEnvOutput = Arrays.asList("ENV_VAR1=VAL1", "ENV_VAR2=VAL2");
    Map<String, String> expectedMap = new HashMap<>(2);
    expectedMap.put("ENV_VAR1", "VAL1");
    expectedMap.put("ENV_VAR2", "VAL2");

    when(commandExecutorMock.run(expectedCommand)).thenReturn(dockerEnvOutput);

    Assert.assertEquals(expectedMap, minikube.getDockerEnv());
    verify(commandExecutorMock).run(expectedCommand);
  }
}
