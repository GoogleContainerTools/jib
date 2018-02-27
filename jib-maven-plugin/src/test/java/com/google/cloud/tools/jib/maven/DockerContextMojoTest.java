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

package com.google.cloud.tools.jib.maven;

import com.google.cloud.tools.jib.builder.SourceFilesConfiguration;
import java.util.Arrays;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

/** Tests for {@link DockerContextMojo}. */
public class DockerContextMojoTest {

  @Test
  public void testGetEntrypoint() {
    String expectedDependenciesPath = "/app/libs/";
    String expectedResourcesPath = "/app/resources/";
    String expectedClassesPath = "/app/classes/";
    List<String> expectedJvmFlags = Arrays.asList("-flag", "another\"Flag");
    String expectedMainClass = "SomeMainClass";

    SourceFilesConfiguration mockSourceFilesConfiguration =
        Mockito.mock(SourceFilesConfiguration.class);

    Mockito.when(mockSourceFilesConfiguration.getDependenciesPathOnImage())
        .thenReturn(expectedDependenciesPath);
    Mockito.when(mockSourceFilesConfiguration.getResourcesPathOnImage())
        .thenReturn(expectedResourcesPath);
    Mockito.when(mockSourceFilesConfiguration.getClassesPathOnImage())
        .thenReturn(expectedClassesPath);

    DockerContextMojo dockerContextMojo =
        new DockerContextMojo().setJvmFlags(expectedJvmFlags).setMainClass(expectedMainClass);

    Assert.assertEquals(
        "[\"java\",\"-flag\",\"another\\\"Flag\",\"-cp\",\"/app/libs/*:/app/resources/:/app/classes/\",\"SomeMainClass\"]",
        dockerContextMojo.getEntrypoint(mockSourceFilesConfiguration));
  }
}
