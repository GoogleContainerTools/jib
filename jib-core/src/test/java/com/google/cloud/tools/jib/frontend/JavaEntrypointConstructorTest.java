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

package com.google.cloud.tools.jib.frontend;

import java.util.Arrays;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

/** Tests for {@link JavaEntrypointConstructor}. */
public class JavaEntrypointConstructorTest {

  @Test
  public void testMakeEntrypoint() {
    String expectedDependenciesPath = "/app/libs/*";
    String expectedResourcesPath = "/app/resources/";
    String expectedClassesPath = "/app/classes/";
    List<String> expectedJvmFlags = Arrays.asList("-flag", "anotherFlag");
    String expectedMainClass = "SomeMainClass";

    List<String> entrypoint =
        JavaEntrypointConstructor.makeEntrypoint(
            Arrays.asList(expectedDependenciesPath, expectedResourcesPath, expectedClassesPath),
            expectedJvmFlags,
            expectedMainClass);
    Assert.assertEquals(
        Arrays.asList(
            "java",
            "-flag",
            "anotherFlag",
            "-cp",
            "/app/libs/*:/app/resources/:/app/classes/",
            "SomeMainClass"),
        entrypoint);

    // Checks that this is also the default entrypoint.
    Assert.assertEquals(
        JavaEntrypointConstructor.makeDefaultEntrypoint(expectedJvmFlags, expectedMainClass),
        entrypoint);
  }
}
