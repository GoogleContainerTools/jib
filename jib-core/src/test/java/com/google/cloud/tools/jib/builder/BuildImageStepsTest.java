/*
 * Copyright 2018 Google Inc.
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

package com.google.cloud.tools.jib.builder;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import com.google.cloud.tools.jib.builder.configuration.BuildConfiguration;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

/** Tests for {@link BuildImageSteps}. More comprehensive tests are in the integration tests. */
public class BuildImageStepsTest {

  @Test
  public void testGetEntrypoint() {
    String expectedDependenciesPath = "/app/libs/";
    String expectedResourcesPath = "/app/resources/";
    String expectedClassesPath = "/app/classes/";
    List<String> expectedJvmFlags = Arrays.asList("-flag", "anotherFlag");
    String expectedMainClass = "SomeMainClass";

    SourceFilesConfiguration mockSourceFilesConfiguration =
        Mockito.mock(SourceFilesConfiguration.class);
    BuildConfiguration mockBuildConfiguration = Mockito.mock(BuildConfiguration.class);

    Mockito.when(mockSourceFilesConfiguration.getDependenciesPathOnImage())
        .thenReturn(expectedDependenciesPath);
    Mockito.when(mockSourceFilesConfiguration.getResourcesPathOnImage())
        .thenReturn(expectedResourcesPath);
    Mockito.when(mockSourceFilesConfiguration.getClassesPathOnImage())
        .thenReturn(expectedClassesPath);

    Mockito.when(mockBuildConfiguration.getJvmFlags()).thenReturn(expectedJvmFlags);
    Mockito.when(mockBuildConfiguration.getMainClass()).thenReturn(expectedMainClass);

    Assert.assertEquals(
        Arrays.asList(
            "java",
            "-flag",
            "anotherFlag",
            "-cp",
            "/app/libs/*:/app/resources/:/app/classes/",
            "SomeMainClass"),
        new BuildImageSteps(
                mockBuildConfiguration, mockSourceFilesConfiguration, Mockito.mock(Path.class))
            .getEntrypoint());
  }
}
