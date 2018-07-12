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

package com.google.cloud.tools.jib.builder;

import java.util.Arrays;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

/** Tests for {@link EntrypointBuilder}. */
public class EntrypointBuilderTest {

  @Test
  public void testMakeEntrypoint() {
    String expectedDependenciesPath = "/app/libs/";
    String expectedSnapshotDependenciesPath = "/app/snapshot-libs/";
    String expectedResourcesPath = "/app/resources/";
    String expectedClassesPath = "/app/classes/";
    List<String> expectedJvmFlags = Arrays.asList("-flag", "anotherFlag");
    String expectedMainClass = "SomeMainClass";

    SourceFilesConfiguration mockSourceFilesConfiguration =
        Mockito.mock(SourceFilesConfiguration.class);

    Mockito.when(mockSourceFilesConfiguration.getDependenciesPathOnImage())
        .thenReturn(expectedDependenciesPath);
    Mockito.when(mockSourceFilesConfiguration.getSnapshotDependenciesPathOnImage())
        .thenReturn(expectedSnapshotDependenciesPath);
    Mockito.when(mockSourceFilesConfiguration.getResourcesPathOnImage())
        .thenReturn(expectedResourcesPath);
    Mockito.when(mockSourceFilesConfiguration.getClassesPathOnImage())
        .thenReturn(expectedClassesPath);

    Assert.assertEquals(
        Arrays.asList(
            "java",
            "-flag",
            "anotherFlag",
            "-cp",
            "/app/libs/*:/app/snapshot-libs/*:/app/resources/:/app/classes/",
            "SomeMainClass"),
        EntrypointBuilder.makeEntrypoint(
            mockSourceFilesConfiguration, expectedJvmFlags, expectedMainClass));
  }
}
